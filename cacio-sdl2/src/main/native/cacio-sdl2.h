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
#ifndef _CACIO_SDL_H
#define	_CACIO_SDL_H

#include <jni.h>
#include "SurfaceData.h"

#include "SDL.h"

#ifdef	__cplusplus
extern "C" {
#endif

typedef struct {

  SurfaceDataOps sdOps;
  SDL_Surface *surface;
  jint x, y, width, height;
	SDL_Window *window;
	SDL_Renderer *renderer;
} SDLSurfaceDataOps;

/*
 * Handed to Java as an opaque jlong. The screen surface carries a window (its
 * contents have to be flushed with SDL_UpdateWindowSurface), while the surfaces
 * backing volatile images carry a NULL window and are never presented directly.
 */
struct SDLWindowAndRenderer {
	SDL_Window *window;
	SDL_Renderer *renderer;
	SDL_Surface *surface;
};

/*
 * Constants javah used to emit into java_awt_event_MouseEvent.h. javah was
 * removed in JDK 10 and javac -h only writes headers for the classes it
 * compiles, so the values are spelled out here instead.
 */
#define java_awt_event_MouseEvent_MOUSE_CLICKED       500
#define java_awt_event_MouseEvent_MOUSE_PRESSED       501
#define java_awt_event_MouseEvent_MOUSE_RELEASED      502
#define java_awt_event_MouseEvent_MOUSE_MOVED         503
#define java_awt_event_MouseEvent_MOUSE_DRAGGED       506
#define java_awt_event_MouseEvent_MOUSE_WHEEL         507
#define java_awt_event_MouseEvent_BUTTON1_DOWN_MASK   1024
#define java_awt_event_MouseEvent_BUTTON2_DOWN_MASK   2048
#define java_awt_event_MouseEvent_BUTTON3_DOWN_MASK   4096

#define java_awt_event_KeyEvent_KEY_TYPED             400
#define java_awt_event_KeyEvent_KEY_PRESSED           401
#define java_awt_event_KeyEvent_KEY_RELEASED          402

#define java_awt_event_KeyEvent_VK_UNDEFINED          0
#define java_awt_event_KeyEvent_CHAR_UNDEFINED        0xFFFF

#define java_awt_event_InputEvent_SHIFT_DOWN_MASK     64
#define java_awt_event_InputEvent_CTRL_DOWN_MASK      128
#define java_awt_event_InputEvent_META_DOWN_MASK      256
#define java_awt_event_InputEvent_ALT_DOWN_MASK       512

/** One AWT-shaped input event, handed from the main thread to the event pump. */
typedef struct {
    int id;         /* an AWT MouseEvent id */
    int x, y;
    int modifiers;  /* AWT extended modifiers, i.e. the button state */
    int button;     /* only meaningful for MOUSE_WHEEL */
    int clickCount;
    int popup;      /* popup trigger */
    int keyCode;    /* an AWT VK_ constant, for key events */
    int keyChar;    /* the typed character, for KEY_TYPED */
} CacioEvent;

/**
 * Set up the batching layer. Called once, before any of the below.
 */
void CacioSDL2_init(void);

/**
 * Add a rectangle to the region waiting to be presented. Called from whichever
 * thread is painting, typically the EDT.
 */
void CacioSDL2_addDamage(SDL_Window *window, int x, int y, int w, int h);

/**
 * Present whatever has been damaged since the last call. Main thread only.
 */
void CacioSDL2_flushDamage(void);

/**
 * Wait up to {@code timeoutMillis} for input, move everything SDL has queued
 * into the event queue below, collapsing mouse motion, and present whatever
 * has been damaged. This is the whole of the main thread's job once the
 * backend is up. Main thread only.
 *
 * The waiting has to happen inside SDL. SDL reads input with
 * [NSApp nextEventMatchingMask:], so anything else that runs the main run loop
 * -- CFRunLoopRunInMode, say -- takes the window server's events first and
 * SDL then finds nothing to report.
 */
void CacioSDL2_pump(int timeoutMillis);

/**
 * Take the next input event, waiting up to {@code timeoutMillis} for one.
 *
 * @return 1 if an event was written to {@code out}, 0 if none arrived in time
 */
int CacioSDL2_nextEvent(CacioEvent *out, int timeoutMillis);

/** @return 1 once the user has asked to close the SDL window */
int CacioSDL2_isCloseRequested(void);

/*
 * Every SDL video call has to happen on the process main thread on macOS --
 * Cocoa_PumpEvents, window creation and framebuffer updates all touch AppKit.
 * Cacio calls into the backend from the EDT and from its event-pump thread
 * instead, so those calls are funnelled onto the main thread, which the
 * application keeps servicing through SDL2GraphicsEnvironment.pumpMainThread().
 * Everywhere else the block simply runs in place.
 */
#ifdef __APPLE__
#include <dispatch/dispatch.h>
#include <pthread.h>
#define CACIO_ON_MAIN(body) \
    do { \
        if (pthread_main_np()) { body } \
        else { dispatch_sync(dispatch_get_main_queue(), ^{ body }); } \
    } while (0)
#else
#define CACIO_ON_MAIN(body) do { body } while (0)
#endif

/**
 * Brings the application to the front. SDL_RaiseWindow only orders the window
 * within its own application, so without this the window opens behind whatever
 * the user was last looking at. Main thread only; a no-op off macOS.
 */
void CacioSDL2_activateApp(void);

/* exported by libjava */
extern void JNU_ThrowByName(JNIEnv *env, const char *name, const char *msg);

#ifdef	__cplusplus
}
#endif

#endif	/* _CACIO_SDL_H */

