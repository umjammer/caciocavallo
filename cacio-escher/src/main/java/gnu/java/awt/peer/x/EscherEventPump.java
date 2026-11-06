/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowEvent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import com.github.caciocavallosilano.cacio.peer.CacioComponent;
import com.github.caciocavallosilano.cacio.peer.CacioEventPump;
import com.github.caciocavallosilano.cacio.peer.CacioMouseInfoPeer;
import gnu.x11.Display;
import gnu.x11.event.ButtonPress;
import gnu.x11.event.ButtonRelease;
import gnu.x11.event.ClientMessage;
import gnu.x11.event.ConfigureNotify;
import gnu.x11.event.EnterNotify;
import gnu.x11.event.Event;
import gnu.x11.event.Expose;
import gnu.x11.event.FocusIn;
import gnu.x11.event.FocusOut;
import gnu.x11.event.Input;
import gnu.x11.event.LeaveNotify;
import gnu.x11.event.MapNotify;
import gnu.x11.event.MotionNotify;
import gnu.x11.event.UnmapNotify;
import sun.awt.AWTAccessor;
import sun.awt.SunToolkit;


/**
 * Fetches events from X, translates them to AWT events and pumps them up
 * into the AWT event queue.
 *
 * @author Roman Kennke (kennke@aicas.com)
 */
class EscherEventPump extends CacioEventPump<Event> {

    private static final Logger logger = System.getLogger(EscherEventPump.class.getName());

    /** How long two presses may be apart to count as a multi click. */
    private static final long MULTI_CLICK_MILLIS = 500;

    /** How far the pointer may travel for a press and a release to still be a click. */
    private static final int CLICK_SLOP = 4;

    /** The buttons being held down, as extended modifiers. */
    private int buttonsDown;

    private int clickCount;
    private int clickButton;
    private long lastPressTime;
    private int pressX, pressY;
    private EscherPlatformWindow pressWindow;

    /** The window that holds the X input focus. */
    private EscherPlatformWindow focused;

    private static XGraphicsDevice device() {
        return (XGraphicsDevice) GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    @Override
    protected Event fetchNativeEvent() {
        Event event = null;
        SunToolkit.awtLock();
        try {
            Display display = device().getDisplay();
            // never block here, this is holding the lock everything else needs
            event = display.pollEvent();
        } catch (gnu.x11.Error e) {
            // an error for something long gone, not worth losing the pump for
            logger.log(Level.WARNING, "asynchronous X error: " + e);
        } finally {
            SunToolkit.awtUnlock();
        }
        if (event == null) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
        return event;
    }

    @Override
    protected void dispatchNativeEvent(Event event) {
        if (event == null) {
            return;
        }
        try {
            dispatch(event);
        } catch (gnu.x11.Error e) {
            logger.log(Level.WARNING, "X error while dispatching " + event.code() + ": " + e);
        }
    }

    private void dispatch(Event event) {
        XGraphicsDevice device = device();
        switch (event) {
            case Expose e -> {
                EscherPlatformWindow w = device.getWindow(e.getWindowID());
                if (w != null) {
                    postPaintEvent(w.getCacioComponent(), e.x(), e.y(), e.width(), e.height());
                }
            }
            case MapNotify e -> {
                EscherPlatformWindow w = device.getWindow(e.getWindowID());
                if (w != null) {
                    w.mapped(true);
                }
            }
            case UnmapNotify e -> {
                EscherPlatformWindow w = device.getWindow(e.getWindowID());
                if (w != null) {
                    w.mapped(false);
                }
            }
            case ConfigureNotify e -> {
                EscherPlatformWindow w = device.getWindow(e.getWindowID());
                if (w != null) {
                    configure(w, e.width(), e.height());
                }
            }
            case MotionNotify e -> motion(device.getWindow(e.getEventWindowID()), e);
            case ButtonPress e -> press(device.getWindow(e.getEventWindowID()), e);
            case ButtonRelease e -> release(device.getWindow(e.getEventWindowID()), e);
            case EnterNotify e -> crossing(device.getWindow(e.getEventWindowID()), e, MouseEvent.MOUSE_ENTERED);
            case LeaveNotify e -> crossing(device.getWindow(e.getEventWindowID()), e, MouseEvent.MOUSE_EXITED);
            case gnu.x11.event.KeyPress e -> key(device.getWindow(e.getEventWindowID()), e, true);
            case gnu.x11.event.KeyRelease e -> key(device.getWindow(e.getEventWindowID()), e, false);
            case FocusIn e -> focusIn(device.getWindow(e.getEventWindowID()));
            case FocusOut e -> focusOut(device.getWindow(e.getEventWindowID()));
            case ClientMessage e -> {
                EscherPlatformWindow w = device.getWindow(e.getWindowID());
                if (w != null && e.deleteWindow()
                        && w.getCacioComponent().getAWTComponent() instanceof Window) {
                    postWindwoEvent(w.getCacioComponent(), WindowEvent.WINDOW_CLOSING, 0, 0);
                }
            }
            default -> logger.log(Level.TRACE, "unhandled X event: " + event.code());
        }
    }

    /**
     * Handles the size and position of a window as the X server has it. Requests
     * of our own come back through here as well, and they have already been
     * entered in the X window, so what is different here is news: the window
     * manager did it, or the user dragged a border.
     */
    private void configure(EscherPlatformWindow w, int width, int height) {
        CacioComponent source = w.getCacioComponent();
        if (!(source.getAWTComponent() instanceof Window window)) {
            return;
        }
        AWTAccessor.ComponentAccessor accessor = AWTAccessor.getComponentAccessor();
        if (w.sizeChanged(width, height)) {
            accessor.setSize(window, width, height);
            postComponentEvent(source, ComponentEvent.COMPONENT_RESIZED);
            postPaintEvent(source, 0, 0, width, height);
        }
        // a reparenting window manager reports these relative to its frame
        if (window.isShowing()) {
            Point p = w.getLocationOnScreen();
            if (p.x != window.getX() || p.y != window.getY()) {
                accessor.setLocation(window, p.x, p.y);
                postComponentEvent(source, ComponentEvent.COMPONENT_MOVED);
            }
        }
    }

    private static int buttonMask(int button) {
        return switch (button) {
            case 1 -> InputEvent.BUTTON1_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> 0;
        };
    }

    private static boolean isWheel(int button) {
        return button == 4 || button == 5;
    }

    private void pointerAt(Input e) {
        CacioMouseInfoPeer.getInstance().setMouseScreenCoordinates(e.getRootX(), e.getRootY());
    }

    private void post(EscherPlatformWindow w, int id, long when, int modifiers, int x, int y,
                      int clicks, boolean popupTrigger, int button) {
        CacioComponent source = w.getCacioComponent();
        source.handlePeerEvent(new MouseEvent(source.getAWTComponent(), id, when, modifiers,
                x, y, clicks, popupTrigger, button));
    }

    private void motion(EscherPlatformWindow w, MotionNotify e) {
        pointerAt(e);
        if (w == null || w.isBlocked()) {
            return;
        }
        int mods = KeyboardMapping.mapModifiers(e.getState()) | buttonsDown;
        int id = buttonsDown != 0 ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
        post(w, id, System.currentTimeMillis(), mods, e.getEventX(), e.getEventY(), 0, false, MouseEvent.NOBUTTON);
    }

    private void press(EscherPlatformWindow w, ButtonPress e) {
        pointerAt(e);
        if (w == null || w.isBlocked()) {
            return;
        }
        w.takeFocus();

        int button = e.detail();
        long when = System.currentTimeMillis();
        int mods = KeyboardMapping.mapModifiers(e.getState()) | buttonsDown;
        int x = e.getEventX();
        int y = e.getEventY();

        if (isWheel(button)) {
            CacioComponent source = w.getCacioComponent();
            source.handlePeerEvent(new MouseWheelEvent(source.getAWTComponent(), MouseEvent.MOUSE_WHEEL, when,
                    mods, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, button == 4 ? -1 : 1));
            return;
        }
        int mask = buttonMask(button);
        if (mask == 0) {
            return; // AWT does not do extra buttons here
        }

        boolean again = button == clickButton && w == pressWindow
                && when - lastPressTime <= MULTI_CLICK_MILLIS
                && Math.abs(x - pressX) <= CLICK_SLOP && Math.abs(y - pressY) <= CLICK_SLOP;
        clickCount = again ? clickCount + 1 : 1;
        clickButton = button;
        lastPressTime = when;
        pressX = x;
        pressY = y;
        pressWindow = w;

        buttonsDown |= mask;
        post(w, MouseEvent.MOUSE_PRESSED, when, mods | mask, x, y, clickCount, button == 3, button);
    }

    private void release(EscherPlatformWindow w, ButtonRelease e) {
        pointerAt(e);
        int button = e.detail();
        int mask = buttonMask(button);
        if (mask == 0) {
            return;
        }
        buttonsDown &= ~mask;
        if (w == null || w.isBlocked()) {
            return;
        }
        long when = System.currentTimeMillis();
        int mods = KeyboardMapping.mapModifiers(e.getState()) & ~mask | buttonsDown;
        int x = e.getEventX();
        int y = e.getEventY();
        post(w, MouseEvent.MOUSE_RELEASED, when, mods, x, y, clickCount, false, button);
        if (w == pressWindow && button == clickButton
                && Math.abs(x - pressX) <= CLICK_SLOP && Math.abs(y - pressY) <= CLICK_SLOP) {
            post(w, MouseEvent.MOUSE_CLICKED, when, mods, x, y, clickCount, false, button);
        }
    }

    private void crossing(EscherPlatformWindow w, Input e, int id) {
        pointerAt(e);
        if (w == null || w.isBlocked()) {
            return;
        }
        int mods = KeyboardMapping.mapModifiers(e.getState()) | buttonsDown;
        post(w, id, System.currentTimeMillis(), mods, e.getEventX(), e.getEventY(), 0, false, MouseEvent.NOBUTTON);
    }

    private void key(EscherPlatformWindow w, Input e, boolean pressed) {
        if (w == null || w.isBlocked()) {
            return;
        }
        CacioComponent source = w.getCacioComponent();
        int xKeyCode = e.detail();
        int xMods = e.getState();
        Display display = e.display;
        int keyCode = KeyboardMapping.mapToKeyCode(display.getInput(), xKeyCode, xMods);
        char keyChar = KeyboardMapping.mapToKeyChar(display.getInput(), xKeyCode, xMods);
        int mods = KeyboardMapping.mapModifiers(xMods);
        long when = System.currentTimeMillis();
        postKeyEvent(source, pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, when, mods, keyCode);
        if (pressed && keyChar != KeyEvent.CHAR_UNDEFINED) {
            postKeyTypedEvent(source, KeyEvent.KEY_TYPED, when, mods, keyChar);
        }
    }

    private void focusIn(EscherPlatformWindow w) {
        if (w == null || w == focused) {
            return;
        }
        focused = w;
        postFocusEvent(w.getCacioComponent(), FocusEvent.FOCUS_GAINED, false, null);
    }

    private void focusOut(EscherPlatformWindow w) {
        if (w == null || w != focused) {
            return;
        }
        focused = null;
        postFocusEvent(w.getCacioComponent(), FocusEvent.FOCUS_LOST, false, null);
    }
}
