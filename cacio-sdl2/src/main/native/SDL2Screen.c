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

#include "cacio-sdl2.h"

#include "net_java_openjdk_awt_peer_sdl2_SDL2Screen.h"


static jmethodID eventDataSetIdMID;
static jmethodID eventDataSetModifiersMID;
static jmethodID eventDataSetXMID;
static jmethodID eventDataSetYMID;
static jmethodID eventDataSetButtonMID;
static jmethodID eventDataSetClickCountMID;
static jmethodID eventDataSetPopupMID;
static jmethodID eventDataSetKeyCodeMID;
static jmethodID eventDataSetKeyCharMID;

/** How long the event pump parks waiting for input before looping again. */
#define EVENT_WAIT_MILLIS 50

static jclass sunToolkitCls;
static jmethodID sunToolkitLockMID;
static jmethodID sunToolkitUnlockMID;

JNIEXPORT void JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2Screen_initIDs
  (JNIEnv *env, jclass cls __attribute__((unused)))
{
    CacioSDL2_init();

    jclass eventDataCls = (*env)->FindClass(env,
                                            "com/github/caciocavallosilano/cacio/peer/managed/EventData");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetIdMID = (*env)->GetMethodID(env, eventDataCls,
                                            "setId", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetModifiersMID = (*env)->GetMethodID(env, eventDataCls,
                                                   "setModifiers", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetXMID = (*env)->GetMethodID(env, eventDataCls,
                                           "setX", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetYMID = (*env)->GetMethodID(env, eventDataCls,
                                           "setY", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetButtonMID = (*env)->GetMethodID(env, eventDataCls,
                                                "setButton", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetClickCountMID = (*env)->GetMethodID(env, eventDataCls,
                                                    "setClickCount", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetPopupMID = (*env)->GetMethodID(env, eventDataCls,
                                               "setPopup", "(Z)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetKeyCodeMID = (*env)->GetMethodID(env, eventDataCls,
                                                 "setKeyCode", "(I)V");
    if ((*env)->ExceptionCheck(env)) return;

    eventDataSetKeyCharMID = (*env)->GetMethodID(env, eventDataCls,
                                                 "setKeyChar", "(C)V");
    if ((*env)->ExceptionCheck(env)) return;

    sunToolkitCls = (*env)->FindClass(env, "sun/awt/SunToolkit");
    if ((*env)->ExceptionCheck(env)) return;
    sunToolkitCls = (*env)->NewGlobalRef(env, sunToolkitCls);
    if ((*env)->ExceptionCheck(env)) return;
    sunToolkitLockMID = (*env)->GetStaticMethodID(env, sunToolkitCls,
                                                  "awtLock", "()V");
    if ((*env)->ExceptionCheck(env)) return;
    sunToolkitUnlockMID = (*env)->GetStaticMethodID(env, sunToolkitCls,
                                                    "awtUnlock", "()V");
    if ((*env)->ExceptionCheck(env)) return;
}

JNIEXPORT jlong JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2Screen_nativeInitScreen
  (JNIEnv *env, jobject thiz __attribute__((unused)), jint width, jint height,
   jint background)
{
    (*env)->CallStaticVoidMethod(env, sunToolkitCls, sunToolkitLockMID);

    /*
     * Without this, the click that brings the window to the front is swallowed
     * by the activation instead of reaching the component under the pointer.
     */
    SDL_SetHint(SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, "1");

    __block struct SDLWindowAndRenderer *data = calloc(1, sizeof(*data));
    if (data == NULL) {
        (*env)->CallStaticVoidMethod(env, sunToolkitCls, sunToolkitUnlockMID);
        JNU_ThrowByName(env, "java/lang/OutOfMemoryError",
                "SDL2Screen::nativeInitScreen");
        return 0L;
    }

    CACIO_ON_MAIN({
        data->window = SDL_CreateWindow("Cacio SDL2", SDL_WINDOWPOS_CENTERED,
            SDL_WINDOWPOS_CENTERED, width, height, SDL_WINDOW_SHOWN);
        if (data->window != NULL) {
            data->surface = SDL_GetWindowSurface(data->window);
        }
    });
    if (data->window == NULL || data->surface == NULL) {
        fprintf(stderr, "Unable to set video mode: %s\n", SDL_GetError());
        free(data);
        (*env)->CallStaticVoidMethod(env, sunToolkitCls, sunToolkitUnlockMID);
        JNU_ThrowByName(env, "java/lang/InternalError",
                "SDL2Screen::nativeInitScreen: cannot create SDL_Surface.");
        return 0L;
    }

    /*
     * Start from cacio's desktop colour, which is what it clears to behind a
     * window that moved away -- otherwise only the vacated areas would take
     * that colour and the rest would keep whatever the framebuffer held.
     */
    SDL_FillRect(data->surface, NULL,
                 SDL_MapRGB(data->surface->format,
                            (background >> 16) & 0xFF,
                            (background >> 8) & 0xFF,
                            background & 0xFF));
    SDL_UpdateWindowSurface(data->window);

    /* an application window is expected to come up in front and take focus */
    CacioSDL2_activateApp();
    SDL_RaiseWindow(data->window);

    SDL_StartTextInput();   /* required for SDL_TEXTINPUT, i.e. KEY_TYPED */

    (*env)->CallStaticVoidMethod(env, sunToolkitCls, sunToolkitUnlockMID);

    return (jlong) data;
}

JNIEXPORT void JNICALL Java_net_java_openjdk_awt_peer_sdl2_SDL2Screen_nativeGetEvent
  (JNIEnv *env, jobject thiz __attribute__((unused)), jobject eventData)
{
    CacioEvent event;

    /*
     * Waits on the queue the main thread drains SDL into, rather than polling
     * SDL here: SDL may only be polled from the main thread, and one event per
     * call could never keep up with the rate mouse motion arrives at.
     */
    if (!CacioSDL2_nextEvent(&event, EVENT_WAIT_MILLIS)) {
        return;
    }

    (*env)->CallStaticVoidMethod(env, sunToolkitCls, sunToolkitLockMID);

    (*env)->CallVoidMethod(env, eventData, eventDataSetIdMID, (jint) event.id);
    (*env)->CallVoidMethod(env, eventData, eventDataSetXMID, (jint) event.x);
    (*env)->CallVoidMethod(env, eventData, eventDataSetYMID, (jint) event.y);
    (*env)->CallVoidMethod(env, eventData, eventDataSetModifiersMID,
                           (jint) event.modifiers);
    (*env)->CallVoidMethod(env, eventData, eventDataSetButtonMID,
                           (jint) event.button);
    (*env)->CallVoidMethod(env, eventData, eventDataSetClickCountMID,
                           (jint) event.clickCount);
    (*env)->CallVoidMethod(env, eventData, eventDataSetPopupMID,
                           event.popup ? JNI_TRUE : JNI_FALSE);
    /* EventData.clear() leaves these two alone, so they are always set */
    (*env)->CallVoidMethod(env, eventData, eventDataSetKeyCodeMID,
                           (jint) event.keyCode);
    (*env)->CallVoidMethod(env, eventData, eventDataSetKeyCharMID,
                           (jchar) event.keyChar);

    (*env)->CallStaticVoidMethod(env, sunToolkitCls, sunToolkitUnlockMID);
}
