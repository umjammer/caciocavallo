package gnu.java.awt.peer.x;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.peer.RobotPeer;
import java.util.HashMap;
import java.util.Map;

import gnu.x11.Display;
import gnu.x11.EscherUnsupportedScreenBitDepthException;
import gnu.x11.RGB;
import gnu.x11.Window;
import gnu.x11.extension.NotFoundException;
import gnu.x11.extension.XTest;
import gnu.x11.image.Image;
import gnu.x11.image.ZPixmap;


/**
 * A robot on top of the XTEST extension, so that what it does comes back through
 * the X server like real input. Screen captures read the root window, which a
 * rootless server like XQuartz keeps empty.
 */
final class EscherRobotPeer implements RobotPeer {

    /** The X keycodes are in this range according to the protocol. */
    private static final int MIN_KEYCODE = 8, MAX_KEYCODE = 255;

    private final Display display;
    private final Window root;
    private final XTest xtest;
    private final Rectangle screen;

    /** AWT virtual key to X keycode. */
    private final Map<Integer, Integer> keycodes = new HashMap<>();

    EscherRobotPeer(XGraphicsDevice device) throws AWTException {
        display = device.getDisplay();
        root = display.getDefaultRoot();
        screen = device.getDefaultConfiguration().getBounds();
        try {
            xtest = new XTest(display);
        } catch (NotFoundException e) {
            AWTException awtException = new AWTException("the X server has no XTEST extension");
            awtException.initCause(e);
            throw awtException;
        }
        for (int keycode = MAX_KEYCODE; keycode >= MIN_KEYCODE; keycode--) {
            int vk = KeyboardMapping.mapToKeyCode(display.getInput(), keycode, 0);
            if (vk != KeyEvent.VK_UNDEFINED) {
                keycodes.put(vk, keycode);
            }
        }
    }

    @Override
    public void mouseMove(int x, int y) {
        xtest.fakeMotionEvent(root, x, y, false, 0);
        display.flush();
    }

    private static int button(int mask) {
        return (mask & InputEvent.BUTTON1_DOWN_MASK) != 0 ? 1
                : (mask & InputEvent.BUTTON2_DOWN_MASK) != 0 ? 2
                : (mask & InputEvent.BUTTON3_DOWN_MASK) != 0 ? 3
                : 0;
    }

    private void buttons(int mask, boolean press) {
        // more than one can be given at a time
        for (int bit : new int[] {InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK}) {
            if ((mask & bit) != 0) {
                xtest.fake_button_event(button(bit), press, 0);
            }
        }
        display.flush();
    }

    @Override
    public void mousePress(int buttons) {
        buttons(buttons, true);
    }

    @Override
    public void mouseRelease(int buttons) {
        buttons(buttons, false);
    }

    @Override
    public void mouseWheel(int wheelAmt) {
        int button = wheelAmt < 0 ? 4 : 5;
        for (int i = 0; i < Math.abs(wheelAmt); i++) {
            xtest.fake_button_event(button, true, 0);
            xtest.fake_button_event(button, false, 0);
        }
        display.flush();
    }

    private void key(int keycode, boolean press) {
        Integer xKeycode = keycodes.get(keycode);
        if (xKeycode == null) {
            throw new IllegalArgumentException("Invalid key code: " + keycode);
        }
        xtest.fakeKeyEvent(xKeycode, press, 0);
        display.flush();
    }

    @Override
    public void keyPress(int keycode) {
        key(keycode, true);
    }

    @Override
    public void keyRelease(int keycode) {
        key(keycode, false);
    }

    @Override
    public int getRGBPixel(int x, int y) {
        return getRGBPixels(new Rectangle(x, y, 1, 1))[0];
    }

    @Override
    public int[] getRGBPixels(Rectangle bounds) {
        int[] pixels = new int[bounds.width * bounds.height];
        // X refuses anything that reaches outside of the screen
        Rectangle r = bounds.intersection(screen);
        if (r.isEmpty()) {
            return pixels;
        }
        ZPixmap image;
        try {
            image = (ZPixmap) root.image(r.x, r.y, r.width, r.height, -1, Image.Format.ZPIXMAP);
        } catch (EscherUnsupportedScreenBitDepthException e) {
            throw new IllegalStateException(e);
        }
        for (int y = 0; y < r.height; y++) {
            int row = (r.y - bounds.y + y) * bounds.width + r.x - bounds.x;
            for (int x = 0; x < r.width; x++) {
                RGB rgb = image.getRGB(x, y);
                pixels[row + x] = 0xff00_0000 | (rgb.getRed() & 0xff) << 16 | (rgb.getGreen() & 0xff) << 8 | rgb.getBlue() & 0xff;
            }
        }
        return pixels;
    }
}
