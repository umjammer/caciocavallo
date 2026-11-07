package gnu.java.awt.peer.x;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import gnu.java.awt.peer.x.junit.CacioEscherRunner;
import gnu.x11.Atom;
import gnu.x11.Display;
import gnu.x11.UnixDomainSocket;
import gnu.x11.Window;
import gnu.x11.WindowAttributes;
import gnu.x11.event.Event;
import gnu.x11.event.SelectionNotify;
import gnu.x11.event.SelectionRequest;
import gnu.x11.extension.XTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Robot, the selections, cursors and drag and drop, against a real X server. All
 * the input goes through XTEST and comes back as X events, so these need an X
 * server at {@code $DISPLAY} that nothing else is using, Xvfb for example, and
 * {@code -Descher.x11=true} to opt in.
 */
@EnabledIfSystemProperty(named = "escher.x11", matches = "true")
class EscherFeaturesTest {

    private static Robot robot;

    private JFrame frame;
    private JFrame other;

    @BeforeAll
    static void install() throws Exception {
        CacioEscherRunner.install();
        robot = new Robot();
        robot.setAutoDelay(15);
    }

    private JFrame show(JComponent content) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("EscherFeaturesTest");
            frame.getContentPane().add(content, BorderLayout.CENTER);
            frame.setBounds(100, 100, 400, 300);
            frame.setVisible(true);
        });
        settle();
        return frame;
    }

    private static void settle() {
        robot.waitForIdle();
        robot.delay(250);
        robot.waitForIdle();
    }

    @AfterEach
    void dispose() throws Exception {
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        if (other != null) {
            SwingUtilities.invokeAndWait(other::dispose);
            other = null;
        }
        if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
            settle();
        }
    }

    private static Point center(Component c) throws Exception {
        AtomicReference<Point> p = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Point l = c.getLocationOnScreen();
            p.set(new Point(l.x + c.getWidth() / 2, l.y + c.getHeight() / 2));
        });
        return p.get();
    }

    private static void click(Component c) throws Exception {
        Point p = center(c);
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
    }

    private static void type(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
            robot.keyRelease(key);
        }
        settle();
    }

    private static void ctrl(int key) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        type(key);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        settle();
    }

    private static void drag(Component from, Component to) throws Exception {
        Point a = center(from);
        Point b = center(to);
        robot.mouseMove(a.x, a.y);
        settle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 20; i++) {
            robot.mouseMove(a.x + (b.x - a.x) * i / 20, a.y + (b.y - a.y) * i / 20);
            robot.delay(10);
        }
        settle();
    }

    // ---- robot

    @Test
    void robotClicksAButton() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton button = new JButton("click me");
        button.addActionListener(e -> clicks.incrementAndGet());
        show(button);
        click(button);
        assertEquals(1, clicks.get());
    }

    @Test
    void robotMovesThePointer() throws Exception {
        show(new JLabel("pointer"));
        robot.mouseMove(150, 170);
        settle();
        assertEquals(new Point(150, 170), java.awt.MouseInfo.getPointerInfo().getLocation());
    }

    @Test
    void robotTypes() throws Exception {
        JTextField field = new JTextField();
        show(field);
        click(field);
        robot.keyPress(KeyEvent.VK_SHIFT);
        type(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        type(KeyEvent.VK_B, KeyEvent.VK_1, KeyEvent.VK_SPACE, KeyEvent.VK_C, KeyEvent.VK_BACK_SPACE);
        assertEquals("Ab1 ", field.getText());
    }

    @Test
    void robotWheels() throws Exception {
        AtomicInteger rotation = new AtomicInteger();
        JPanel panel = new JPanel();
        panel.addMouseWheelListener(e -> rotation.addAndGet(e.getWheelRotation()));
        show(panel);
        Point p = center(panel);
        robot.mouseMove(p.x, p.y);
        settle();
        robot.mouseWheel(3);
        settle();
        robot.mouseWheel(-1);
        settle();
        assertEquals(2, rotation.get());
    }

    @Test
    void robotCaptures() throws Exception {
        JPanel panel = new JPanel(new GridLayout(1, 3));
        for (Color color : new Color[] {Color.RED, Color.GREEN, Color.BLUE}) {
            JPanel p = new JPanel();
            p.setBackground(color);
            panel.add(p);
        }
        show(panel);
        Point[] centers = {center(panel.getComponent(0)), center(panel.getComponent(1)), center(panel.getComponent(2))};
        assertEquals(Color.RED, robot.getPixelColor(centers[0].x, centers[0].y));
        assertEquals(Color.GREEN, robot.getPixelColor(centers[1].x, centers[1].y));
        assertEquals(Color.BLUE, robot.getPixelColor(centers[2].x, centers[2].y));

        // partly off the screen, what is outside has to come back black
        BufferedImage capture = robot.createScreenCapture(new Rectangle(centers[2].x, centers[2].y, 2, 2));
        assertEquals(Color.BLUE.getRGB(), capture.getRGB(1, 1));
        Rectangle screen = frame.getGraphicsConfiguration().getBounds();
        capture = robot.createScreenCapture(new Rectangle(screen.width - 1, 0, 3, 1));
        assertEquals(0xff000000, capture.getRGB(2, 0));
    }

    // ---- selections

    /** Another X client, on a connection of its own. */
    private static final class Client implements AutoCloseable {

        final Display display;
        final Window window;
        final Atom utf8, targets, property;
        final Atom selection;
        private final Thread reader;
        private volatile boolean closed;
        volatile String offering;
        private final CountDownLatch notified = new CountDownLatch(1);
        private volatile int notifiedProperty;

        Client(String selectionName) throws Exception {
            String name = System.getenv("DISPLAY");
            display = new Display(new UnixDomainSocket("/tmp/.X11-unix/X" + name.substring(name.indexOf(':') + 1)), "localhost", 0, 0);
            window = new Window(display.getDefaultRoot(), 0, 0, 1, 1, 0, new WindowAttributes());
            utf8 = Atom.intern(display, "UTF8_STRING");
            targets = Atom.intern(display, "TARGETS");
            property = Atom.intern(display, "TEST_CLIENT");
            selection = Atom.intern(display, selectionName);
            reader = new Thread(this::read, "test client");
            reader.setDaemon(true);
            reader.start();
        }

        private void read() {
            while (!closed) {
                Event event = display.pollEvent();
                if (event == null) {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        return;
                    }
                } else if (event instanceof SelectionNotify e) {
                    notifiedProperty = e.getPropertyAtomID();
                    notified.countDown();
                } else if (event instanceof SelectionRequest e) {
                    Window requestor = (Window) Window.intern(display, e.getRequestorWindowID());
                    boolean ok = false;
                    if (e.getTargetAtomID() == targets.getID()) {
                        requestor.changeProperty(Window.PropertyMode.REPLACE, (Atom) Atom.intern(display, e.getPropertyAtomID()),
                                Atom.ATOM, 32, new int[] {targets.getID(), utf8.getID()}, 0, 32);
                        ok = true;
                    } else if (e.getTargetAtomID() == utf8.getID()) {
                        requestor.changeProperty(Window.PropertyMode.REPLACE, (Atom) Atom.intern(display, e.getPropertyAtomID()),
                                utf8, 8, offering.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, 8);
                        ok = true;
                    }
                    requestor.sendEvent(false, 0, new SelectionNotify(display, e.getTime(), e.getRequestorWindowID(),
                            e.getSelectionAtomID(), e.getTargetAtomID(), ok ? e.getPropertyAtomID() : 0));
                    display.flush();
                }
            }
        }

        /** Takes the selection over. */
        void offer(String text) {
            offering = text;
            window.setSelectionOwner(selection, Display.CURRENT_TIME);
            display.flush();
        }

        /** What the owner of the selection hands over for a target. */
        Window.Property fetch(Atom target) throws Exception {
            window.convertSelection(selection, target, property, Display.CURRENT_TIME);
            display.flush();
            assertTrue(notified.await(5, TimeUnit.SECONDS), "no SelectionNotify");
            assertTrue(notifiedProperty != 0, "the owner refused");
            return window.getProperty(true, property, Atom.ANY_PROPERTY_TYPE, 0, 1 << 20);
        }

        @Override
        public void close() throws Exception {
            closed = true;
            reader.join(1000);
        }
    }

    private void selectionToOtherClient(Clipboard clipboard, String selectionName) throws Exception {
        String text = "from java カチョ";
        clipboard.setContents(new StringSelection(text), null);
        settle();
        try (Client client = new Client(selectionName)) {
            Window.Property p = client.fetch(client.utf8);
            assertEquals(client.utf8.getID(), p.typeID());
            assertEquals(text, new String(p.data(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void selectionFromOtherClient(Clipboard clipboard) throws Exception {
        String selectionName = clipboard.getName();
        String text = "from the other client チーズ";
        try (Client client = new Client(selectionName)) {
            client.offer(text);
            settle();
            Transferable contents = clipboard.getContents(null);
            assertNotNull(contents);
            assertTrue(contents.isDataFlavorSupported(DataFlavor.stringFlavor));
            assertEquals(text, contents.getTransferData(DataFlavor.stringFlavor));
        }
    }

    @Test
    void clipboardToOtherClient() throws Exception {
        selectionToOtherClient(Toolkit.getDefaultToolkit().getSystemClipboard(), "CLIPBOARD");
    }

    @Test
    void clipboardFromOtherClient() throws Exception {
        selectionFromOtherClient(Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    @Test
    void primarySelection() throws Exception {
        Clipboard selection = Toolkit.getDefaultToolkit().getSystemSelection();
        assertNotNull(selection);
        selectionToOtherClient(selection, "PRIMARY");
        selectionFromOtherClient(selection);
    }

    @Test
    void losingTheClipboardIsReported() throws Exception {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        CountDownLatch lost = new CountDownLatch(1);
        ClipboardOwner owner = (c, t) -> lost.countDown();
        clipboard.setContents(new StringSelection("mine"), owner);
        settle();
        try (Client client = new Client("CLIPBOARD")) {
            client.offer("not anymore");
            assertTrue(lost.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void swingCopyAndPasteOverTheClipboard() throws Exception {
        JTextField from = new JTextField("copy me");
        JTextField to = new JTextField();
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(from);
        panel.add(to);
        show(panel);
        try (Client client = new Client("CLIPBOARD")) {
            click(from);
            ctrl(KeyEvent.VK_A);
            ctrl(KeyEvent.VK_C);
            assertEquals("copy me", new String(client.fetch(client.utf8).data(), java.nio.charset.StandardCharsets.UTF_8));
            client.offer("pasted from elsewhere");
            settle();
            click(to);
            ctrl(KeyEvent.VK_V);
            assertEquals("pasted from elsewhere", to.getText());
        }
    }

    // ---- cursors

    /** Whether the X window of the frame has the X cursor of an AWT one. */
    private boolean showsCursor(Cursor cursor) throws Exception {
        XGraphicsDevice device = EscherToolkit.getDefaultDevice();
        settle();
        return new XTest(device.getDisplay()).compareCursor(device.getWindowOf(frame).xwindow, device.getXCursor(cursor));
    }

    private void pointAt(Component c) throws Exception {
        Point p = center(c);
        // twice, to be sure that this is a move
        robot.mouseMove(p.x - 1, p.y);
        robot.mouseMove(p.x, p.y);
    }

    @Test
    void cursorFollowsTheComponentUnderThePointer() throws Exception {
        JTextField text = new JTextField("an I-beam");
        JLabel hand = new JLabel("a hand");
        hand.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel plain = new JLabel("nothing special");
        JPanel panel = new JPanel(new GridLayout(3, 1));
        panel.add(text);
        panel.add(hand);
        panel.add(plain);
        show(panel);

        pointAt(text);
        assertTrue(showsCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)));
        assertFalse(showsCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)));
        pointAt(hand);
        assertTrue(showsCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)));
        pointAt(plain);
        assertTrue(showsCursor(Cursor.getDefaultCursor()));
    }

    @Test
    void everyPredefinedCursorShows() throws Exception {
        JPanel panel = new JPanel();
        show(panel);
        for (int type = Cursor.DEFAULT_CURSOR; type <= Cursor.MOVE_CURSOR; type++) {
            Cursor cursor = Cursor.getPredefinedCursor(type);
            panel.setCursor(cursor);
            pointAt(panel);
            assertTrue(showsCursor(cursor), cursor.getName());
        }
    }

    @Test
    void customCursor() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 16; i++) {
            image.setRGB(i, i, 0xff000000);
            image.setRGB(15 - i, i, 0xffffffff);
        }
        Cursor cursor = Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(8, 8), "cross");
        assertEquals(Cursor.CUSTOM_CURSOR, cursor.getType());
        JPanel panel = new JPanel();
        panel.setCursor(cursor);
        show(panel);
        pointAt(panel);
        assertTrue(showsCursor(cursor));
    }

    // ---- drag and drop

    @Test
    void swingDragFromListToTextField() throws Exception {
        JList<String> list = new JList<>(new String[] {"dragged text", "other"});
        list.setDragEnabled(true);
        list.setSelectedIndex(0);
        JTextArea text = new JTextArea();
        JPanel panel = new JPanel(new GridLayout(1, 2));
        panel.add(new JScrollPane(list));
        panel.add(text);
        show(panel);

        Rectangle cell = list.getCellBounds(0, 0);
        Point listAt = list.getLocationOnScreen();
        Point a = new Point(listAt.x + cell.x + 10, listAt.y + cell.y + cell.height / 2);
        Point b = center(text);
        robot.mouseMove(a.x, a.y);
        settle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 20; i++) {
            robot.mouseMove(a.x + (b.x - a.x) * i / 20, a.y + (b.y - a.y) * i / 20);
            robot.delay(10);
        }
        settle();
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
        assertEquals("dragged text", text.getText());
    }

    /** A plain AWT source and target, with their own listeners. */
    private final class Awt {

        final JLabel source = new JLabel("source");
        final JLabel target = new JLabel("target");
        final AtomicReference<String> dropped = new AtomicReference<>();
        final AtomicBoolean entered = new AtomicBoolean();
        final AtomicReference<Boolean> finished = new AtomicReference<>();
        final CountDownLatch end = new CountDownLatch(1);

        Awt() throws Exception {
            this(false);
        }

        Awt(boolean twoWindows) throws Exception {
            DragSource ds = DragSource.getDefaultDragSource();
            DragGestureListener gesture = e -> e.startDrag(null, new StringSelection("awt payload"), new DragSourceAdapter() {
                @Override
                public void dragDropEnd(DragSourceDropEvent dsde) {
                    finished.set(dsde.getDropSuccess());
                    end.countDown();
                }
            });
            assertNotNull(ds.createDefaultDragGestureRecognizer(source, DnDConstants.ACTION_COPY_OR_MOVE, gesture));
            new DropTarget(target, new DropTargetAdapter() {
                @Override
                public void dragEnter(java.awt.dnd.DropTargetDragEvent dtde) {
                    entered.set(true);
                }

                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        dropped.set((String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor));
                        dtde.dropComplete(true);
                    } catch (Exception e) {
                        dtde.dropComplete(false);
                    }
                }
            });
            JPanel panel = new JPanel(new GridLayout(2, 1));
            source.setOpaque(true);
            target.setOpaque(true);
            panel.add(source);
            if (twoWindows) {
                SwingUtilities.invokeAndWait(() -> {
                    other = new JFrame("the other window");
                    other.getContentPane().add(target);
                    other.setBounds(560, 100, 300, 200);
                    other.setVisible(true);
                });
            } else {
                panel.add(target);
            }
            show(panel);
            if (twoWindows) {
                // a window manager goes by its own idea of a place for a new window
                SwingUtilities.invokeAndWait(() -> {
                    frame.setLocation(100, 100);
                    other.setLocation(560, 100);
                });
                settle();
                assertFalse(frame.getBounds().intersects(other.getBounds()));
            }
        }
    }

    @Test
    void awtDragAndDrop() throws Exception {
        Awt awt = new Awt();
        drag(awt.source, awt.target);
        assertTrue(awt.entered.get());
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        assertTrue(awt.end.await(5, TimeUnit.SECONDS));
        settle();
        assertEquals("awt payload", awt.dropped.get());
        assertEquals(Boolean.TRUE, awt.finished.get());
        assertFalse(sun.awt.dnd.SunDragSourceContextPeer.isDragDropInProgress());
    }

    @Test
    void dragBetweenTwoWindows() throws Exception {
        Awt awt = new Awt(true);
        drag(awt.source, awt.target);
        assertTrue(awt.entered.get());
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        assertTrue(awt.end.await(5, TimeUnit.SECONDS));
        settle();
        assertEquals("awt payload", awt.dropped.get());
        assertEquals(Boolean.TRUE, awt.finished.get());
    }

    @Test
    void escapeCancelsADrag() throws Exception {
        Awt awt = new Awt();
        drag(awt.source, awt.target);
        type(KeyEvent.VK_ESCAPE);
        assertTrue(awt.end.await(5, TimeUnit.SECONDS));
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        settle();
        assertEquals(null, awt.dropped.get());
        assertEquals(Boolean.FALSE, awt.finished.get());
    }

    @Test
    void droppingOutsideOfAnyTargetFails() throws Exception {
        Awt awt = new Awt();
        Point a = center(awt.source);
        robot.mouseMove(a.x, a.y);
        settle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 12; i++) {
            robot.mouseMove(a.x + i * 8, a.y);
            robot.delay(10);
        }
        settle();
        robot.mouseMove(700, 700); // the bare root window
        settle();
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        assertTrue(awt.end.await(5, TimeUnit.SECONDS));
        assertEquals(Boolean.FALSE, awt.finished.get());
    }

    @BeforeEach
    void noLeftovers() {
        assertFalse(sun.awt.dnd.SunDragSourceContextPeer.isDragDropInProgress());
    }
}
