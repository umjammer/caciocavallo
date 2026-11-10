/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package gnu.java.awt.peer.x;

import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.BufferCapabilities.FlipContents;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.FocusEvent.Cause;
import java.awt.event.PaintEvent;
import java.awt.event.WindowEvent;
import java.awt.image.ColorModel;
import java.awt.peer.ComponentPeer;
import java.awt.peer.ContainerPeer;
import java.nio.charset.StandardCharsets;

import com.github.caciocavallosilano.cacio.peer.CacioComponent;
import com.github.caciocavallosilano.cacio.peer.PlatformToplevelWindow;
import com.github.caciocavallosilano.cacio.peer.PlatformWindow;
import gnu.x11.Atom;
import gnu.x11.Display;
import gnu.x11.Window;
import gnu.x11.WindowAttributes;
import gnu.x11.event.Event;
import sun.awt.SunToolkit;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.pipe.Region;


class EscherPlatformWindow implements PlatformToplevelWindow {

    private static final int standardSelect =
            Event.EventMask.BUTTON_PRESS_MASK.getMask()
                    | Event.EventMask.BUTTON_RELEASE_MASK.getMask() | Event.EventMask.POINTER_MOTION_MASK.getMask()
                    | Event.EventMask.ENTER_WINDOW_MASK.getMask() | Event.EventMask.LEAVE_WINDOW_MASK.getMask()
                    // | Event.RESIZE_REDIRECT_MASK.getMask() //
                    | Event.EventMask.EXPOSURE_MASK.getMask() | Event.EventMask.PROPERTY_CHANGE_MASK.getMask()
                    | Event.EventMask.STRUCTURE_NOTIFY_MASK.getMask() //
                    // | Event.SUBSTRUCTURE_NOTIFY_MASK.getMask()
                    | Event.EventMask.KEY_PRESS_MASK.getMask() | Event.EventMask.KEY_RELEASE_MASK.getMask()
                    // | Event.VISIBILITY_CHANGE_MASK.getMask() //
                    | Event.EventMask.FOCUS_CHANGE_MASK.getMask();

    /**
     * The corresponding CacioCavallo component.
     */
    private final CacioComponent cacioComponent;

    /**
     * The X window.
     */
    final Window xwindow;

    /**
     * The surface data object for this window.
     */
    private final SurfaceData surfaceData;

    /**
     * The frame insets. These get updated in {@link #show()}.
     */
    private Insets insets;

    /**
     * The background color of this component.
     */
    private Color background;

    /**
     * The foreground color of this component.
     */
    private Color foreground;

    /**
     * The font of this component.
     */
    private Font font;

    /**
     * Whether this window is on the screen as far as the X server is concerned.
     * Set from the event pump.
     */
    private volatile boolean mapped;

    /**
     * Whether a modal dialog is keeping input away from this window.
     */
    private volatile boolean blocked;

    /**
     * The cursor that was set last.
     */
    private volatile java.awt.Cursor currentCursor;

    EscherPlatformWindow(CacioComponent cacioComp, PlatformWindow parent) {

        cacioComponent = cacioComp;
        Component awtComp = cacioComp.getAWTComponent();
        XGraphicsDevice dev = EscherToolkit.getDefaultDevice();

        // TODO: Maybe initialize lazily in show().
        WindowAttributes atts = new WindowAttributes();

        // FIXME: Howto generate a Window without decorations?
        int x = Math.max(awtComp.getX(), 0);
        int y = Math.max(awtComp.getY(), 0);
        int w = Math.max(awtComp.getWidth(), 1);
        int h = Math.max(awtComp.getHeight(), 1);

        Window parentWindow = null;

        Insets parentInsets;
        if (parent == null) {
            parentWindow = dev.getDisplay().getDefaultRoot(); // TODO
            parentInsets = new Insets(0, 0, 0, 0);
            // A plain toplevel window is a popup, a tooltip or the like, which
            // has to show up where it was asked to and without a title bar.
            atts.setOverrideRedirect(!isFocusableToplevel(awtComp));
        } else {
            parentWindow = ((EscherPlatformWindow) parent).xwindow;
            parentInsets = parent.getInsets();
        }

        xwindow = new Window(parentWindow, x - parentInsets.left,
                y - parentInsets.top, w, h, 0, atts);
        xwindow.selectInput(standardSelect);

        dev.registerWindow(this);

        xwindow.setWMDeleteWindow();

        awtComp.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));

        boolean undecorated;
        if (awtComp instanceof Frame f) {
            undecorated = f.isUndecorated();
            setTitle(f.getTitle()); // it may have been set before there was a peer
        } else if (awtComp instanceof Dialog d) {
            undecorated = d.isUndecorated();
            setTitle(d.getTitle());
        } else {
            // Everything else is undecorated by default.
            undecorated = true;
        }
        if (undecorated) {
            // First try the Motif implementation of undecorated frames. This
            // is semantically closest and supported by all major window
            // managers.
            // TODO: At the time of writing this, there's no freedesktop.org
            // standard extension that matches the required semantic. Maybe
            // undecorated frames are added in the future, if so, then use
            // these.
            Atom at = Atom.intern(dev.getDisplay(), "_MOTIF_WM_HINTS");
            if (at != null) {
                xwindow.changeProperty(Window.PropertyMode.REPLACE, at, at, 32, new int[] {
                        1 << 1, 0, 0, 0, 0}, 0, 5);
            }
        }
        // A window manager's frame goes around the X window, it is not part of
        // the AWT component: an AWT location is that of the X window itself.
        insets = new Insets(0, 0, 0, 0);

        // Setup the surface data.
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        surfaceData =
                new XDrawableSurfaceData((XGraphicsConfiguration) gc, xwindow,
                        XDrawableSurfaceData.EscherIntRgb,
                        gc.getColorModel());

        if (awtComp.isVisible())
            show();
    }

    @Override
    public void applyShape(Region shape) {
    }

    @Override
    public void dispose() {
        XGraphicsDevice dev = EscherToolkit.getDefaultDevice();
        dev.unregisterWindow(this);
        SunToolkit.awtLock();
        try {
            ((XDrawableSurfaceData) surfaceData).dispose();
            xwindow.destroyWindow();
            dev.getDisplay().flush();
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    @Override
    public Rectangle getBounds() {
        Point p = getLocationOnScreen();
        return new Rectangle(p.x, p.y, xwindow.width, xwindow.height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height, int op) {
        Insets i = insets;
        Insets parentInsets = getParentToplevelInsets();
        // X rejects an empty window
        int w = Math.max(width - i.left - i.right, 1);
        int h = Math.max(height - i.top - i.bottom, 1);
        int px = x - i.left - parentInsets.left;
        int py = y - i.top - parentInsets.top;
        switch (op & ~ComponentPeer.NO_EMBEDDED_CHECK) {
            case ComponentPeer.SET_BOUNDS -> xwindow.moveResize(px, py, w, h);
            case ComponentPeer.SET_LOCATION -> xwindow.move(px, py);
            case ComponentPeer.SET_SIZE -> xwindow.resize(w, h);
            case ComponentPeer.SET_CLIENT_SIZE -> xwindow.resize(Math.max(width, 1), Math.max(height, 1));
            default -> { }
        }
    }

    private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);

    private Insets getParentToplevelInsets() {
        Insets insets;
        Component awtComp = cacioComponent.getAWTComponent();
        Component parent = awtComp.getParent();
        if (parent instanceof java.awt.Window) {
            insets = ((java.awt.Window) parent).getInsets();
        } else {
            insets = EMPTY_INSETS;
        }
        return insets;
    }

    @Override
    public ColorModel getColorModel() {
        return getGraphicsConfiguration().getColorModel();
    }

    @Override
    public Graphics2D getGraphics(Color foreground, Color background, Font font) {
        Component awtComponent = cacioComponent.getAWTComponent();
        Color fg = foreground;
        if (fg == null)
            fg = Color.BLACK;
        Color bg = background;
        if (bg == null)
            bg = Color.WHITE;
        SunGraphics2D sg2d = new SunGraphics2D(surfaceData, fg, bg, font);
        sg2d.translate(-insets.left, -insets.top);
        return sg2d;
    }

    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
        // Has to be the same instance every time: swing keeps its back buffers
        // per configuration.
        return EscherToolkit.getDefaultDevice().getDefaultConfiguration();
    }

    @Override
    public Insets getInsets() {
        return (Insets) insets.clone();
    }

    @Override
    public Point getLocationOnScreen() {
        // the window's own position is relative to its parent, which is the
        // frame of a window manager as soon as there is one
        Window root = EscherToolkit.getDefaultDevice().getDisplay().getDefaultRoot();
        Window.Coordinates c = root.translateCoordinates(xwindow, 0, 0);
        return new Point(c.x() - insets.left, c.y() - insets.top);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            show();
        } else {
            hide();
        }
    }

    private void show() {
        // Prevent ResizeRedirect events.
        //xwindow.select_input(Event.NO_EVENT_MASK);
        //xwindow.select_input(noResizeRedirectSelect);

        xwindow.map();
        EventQueue eq = Toolkit.getDefaultToolkit().getSystemEventQueue();
        Component awtComponent = cacioComponent.getAWTComponent();
        if (awtComponent instanceof java.awt.Window w) {
            eq.postEvent(new WindowEvent(w, WindowEvent.WINDOW_OPENED));
        }
        eq.postEvent(new PaintEvent(awtComponent, PaintEvent.PAINT,
                new Rectangle(0, 0, awtComponent.getWidth(), awtComponent.getHeight())));
        Graphics g = getGraphics(foreground, background, font);
        g.clearRect(0, 0, awtComponent.getWidth(), awtComponent.getHeight());
        g.dispose();
//      // Reset input selection.
//      atts.set_override_redirect(false);
//      xwindow.change_attributes(atts);
    }

    /**
     * Makes the component invisible. This is called from
     * {@link Component#hide()}.
     * <p>
     * This is implemented to call setVisible(false) on the Swing component.
     */
    private void hide() {
        xwindow.unmap();
    }

    @Override
    public boolean canDetermineObscurity() {
        return false;
    }

    @Override
    public boolean isObscured() {
        return false;
    }

    @Override
    public boolean isReparentSuppored() {
        return false;
    }

    @Override
    public void reparent(ContainerPeer newContainer) {
    }

    @Override
    public boolean isRestackSupported() {
        return false;
    }

    @Override
    public void restack() {
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public void setMaximizedBounds(Rectangle bounds) {
    }

    @Override
    public void setResizable(boolean resizable) {
    }

    @Override
    public void setState(int state) {
    }

    @Override
    public void setTitle(String title) {
        String name = title != null ? title : "";
        xwindow.setWMName(name);
        // WM_NAME is latin1, anything else needs the EWMH one to survive
        Display display = xwindow.getDisplay();
        xwindow.changeProperty(Window.PropertyMode.REPLACE,
                Atom.intern(display, "_NET_WM_NAME"), Atom.intern(display, "UTF8_STRING"),
                8, name.getBytes(StandardCharsets.UTF_8), 0, 8);
    }

    public boolean requestFocus(Component lightweightChild, boolean temporary,
                                boolean focusedWindowChangeAllowed, long time,
                                Cause cause) {

        takeFocus();

        return true;
    }

    @Override
    public void createBuffers(int numBuffers, BufferCapabilities caps) throws AWTException {
        throw new AWTException("Not yet implemented.");
    }

    @Override
    public void destroyBuffers() {
        // Nothing to do here yet.
    }

    @Override
    public void flip(int x1, int y1, int x2, int y2, FlipContents flipAction) {
        // Nothing to do here yet.
    }

    @Override
    public Image getBackBuffer() {
        // Nothing to do here yet.
        return null;
    }

    @Override
    public void setCursor(java.awt.Cursor cursor) {
        if (cursor != currentCursor) {
            currentCursor = cursor;
            gnu.x11.Cursor xcursor = EscherToolkit.getDefaultDevice().getXCursor(cursor);
            if (xcursor != null) {
                WindowAttributes attributes = new WindowAttributes();
                attributes.setCursor(xcursor);
                xwindow.changeAttributes(attributes);
                xwindow.getDisplay().flush();
            }
        }
    }

    @Override
    public void requestFocus() {
        takeFocus();
    }

    @Override
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    boolean isBlocked() {
        return blocked;
    }

    /**
     * @return whether the component is a window that takes the keyboard, unlike
     *         a popup that just shows up over the one that has it
     */
    private static boolean isFocusableToplevel(Component c) {
        return c instanceof Frame || c instanceof Dialog;
    }

    /**
     * Moves the X input focus here, if this is a window for that. Without a
     * window manager nobody else does, and X rejects it for an unmapped window.
     */
    void takeFocus() {
        if (mapped && !blocked && isFocusableToplevel(cacioComponent.getAWTComponent())) {
            xwindow.setInputFocus();
            xwindow.getDisplay().flush();
        }
    }

    /**
     * Called from the event pump when the X server has mapped or unmapped this
     * window.
     */
    void mapped(boolean mapped) {
        this.mapped = mapped;
        if (mapped) {
            takeFocus();
        }
    }

    /**
     * Enters where the window really is. {@link Window#move(int, int)} skips what
     * it takes for no change, so a position a window manager came up with has to
     * be known to it, or going back to the requested one would do nothing.
     */
    void locationChanged(Point p) {
        xwindow.setGeometryCache(new gnu.x11.Rectangle(p.x, p.y, xwindow.width, xwindow.height));
    }

    /**
     * Enters a size the X server reports.
     *
     * @return whether it differs from the one that was known so far
     */
    boolean sizeChanged(int width, int height) {
        if (xwindow.width == width && xwindow.height == height) {
            return false;
        }
        xwindow.width = width;
        xwindow.height = height;
        return true;
    }

    /**
     * @return
     */
    public CacioComponent getCacioComponent() {
        return cacioComponent;
    }
}
