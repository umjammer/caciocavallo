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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Point;
import java.awt.PrintJob;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.im.spi.InputMethodDescriptor;
import java.awt.image.ColorModel;
import java.awt.peer.DesktopPeer;
import java.awt.peer.FontPeer;
import java.awt.peer.FramePeer;
import java.awt.peer.RobotPeer;
import java.awt.peer.SystemTrayPeer;
import java.awt.peer.TrayIconPeer;
import java.util.Map;
import java.util.Properties;

import com.github.caciocavallosilano.cacio.peer.CacioToolkit;
import com.github.caciocavallosilano.cacio.peer.PlatformWindowFactory;
import gnu.x11.Display;
import sun.awt.LightweightFrame;
import sun.awt.datatransfer.DataTransferer;


public class EscherToolkit extends CacioToolkit {

    private PlatformWindowFactory platformWindowFactory;

    @Override
    public synchronized PlatformWindowFactory getPlatformWindowFactory() {
        if (platformWindowFactory == null) {
            platformWindowFactory = new EscherPlatformWindowFactory();
        }
        return platformWindowFactory;
    }

    @Override
    public DragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge)
            throws InvalidDnDOperationException {
        return new EscherDragSourceContextPeer(dge);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DragGestureRecognizer> T createDragGestureRecognizer(
            Class<T> recognizerClass, DragSource ds, Component c, int actions, DragGestureListener dgl) {
        if (MouseDragGestureRecognizer.class.equals(recognizerClass)) {
            return (T) new EscherDragGestureRecognizer(ds, c, actions, dgl);
        }
        return null;
    }

    @Override
    public DataTransferer getDataTransferer() {
        return EscherDataTransferer.instance();
    }

    @Override
    public RobotPeer createRobot(GraphicsDevice screen) throws AWTException {
        return new EscherRobotPeer((XGraphicsDevice) screen);
    }

    @Override
    public Clipboard getSystemClipboard() throws HeadlessException {
        return getDefaultDevice().getClipboard();
    }

    @Override
    public Clipboard getSystemSelection() throws HeadlessException {
        return getDefaultDevice().getPrimarySelection();
    }

    @Override
    public Cursor createCustomCursor(Image cursor, Point hotSpot, String name)
            throws IndexOutOfBoundsException, HeadlessException {
        return new EscherCustomCursor(cursor, hotSpot, name);
    }

    @Override
    public Dimension getBestCursorSize(int preferredWidth, int preferredHeight) throws HeadlessException {
        return new Dimension(preferredWidth > 0 ? preferredWidth : 32, preferredHeight > 0 ? preferredHeight : 32);
    }

    @Override
    public int getMaximumCursorColors() throws HeadlessException {
        return 2;
    }

    @Override
    public SystemTrayPeer createSystemTray(SystemTray target) {
        return null;
    }

    @Override
    public TrayIconPeer createTrayIcon(TrayIcon target) throws HeadlessException, AWTException {
        return null;
    }

    @Override
    public void grab(Window w) {
    }

    @Override
    public boolean isDesktopSupported() {
        return false;
    }

    @Override
    public boolean isTaskbarSupported() {
        return false;
    }

    @Override
    public boolean isTraySupported() {
        return false;
    }

    @Override
    protected boolean syncNativeQueue(long timeout) {
        Display display = getDefaultDevice().getDisplay();
        // when this comes back, the server has handled everything sent so far
        // and what that caused is on its way here
        display.getInput().inputFocus();
        boolean events = display.eventAvailable();
        long end = System.currentTimeMillis() + timeout;
        while (display.eventAvailable() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return events;
    }

    @Override
    public void ungrab(Window w) {
    }

    @Override
    public void beep() {
    }

    @Override
    public DesktopPeer createDesktopPeer(Desktop target) throws HeadlessException {
        return null;
    }

    @Override
    public ColorModel getColorModel() throws HeadlessException {
        return getDefaultDevice().getDefaultConfiguration().getColorModel();
    }

    @Override
    public PrintJob getPrintJob(Frame frame, String jobtitle, Properties props) {
        return null;
    }

    @Override
    public int getScreenResolution() throws HeadlessException {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        XGraphicsConfiguration xgc = (XGraphicsConfiguration) gc;

        return xgc.getResolution();
    }

    @Override
    public Map<TextAttribute, ?> mapInputMethodHighlight(InputMethodHighlight highlight)
            throws HeadlessException {
        return null;
    }

    @Override
    public void sync() {
    }

    @Override
    public InputMethodDescriptor getInputMethodAdapterDescriptor() throws AWTException {
        return null;
    }

    public static XGraphicsDevice getDefaultDevice() {
        XGraphicsEnvironment env = (XGraphicsEnvironment) XGraphicsEnvironment.getLocalGraphicsEnvironment();
        return (XGraphicsDevice) env.getDefaultScreenDevice();
    }

    @Override
    public FramePeer createLightweightFrame(LightweightFrame target) throws HeadlessException {
        return null;
    }
}
