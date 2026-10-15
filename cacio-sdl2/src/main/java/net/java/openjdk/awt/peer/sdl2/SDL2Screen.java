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

package net.java.openjdk.awt.peer.sdl2;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.ColorModel;
import java.util.List;

import com.github.caciocavallosilano.cacio.peer.CacioEventSource;
import com.github.caciocavallosilano.cacio.peer.WindowClippedGraphics;
import com.github.caciocavallosilano.cacio.peer.managed.EventData;
import com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory;
import com.github.caciocavallosilano.cacio.peer.managed.PlatformScreen;
import sun.java2d.SunGraphics2D;


/**
 *
 * @author Mario Torre <neugens@limasoftware.net>
 */
class SDL2Screen implements PlatformScreen, CacioEventSource {

    private static final int width;
    private static final int height;

    static {
        initIDs();
        
        Dimension dim = FullScreenWindowFactory.getScreenDimension();
        width = dim.width;
        height = dim.height;
    }

    private final long nativeSDLdata;
    private EventData eventData;
    private SDL2SurfaceData surfaceData;

    SDL2Screen() {
        // the screen starts out showing cacio's desktop colour, which is also
        // what it clears to behind a window that moved away or was hidden
        nativeSDLdata = nativeInitScreen(width, height,
                FullScreenWindowFactory.getScreenBackground().getRGB());
    }

    @Override
    public Graphics2D getClippedGraphics(Color fg, Color bg, Font font,
                                         List<Rectangle> clipRects) {

        SDL2SurfaceData data = getSurfaceData();
        Graphics2D g2d = new SunGraphics2D(data, fg, bg, font);
        if (clipRects != null && !clipRects.isEmpty()) {
            Area a = new Area(getBounds());
            for (Rectangle clip : clipRects) {
                a.subtract(new Area(clip));
            }
            g2d = new WindowClippedGraphics(g2d, a);
        }
        return g2d;
    }

    @Override
    public ColorModel getColorModel() {

        return getGraphicsConfiguration().getColorModel();
    }

    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {

        return SDL2GraphicsConfiguration.getDefaultConfiguration();
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(0, 0, width, height);
    }

    @Override
    public EventData getNextEvent() {

        if (eventData == null) {
            eventData = new EventData();
        }

        eventData.clear();

        // blocks for a short while when there is nothing to report, so there
        // is no need -- and no room, with a mouse to keep up with -- to sleep
        nativeGetEvent(eventData);

        eventData.setTime(System.currentTimeMillis());
        eventData.setSource(this);
        return eventData;
    }

    private SDL2SurfaceData getSurfaceData() {

        if (surfaceData == null) {
            surfaceData = new SDL2SurfaceData(SDL2SurfaceData.typeDefault,
                                             getColorModel(), getBounds(),
                                             getGraphicsConfiguration(), this,
                                             nativeSDLdata);
        }
        return surfaceData;
    }

    @Override
    public void setCursor(Cursor cursor) {
        nativeSetCursor(cursor.getType());
    }

    private native void nativeSetCursor(int cursorType);
    private native void nativeGetEvent(EventData eventData);
    private native long nativeInitScreen(int width, int height, int background);
    private native static void initIDs();
}
