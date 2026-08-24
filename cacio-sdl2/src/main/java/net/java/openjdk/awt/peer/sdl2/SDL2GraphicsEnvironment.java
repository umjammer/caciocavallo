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

import java.awt.GraphicsDevice;

import sun.java2d.SunGraphicsEnvironment;


/**
 * Graphics Environment implementation based on libSDL.
 *
 * @author Mario Torre <neugens.liamsoftware@gmail.com>
 */
public class SDL2GraphicsEnvironment extends SunGraphicsEnvironment {

    static {
        System.loadLibrary("cacio-sdl2");
        boolean initialised = nativeInit();
        if (!initialised) {
            throw new ExceptionInInitializerError("Cannot initialise libSDL!");
        }
    }

    @Override
    protected int getNumScreens() {

        return 1;
    }

    @Override
    protected GraphicsDevice makeScreenDevice(int screennum) {

        return new SDL2GraphicsDevice();
    }

    @Override
    public boolean isDisplayLocal() {

        return true;
    }

    /**
     * Services the platform run loop for up to {@code timeoutMillis}.
     *
     * On macOS every SDL video call has to run on the process main thread, so
     * the backend hands those calls over to it; an application using this
     * backend has to be started with {@code -XstartOnFirstThread}, run its UI
     * on another thread and call this from its main thread in a loop. On the
     * other platforms it does nothing.
     */
    public static native void pumpMainThread(int timeoutMillis);

    /**
     * Returns whether the user has asked to close the SDL window, by its close
     * button or by quitting the application.
     */
    public static boolean isCloseRequested() {
        return nativeIsCloseRequested();
    }

    private static native boolean nativeIsCloseRequested();

    private static native boolean nativeInit();
}
