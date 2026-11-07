package gnu.java.awt.peer.x;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;

import sun.awt.datatransfer.DataTransferer;
import sun.awt.datatransfer.ToolkitThreadBlockedHandler;


/**
 * What the JDK's drag and drop needs to name data formats. Formats are simply
 * numbered here, nothing but this JVM ever gets to see them.
 */
final class EscherDataTransferer extends DataTransferer {

    private static final String URI_LIST = "text/uri-list";
    private static final String PNG = "image/png";

    private static final EscherDataTransferer instance = new EscherDataTransferer();

    private final Map<String, Long> formats = new HashMap<>();
    private final Map<Long, String> names = new HashMap<>();
    private final BlockedHandler blockedHandler = new BlockedHandler();

    static EscherDataTransferer instance() {
        return instance;
    }

    private EscherDataTransferer() {
    }

    @Override
    public String getDefaultUnicodeEncoding() {
        return "UTF-8";
    }

    @Override
    public boolean isLocaleDependentTextFormat(long format) {
        return false;
    }

    @Override
    public boolean isFileFormat(long format) {
        return URI_LIST.equals(getNativeForFormat(format));
    }

    @Override
    public boolean isImageFormat(long format) {
        return PNG.equals(getNativeForFormat(format));
    }

    @Override
    protected synchronized Long getFormatForNativeAsLong(String str) {
        return formats.computeIfAbsent(str, name -> {
            long format = formats.size() + 1L;
            names.put(format, name);
            return format;
        });
    }

    @Override
    protected synchronized String getNativeForFormat(long format) {
        return names.get(format);
    }

    @Override
    protected ByteArrayOutputStream convertFileListToBytes(ArrayList<String> fileList) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String file : fileList) {
            out.write((new File(file).toURI() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    @Override
    protected String[] dragQueryFile(byte[] bytes) {
        List<String> files = new ArrayList<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\r?\n")) {
            if (line.startsWith("file:")) {
                files.add(new File(URI.create(line.trim())).getPath());
            }
        }
        return files.toArray(String[]::new);
    }

    @Override
    protected Image platformImageBytesToImage(byte[] bytes, long format) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Override
    protected byte[] imageToPlatformBytes(Image image, long format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RenderedImage rendered;
        if (image instanceof RenderedImage r) {
            rendered = r;
        } else {
            BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            copy.getGraphics().drawImage(image, 0, 0, null);
            rendered = copy;
        }
        ImageIO.write(rendered, "png", out);
        return out.toByteArray();
    }

    @Override
    public ToolkitThreadBlockedHandler getToolkitThreadBlockedHandler() {
        return blockedHandler;
    }

    /** The event pump waiting for the EDT during a synchronous drop target event. */
    private static final class BlockedHandler implements ToolkitThreadBlockedHandler {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();

        @Override
        public void lock() {
            lock.lock();
        }

        @Override
        public void unlock() {
            lock.unlock();
        }

        @Override
        public void enter() {
            try {
                condition.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void exit() {
            condition.signalAll();
        }
    }
}
