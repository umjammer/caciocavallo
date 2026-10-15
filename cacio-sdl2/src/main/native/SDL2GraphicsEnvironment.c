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

#include "jni.h"

#include "cacio-sdl2.h"
#include "net_java_openjdk_awt_peer_sdl2_SDL2GraphicsEnvironment.h"

JNIEXPORT jboolean JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2GraphicsEnvironment_nativeInit
  (JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused)))
{
    __block int rc = -1;

    CacioSDL2_init();

    CACIO_ON_MAIN({
        rc = SDL_Init(SDL_INIT_VIDEO);
    });

    if (rc != 0) {
        fprintf(stderr, "*** Unable to initialize SDL: %s\n", SDL_GetError());
        return JNI_FALSE;
    }
    /*
     * No atexit(SDL_Quit): the JVM shuts down on the DestroyJavaVM thread, and
     * tearing Cocoa down from there traps. The process is going away anyway.
     */

    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2GraphicsEnvironment_pumpMainThread
  (JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused)),
   jint timeoutMillis)
{
    CacioSDL2_pump(timeoutMillis);
}

JNIEXPORT jboolean JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2GraphicsEnvironment_nativeIsCloseRequested
  (JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused)))
{
    return CacioSDL2_isCloseRequested() ? JNI_TRUE : JNI_FALSE;
}