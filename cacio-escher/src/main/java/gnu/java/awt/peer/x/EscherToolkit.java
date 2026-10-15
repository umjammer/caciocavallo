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
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.PrintJob;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.im.spi.InputMethodDescriptor;
import java.awt.image.ColorModel;
import java.awt.peer.DesktopPeer;
import java.awt.peer.FontPeer;
import java.awt.peer.FramePeer;
import java.awt.peer.MouseInfoPeer;
import java.awt.peer.RobotPeer;
import java.awt.peer.SystemTrayPeer;
import java.awt.peer.TrayIconPeer;
import java.util.Map;
import java.util.Properties;

import com.github.caciocavallosilano.cacio.peer.CacioEventSource;
import com.github.caciocavallosilano.cacio.peer.CacioToolkit;
import com.github.caciocavallosilano.cacio.peer.PlatformWindowFactory;
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
        return null;
    }

    @Override
    public RobotPeer createRobot(GraphicsDevice screen) throws AWTException {
        return null;
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
        return false;
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
    public synchronized MouseInfoPeer getMouseInfoPeer() {
        // TODO: Implement properly. Can't use the DefaultMouseInfoPeer...
        throw new UnsupportedOperationException();
    }

    @Override
    public ColorModel getColorModel() throws HeadlessException {
        // TODO: I assume 24 bit depth here, we can do this better.
        return ColorModel.getRGBdefault();
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
    public Clipboard getSystemClipboard() throws HeadlessException {
        return null;
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
