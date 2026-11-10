package gnu.java.awt.peer.x;

import java.awt.Image;
import java.awt.Point;

import gnu.x11.Cursor;
import gnu.x11.Display;
import gnu.x11.GC;
import gnu.x11.Pixmap;
import sun.awt.CustomCursor;


/**
 * {@code Toolkit.createCustomCursor}. The core protocol has two colored cursors,
 * so the image gets reduced to black and white.
 */
final class EscherCustomCursor extends CustomCursor {

    private static final int OPAQUE = 128;
    private static final int LIGHT = 128;

    /** Assigned while the super constructor runs, so it must have no initializer. */
    private transient Cursor xcursor;

    EscherCustomCursor(Image image, Point hotSpot, String name) {
        super(image, hotSpot, name);
    }

    Cursor xcursor() {
        return xcursor;
    }

    @Override
    protected void createNativeCursor(Image image, int[] pixels, int width, int height, int xHotSpot, int yHotSpot) {
        Display display = EscherToolkit.getDefaultDevice().getDisplay();
        Pixmap source = new Pixmap(display.getDefaultRoot(), width, height, 1);
        Pixmap mask = new Pixmap(display.getDefaultRoot(), width, height, 1);
        GC gc = new GC(source);
        gc.setForeground(0);
        source.fill_rectangle(gc, 0, 0, width, height);
        mask.fill_rectangle(gc, 0, 0, width, height);
        gc.setForeground(1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                if (argb >>> 24 >= OPAQUE) {
                    mask.point(gc, x, y);
                    int luminance = (((argb >> 16) & 0xff) * 3 + ((argb >> 8) & 0xff) * 6 + (argb & 0xff)) / 10;
                    if (luminance < LIGHT) {
                        source.point(gc, x, y); // a set bit is the foreground, black
                    }
                }
            }
        }
        xcursor = new Cursor(source, mask, null, null, 0, 0, 0, 0xffff, 0xffff, 0xffff, xHotSpot, yHotSpot);
        gc.free();
        source.free();
        mask.free();
        display.flush();
    }
}
