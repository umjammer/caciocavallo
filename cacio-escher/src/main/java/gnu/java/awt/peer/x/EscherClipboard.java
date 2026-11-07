package gnu.java.awt.peer.x;

import java.awt.EventQueue;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import gnu.x11.Atom;
import gnu.x11.Display;
import gnu.x11.Window;
import gnu.x11.WindowAttributes;
import gnu.x11.event.SelectionClear;
import gnu.x11.event.SelectionNotify;
import gnu.x11.event.SelectionRequest;


/**
 * An X selection (CLIPBOARD or PRIMARY) as a java clipboard, so that it is shared
 * with the other X clients. Text, images (as PNG) and file lists (as a URI list)
 * go through it. There is no INCR, data has to fit into a single request.
 */
final class EscherClipboard extends Clipboard {

    private static final Logger logger = System.getLogger(EscherClipboard.class.getName());

    /** How long the owner of the selection gets to answer. */
    private static final long CONVERT_TIMEOUT_MILLIS = 2000;

    /** The request size of the core protocol, minus some room for the headers. */
    private static final int MAX_DATA_BYTES = 0xffff * 4 - 64;

    private static final String URI_LIST = "text/uri-list";
    private static final String PNG = "image/png";

    private final Display display;

    /** Owns the selection and receives the conversions, it is never mapped. */
    private final Window window;

    private final Atom selection, property;
    private final Atom targets, timestamp, utf8, text, textPlainUtf8, uriList, png;

    /** Whether we hold the selection. */
    private volatile boolean owned;

    /** The conversion on its way, the one thing we can be waiting for. */
    private volatile CompletableFuture<Boolean> pending;

    EscherClipboard(Display display, String selectionName) {
        super(selectionName);
        this.display = display;
        WindowAttributes attributes = new WindowAttributes();
        attributes.setOverrideRedirect(true);
        window = new Window(display.getDefaultRoot(), 0, 0, 1, 1, 0, attributes);
        selection = Atom.intern(display, selectionName);
        property = Atom.intern(display, "ESCHER_SELECTION_" + selectionName);
        targets = Atom.intern(display, "TARGETS");
        timestamp = Atom.intern(display, "TIMESTAMP");
        utf8 = Atom.intern(display, "UTF8_STRING");
        text = Atom.intern(display, "TEXT");
        textPlainUtf8 = Atom.intern(display, "text/plain;charset=utf-8");
        uriList = Atom.intern(display, URI_LIST);
        png = Atom.intern(display, PNG);
    }

    int selectionID() {
        return selection.getID();
    }

    @Override
    public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
        super.setContents(contents, owner);
        owned = true;
        window.setSelectionOwner(selection, Display.CURRENT_TIME);
        display.flush();
    }

    /**
     * Not synchronized: the event pump needs the lock to answer requests, and
     * there is no telling what the data being fetched here is waiting for.
     */
    @Override
    public Transferable getContents(Object requestor) {
        if (owned) {
            return contents;
        }
        byte[] data = convert(targets);
        List<Atom> offered = new ArrayList<>();
        if (data != null) {
            // format 32, in the order of the connection, which is big endian
            for (int i = 0; i + 4 <= data.length; i += 4) {
                int id = (data[i] & 0xff) << 24 | (data[i + 1] & 0xff) << 16 | (data[i + 2] & 0xff) << 8 | data[i + 3] & 0xff;
                for (Atom known : new Atom[] {utf8, textPlainUtf8, Atom.STRING, text, uriList, png}) {
                    if (known.getID() == id) {
                        offered.add(known);
                    }
                }
            }
        } else {
            // the owner may simply not know TARGETS
            offered.add(utf8);
            offered.add(Atom.STRING);
        }
        return new Selection(offered);
    }

    /** Asks the owner of the selection for the data. */
    private byte[] convert(Atom target) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        synchronized (window) {
            pending = done;
            window.convertSelection(selection, target, property, Display.CURRENT_TIME);
            display.flush();
            try {
                if (!done.get(CONVERT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException | TimeoutException e) {
                logger.log(Level.DEBUG, "no answer for " + getName() + ": " + e);
                return null;
            } finally {
                pending = null;
            }
            Window.Property p = window.getProperty(true, property, Atom.ANY_PROPERTY_TYPE, 0, Integer.MAX_VALUE / 4);
            return p.format() != 0 ? p.data() : null;
        }
    }

    /** From the event pump, the answer to {@link #convert}. */
    void selectionNotify(SelectionNotify e) {
        CompletableFuture<Boolean> done = pending;
        if (done != null) {
            done.complete(e.getPropertyAtomID() != 0);
        }
    }

    /** From the event pump, another client took the selection. */
    synchronized void selectionClear(SelectionClear e) {
        owned = false;
        ClipboardOwner oldOwner = owner;
        Transferable oldContents = contents;
        owner = null;
        contents = null;
        if (oldOwner != null) {
            EventQueue.invokeLater(() -> oldOwner.lostOwnership(this, oldContents));
        }
    }

    /** From the event pump, another client wants the data. */
    void selectionRequest(SelectionRequest e) {
        int propertyID = e.getPropertyAtomID() != 0 ? e.getPropertyAtomID() : e.getTargetAtomID();
        Window requestor = (Window) Window.intern(display, e.getRequestorWindowID());
        Atom target = known(e.getTargetAtomID());
        Atom destination = (Atom) Atom.intern(display, propertyID);
        boolean answered = false;
        Transferable data;
        synchronized (this) {
            data = owned ? contents : null;
        }
        try {
            if (data != null && target != null) {
                answered = answer(requestor, destination, target, data);
            }
        } catch (IOException | UnsupportedFlavorException | RuntimeException x) {
            logger.log(Level.DEBUG, "cannot convert " + getName() + ": " + x);
        }
        requestor.sendEvent(false, 0, new SelectionNotify(display, e.getTime(), e.getRequestorWindowID(),
                e.getSelectionAtomID(), e.getTargetAtomID(), answered ? propertyID : 0));
        display.flush();
    }

    private Atom known(int id) {
        for (Atom atom : new Atom[] {targets, timestamp, utf8, textPlainUtf8, Atom.STRING, text, uriList, png}) {
            if (atom.getID() == id) {
                return atom;
            }
        }
        return null;
    }

    private boolean answer(Window requestor, Atom destination, Atom target, Transferable data)
            throws IOException, UnsupportedFlavorException {
        if (target == targets) {
            List<Integer> offered = new ArrayList<>(List.of(targets.getID(), timestamp.getID()));
            if (data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                for (Atom atom : new Atom[] {utf8, textPlainUtf8, Atom.STRING, text}) {
                    offered.add(atom.getID());
                }
            }
            if (data.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                offered.add(png.getID());
            }
            if (data.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                offered.add(uriList.getID());
            }
            requestor.changeProperty(Window.PropertyMode.REPLACE, destination, Atom.ATOM, 32,
                    offered.stream().mapToInt(Integer::intValue).toArray(), 0, 32);
            return true;
        }
        if (target == timestamp) {
            requestor.changeProperty(Window.PropertyMode.REPLACE, destination, Atom.INTEGER, 32, new int[] {0}, 0, 32);
            return true;
        }

        byte[] bytes;
        Atom type = target;
        if (target == png) {
            bytes = pngOf((Image) data.getTransferData(DataFlavor.imageFlavor));
        } else if (target == uriList) {
            bytes = uriListOf((List<?>) data.getTransferData(DataFlavor.javaFileListFlavor));
        } else {
            Charset charset = target == Atom.STRING ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
            if (target == text) {
                type = utf8;
            }
            bytes = ((String) data.getTransferData(DataFlavor.stringFlavor)).getBytes(charset);
        }
        if (bytes.length > MAX_DATA_BYTES) {
            logger.log(Level.WARNING, getName() + ": " + bytes.length + " bytes need INCR, which is not implemented");
            return false;
        }
        requestor.changeProperty(Window.PropertyMode.REPLACE, destination, type, 8, bytes, 0, 8);
        return true;
    }

    private static byte[] pngOf(Image image) throws IOException {
        RenderedImage rendered;
        if (image instanceof RenderedImage r) {
            rendered = r;
        } else {
            BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            copy.getGraphics().drawImage(image, 0, 0, null);
            rendered = copy;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(rendered, "png", out);
        return out.toByteArray();
    }

    private static byte[] uriListOf(List<?> files) {
        StringBuilder list = new StringBuilder();
        for (Object file : files) {
            list.append(((File) file).toURI()).append("\r\n");
        }
        return list.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** What another client has on offer, fetched only when asked for. */
    private final class Selection implements Transferable {

        private final List<Atom> offered;

        Selection(List<Atom> offered) {
            this.offered = offered;
        }

        private Atom textTarget() {
            for (Atom atom : new Atom[] {utf8, textPlainUtf8, text, Atom.STRING}) {
                if (offered.contains(atom)) {
                    return atom;
                }
            }
            return null;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            List<DataFlavor> flavors = new ArrayList<>();
            if (textTarget() != null) {
                flavors.add(DataFlavor.stringFlavor);
            }
            if (offered.contains(png)) {
                flavors.add(DataFlavor.imageFlavor);
            }
            if (offered.contains(uriList)) {
                flavors.add(DataFlavor.javaFileListFlavor);
            }
            return flavors.toArray(DataFlavor[]::new);
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return List.of(getTransferDataFlavors()).contains(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            Atom target = flavor.equals(DataFlavor.imageFlavor) ? png
                    : flavor.equals(DataFlavor.javaFileListFlavor) ? uriList
                    : textTarget();
            byte[] data = convert(target);
            if (data == null) {
                throw new IOException("the owner of " + getName() + " gave up on " + flavor);
            }
            if (target == png) {
                return ImageIO.read(new ByteArrayInputStream(data));
            }
            if (target == uriList) {
                List<File> files = new ArrayList<>();
                for (String line : new String(data, StandardCharsets.UTF_8).split("\r?\n")) {
                    if (line.startsWith("file:")) {
                        files.add(new File(URI.create(line.trim())));
                    }
                }
                return files;
            }
            return new String(data, target == Atom.STRING ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
        }
    }
}
