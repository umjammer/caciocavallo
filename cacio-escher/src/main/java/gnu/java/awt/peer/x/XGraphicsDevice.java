/* XGraphicsDevice.java
   Copyright (C) 2008 Mario Torre and Roman Kennke

This file is part of the Caciocavallo project.

Caciocavallo is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

Caciocavallo is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with Caciocavallo; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package gnu.java.awt.peer.x;

import java.awt.AWTError;
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import gnu.x11.Cursor;
import gnu.x11.Display;
import gnu.x11.EscherServerConnectionException;
import gnu.x11.UnixDomainSocket;


/**
 * This class represents an X Display. The actual connection is established
 * lazily when it is first needed.
 *
 * @author Roman Kennke (kennke@aicas.com)
 */
public class XGraphicsDevice extends GraphicsDevice {

    private static final Logger logger = System.getLogger(XGraphicsDevice.class.getName());

    private XGraphicsConfiguration defaultConfiguration;

    /**
     * The X display associated with the XGraphicsDevice. This is established
     * when {@link #getDisplay} is first called.
     */
    private Display display;

    static class DisplayName {

        String hostname;
        final int displayNumber;
        final int screenNumber;

        public DisplayName(String dn) {
            String[] pair = dn.split(":");
            this.hostname = pair[0];
            this.displayNumber = pair[1].indexOf('.') != -1 ? Integer.valueOf(pair[1].substring(0, pair[1].indexOf('.'))) : Integer.valueOf(pair[1]);
            this.screenNumber = pair[1].indexOf('.') != -1 ? Integer.parseInt(pair[1].substring(pair[1].lastIndexOf('.') + 1)) : -1;
        }

        public String getIDString() {
            return ":" + displayNumber + (screenNumber != -1 ? "." + screenNumber : "");
        }

        @Override
        public String toString() {
            return hostname + getIDString();
        }
    }

    /**
     * The display name from which the display will be initialized.
     */
    private final DisplayName displayName;

    /**
     * The windows on this X Display by their X window id, for the event pump
     * to find the target of an event. It runs on its own thread.
     */
    private final Map<Integer, EscherPlatformWindow> windows = new ConcurrentHashMap<>();

    /** The X cursors by AWT cursor type, they are created when first used. */
    private final Map<Integer, Cursor> cursors = new ConcurrentHashMap<>();

    private EscherClipboard clipboard;
    private EscherClipboard primarySelection;

    private volatile boolean buttonDown;

    /**
     * Creates a new XGraphicsDevice.
     */
    XGraphicsDevice(String dn) {
        displayName = new DisplayName(dn);
    }

    @Override
    public int getType() {
        return TYPE_RASTER_SCREEN;
    }

    @Override
    public String getIDstring() {
        return displayName.getIDString();
    }

    @Override
    public GraphicsConfiguration[] getConfigurations() {
        return new GraphicsConfiguration[] {getDefaultConfiguration()};
    }

    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
        if (defaultConfiguration == null)
            defaultConfiguration = new XGraphicsConfiguration(this);
        return defaultConfiguration;
    }

    /**
     * Returns the X Display associated with this XGraphicsDevice.
     * This establishes the connection to the X server on the first invocation.
     *
     * @return the X Display associated with this XGraphicsDevice
     */
    Display getDisplay() {
        if (display == null) {
            if (displayName.hostname.isEmpty())
                displayName.hostname = "localhost";

            try {
                logger.log(Level.DEBUG, "connecting to : " + displayName);
                // Try to connect via unix domain sockets when host == localhost.
                if ((displayName.hostname.equals("localhost")
                        || displayName.hostname.isEmpty())
                        && System.getProperty("gnu.xawt.no_local_sockets") == null) {
                    Socket socket = createLocalSocket();
                    if (socket != null) {
                        display = new Display(socket, "localhost",
                                displayName.displayNumber,
                                displayName.screenNumber);
                    }
                }

            } catch (EscherServerConnectionException ex) {
                /* do noting here */
            }

            try {
                // The following happens when we are configured to use plain sockets,
                // when the connection is probably remote or when we couldn't load
                // the LocalSocket class stuff.
                if (display == null)
                    display = new Display(displayName.hostname, displayName.displayNumber, displayName.screenNumber);
            } catch (EscherServerConnectionException ex) {
                // time to throw the real exception
                AWTError awtErr = new AWTError("Cannot connect to X Server: "
                        + displayName);
                awtErr.initCause(ex);
                throw awtErr;
            }
        }
        return display;
    }

    void registerWindow(EscherPlatformWindow window) {
        windows.put(window.xwindow.id(), window);
    }

    void unregisterWindow(EscherPlatformWindow window) {
        windows.remove(window.xwindow.id());
    }

    /**
     * @return the X window a component is on, null if it is not showing
     */
    EscherPlatformWindow getWindowOf(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof java.awt.Window) {
                for (EscherPlatformWindow window : windows.values()) {
                    if (window.getCacioComponent().getAWTComponent() == c) {
                        return window;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return the X cursor for an AWT one
     */
    Cursor getXCursor(java.awt.Cursor cursor) {
        if (cursor instanceof EscherCustomCursor custom) {
            return custom.xcursor();
        }
        return cursors.computeIfAbsent(cursor.getType(), type -> new Cursor(getDisplay(), switch (type) {
            case java.awt.Cursor.CROSSHAIR_CURSOR -> Cursor.Shape.CROSSHAIR;
            case java.awt.Cursor.TEXT_CURSOR -> Cursor.Shape.XTERM;
            case java.awt.Cursor.WAIT_CURSOR -> Cursor.Shape.WATCH;
            case java.awt.Cursor.SW_RESIZE_CURSOR -> Cursor.Shape.BOTTOM_LEFT_CORNER;
            case java.awt.Cursor.SE_RESIZE_CURSOR -> Cursor.Shape.BOTTOM_RIGHT_CORNER;
            case java.awt.Cursor.NW_RESIZE_CURSOR -> Cursor.Shape.TOP_LEFT_CORNER;
            case java.awt.Cursor.NE_RESIZE_CURSOR -> Cursor.Shape.TOP_RIGHT_CORNER;
            case java.awt.Cursor.N_RESIZE_CURSOR -> Cursor.Shape.TOP_SIDE;
            case java.awt.Cursor.S_RESIZE_CURSOR -> Cursor.Shape.BOTTOM_SIDE;
            case java.awt.Cursor.W_RESIZE_CURSOR -> Cursor.Shape.LEFT_SIDE;
            case java.awt.Cursor.E_RESIZE_CURSOR -> Cursor.Shape.RIGHT_SIDE;
            case java.awt.Cursor.HAND_CURSOR -> Cursor.Shape.HAND2;
            case java.awt.Cursor.MOVE_CURSOR -> Cursor.Shape.FLEUR;
            default -> Cursor.Shape.LEFT_PTR;
        }));
    }

    /**
     * @return the CLIPBOARD selection as a clipboard
     */
    synchronized EscherClipboard getClipboard() {
        if (clipboard == null) {
            clipboard = new EscherClipboard(getDisplay(), "CLIPBOARD");
        }
        return clipboard;
    }

    /**
     * @return the PRIMARY selection as a clipboard
     */
    synchronized EscherClipboard getPrimarySelection() {
        if (primarySelection == null) {
            primarySelection = new EscherClipboard(getDisplay(), "PRIMARY");
        }
        return primarySelection;
    }

    /**
     * @return the clipboard for a selection, null if it is not one in use
     */
    synchronized EscherClipboard getClipboard(int selection) {
        for (EscherClipboard c : new EscherClipboard[] {clipboard, primarySelection}) {
            if (c != null && c.selectionID() == selection) {
                return c;
            }
        }
        return null;
    }

    void setButtonDown(boolean down) {
        buttonDown = down;
    }

    /**
     * @return whether a mouse button is being held down, according to the events so far
     */
    boolean isButtonDown() {
        return buttonDown;
    }

    /**
     * @return the window for that id, null for a window that is gone already
     */
    EscherPlatformWindow getWindow(int id) {
        return windows.get(id);
    }

    /**
     * Tries to connect to the local X server through its unix domain socket.
     *
     * @return the connected socket, null when there is none to connect to
     */
    private Socket createLocalSocket() {
        try {
            return new UnixDomainSocket("/tmp/.X11-unix/X" + displayName.displayNumber);
        } catch (IOException ex) {
            logger.log(Level.DEBUG, "no local socket for " + displayName.getIDString() + ": " + ex);
            return null;
        }
    }
}
