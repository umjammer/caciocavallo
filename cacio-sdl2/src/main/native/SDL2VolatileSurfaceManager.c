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
#include <jni.h>

#include "SDL.h"
#include "cacio-sdl2.h"

#include "net_java_openjdk_awt_peer_sdl2_SDL2VolatileSurfaceManager.h"

JNIEXPORT jlong JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2VolatileSurfaceManager_initSurface
  (JNIEnv *env, jobject thiz __attribute__((unused)), jint width, jint height)
{
    SDL_Surface *surface = NULL;
    struct SDLWindowAndRenderer *data = NULL;

    /*
     * FIXME: sync these with the ColorModel
     * returned by SDLGraphicsEnvironment
     */
    Uint32 rmask = 0x00FF0000;
    Uint32 gmask = 0x0000FF00;
    Uint32 bmask = 0x000000FF;
    Uint32 amask = 0;

    /* TODO: pass the depth we really want for the image. */
    surface = SDL_CreateRGBSurface(/* SDL_HWSURFACE | SDL_SRCCOLORKEY |  SDL_DOUBLEBUF */ 0,
                                   width, height, 32,
                                   rmask, gmask, bmask, amask);
    if(surface == NULL) {
        fprintf(stderr, "CreateRGBSurface failed: %s\n", SDL_GetError());
        JNU_ThrowByName(env, "java/lang/InternalError",
                        "SDL2VolatileSurfaceManager::initSurface failed");
        return 0L;
    }

    /*
     * SDL2SurfaceData.initOps() reads a struct SDLWindowAndRenderer out of the
     * jlong, so an off-screen surface has to be wrapped in one too. A NULL
     * window marks it as never presented on its own.
     */
    data = calloc(1, sizeof(*data));
    if (data == NULL) {
        SDL_FreeSurface(surface);
        JNU_ThrowByName(env, "java/lang/OutOfMemoryError",
                        "SDL2VolatileSurfaceManager::initSurface");
        return 0L;
    }
    data->surface = surface;

    return (jlong) data;
}
