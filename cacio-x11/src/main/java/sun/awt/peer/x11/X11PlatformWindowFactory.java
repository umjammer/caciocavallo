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

package sun.awt.peer.x11;

import java.awt.Component;
import com.github.caciocavallosilano.cacio.peer.CacioComponent;
import com.github.caciocavallosilano.cacio.peer.CacioEventPump;
import com.github.caciocavallosilano.cacio.peer.PlatformToplevelWindow;
import com.github.caciocavallosilano.cacio.peer.PlatformWindow;
import com.github.caciocavallosilano.cacio.peer.PlatformWindowFactory;

public class X11PlatformWindowFactory implements PlatformWindowFactory {

    private X11EventPump eventPump;

    @Override
    public PlatformWindow createPlatformWindow(CacioComponent component, PlatformWindow parent) {
        Component awtC = component.getAWTComponent();
        return new X11PlatformWindow(getEventPump(), component, (X11PlatformWindow) parent,
                                     awtC.getX(), awtC.getY(), awtC.getWidth(),
                                     awtC.getHeight());
    }

    @Override
    public PlatformToplevelWindow createPlatformToplevelWindow(CacioComponent component) {
        Component awtC = component.getAWTComponent();
        return new X11PlatformWindow(getEventPump(), component, awtC.getX(), awtC.getY(), awtC.getWidth(),
                                     awtC.getHeight());
    }

    @Override
    public CacioEventPump<?> createEventPump() {
        return getEventPump();
    }

    private X11EventPump getEventPump() {
        if (eventPump == null) {
            eventPump = new X11EventPump();
        }
        return eventPump;
    }

    @Override
    public PlatformWindow createPlatformToplevelWindow(CacioComponent component,
            PlatformWindow owner) {
        Component awtC = component.getAWTComponent();
        return new X11PlatformWindow(getEventPump(), component, (X11PlatformWindow) owner,
                                     awtC.getX(), awtC.getY(), awtC.getWidth(),
                                     awtC.getHeight());
    }
}
