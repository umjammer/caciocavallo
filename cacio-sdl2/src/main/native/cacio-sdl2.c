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

/*
 * The bridge between the threads cacio calls the backend from and the main
 * thread SDL insists on.
 *
 * Java2D locks and unlocks the screen surface many times over for a single
 * Swing repaint, and cacio's event pump asks for one event at a time. Doing an
 * SDL call per Java call means a main-thread round trip apiece -- far too slow
 * to draw with, and far too slow to keep up with a moving mouse. So both
 * directions are batched here instead: damage is accumulated and presented
 * once per main-thread tick, and the whole SDL event queue is drained in one
 * go into a queue the event pump blocks on.
 */

#include "cacio-sdl2.h"

#ifdef __APPLE__
#include <objc/message.h>
#include <objc/runtime.h>
#endif

static SDL_bool initialized;

/* ---------------------------------------------------------------- damage */

static SDL_mutex *damageLock;
static SDL_Window *damageWindow;
static SDL_Rect damageRect;
static SDL_bool damaged;

/* ---------------------------------------------------------------- events */

/*
 * Deliberately small: mouse motion is collapsed as it comes in, so a backlog
 * only builds up from clicks and wheel notches, and dropping the oldest of
 * those is better than letting the queue grow without bound.
 */
#define EVENT_QUEUE_SIZE 256

static SDL_mutex *eventLock;
static SDL_cond *eventArrived;
static CacioEvent eventQueue[EVENT_QUEUE_SIZE];
static int eventHead;
static int eventCount;

/* touched by the main thread only, while draining */
static int buttonState;
static int lastX, lastY;
static Uint8 lastPressButton;
static int lastPressX, lastPressY;

static SDL_bool closeRequested;

/** AWT Cursor constants, as passed down from ProxyWindow. */
#define java_awt_Cursor_DEFAULT_CURSOR      0
#define java_awt_Cursor_CROSSHAIR_CURSOR    1
#define java_awt_Cursor_TEXT_CURSOR         2
#define java_awt_Cursor_WAIT_CURSOR         3
#define java_awt_Cursor_SW_RESIZE_CURSOR    4
#define java_awt_Cursor_SE_RESIZE_CURSOR    5
#define java_awt_Cursor_NW_RESIZE_CURSOR    6
#define java_awt_Cursor_NE_RESIZE_CURSOR    7
#define java_awt_Cursor_N_RESIZE_CURSOR     8
#define java_awt_Cursor_S_RESIZE_CURSOR     9
#define java_awt_Cursor_W_RESIZE_CURSOR    10
#define java_awt_Cursor_E_RESIZE_CURSOR    11
#define java_awt_Cursor_HAND_CURSOR        12
#define java_awt_Cursor_MOVE_CURSOR        13

static SDL_Cursor *cursors[java_awt_Cursor_MOVE_CURSOR + 1];
static int shownCursor = -1;

/** @return the SDL system cursor closest to the given AWT cursor type */
static SDL_SystemCursor systemCursorFor(int awtCursorType)
{
    switch (awtCursorType) {
    case java_awt_Cursor_CROSSHAIR_CURSOR: return SDL_SYSTEM_CURSOR_CROSSHAIR;
    case java_awt_Cursor_TEXT_CURSOR:      return SDL_SYSTEM_CURSOR_IBEAM;
    case java_awt_Cursor_WAIT_CURSOR:      return SDL_SYSTEM_CURSOR_WAIT;
    case java_awt_Cursor_HAND_CURSOR:      return SDL_SYSTEM_CURSOR_HAND;
    case java_awt_Cursor_MOVE_CURSOR:      return SDL_SYSTEM_CURSOR_SIZEALL;
    /* SDL only has the two diagonals and the two axes, not eight directions */
    case java_awt_Cursor_NW_RESIZE_CURSOR:
    case java_awt_Cursor_SE_RESIZE_CURSOR: return SDL_SYSTEM_CURSOR_SIZENWSE;
    case java_awt_Cursor_NE_RESIZE_CURSOR:
    case java_awt_Cursor_SW_RESIZE_CURSOR: return SDL_SYSTEM_CURSOR_SIZENESW;
    case java_awt_Cursor_W_RESIZE_CURSOR:
    case java_awt_Cursor_E_RESIZE_CURSOR:  return SDL_SYSTEM_CURSOR_SIZEWE;
    case java_awt_Cursor_N_RESIZE_CURSOR:
    case java_awt_Cursor_S_RESIZE_CURSOR:  return SDL_SYSTEM_CURSOR_SIZENS;
    default:                               return SDL_SYSTEM_CURSOR_ARROW;
    }
}

void CacioSDL2_setCursor(int awtCursorType)
{
    if (awtCursorType < 0
        || awtCursorType > java_awt_Cursor_MOVE_CURSOR
        || awtCursorType == shownCursor) {
        return;
    }

    if (cursors[awtCursorType] == NULL) {
        cursors[awtCursorType] =
            SDL_CreateSystemCursor(systemCursorFor(awtCursorType));
        if (cursors[awtCursorType] == NULL) {
            return;
        }
    }

    shownCursor = awtCursorType;
    SDL_SetCursor(cursors[awtCursorType]);
}

void CacioSDL2_activateApp(void)
{
#ifdef __APPLE__
    /*
     * [[NSApplication sharedApplication] activateIgnoringOtherApps:YES],
     * reached through the Objective-C runtime so this stays a C file.
     */
    Class cls = objc_getClass("NSApplication");
    id app;

    if (cls == NULL) {
        return;
    }
    app = ((id (*)(Class, SEL)) objc_msgSend)(cls,
              sel_registerName("sharedApplication"));
    if (app != NULL) {
        ((void (*)(id, SEL, BOOL)) objc_msgSend)(app,
            sel_registerName("activateIgnoringOtherApps:"), YES);
    }
#endif
}

void CacioSDL2_init(void)
{
    if (initialized) {
        return;
    }
    damageLock = SDL_CreateMutex();
    eventLock = SDL_CreateMutex();
    eventArrived = SDL_CreateCond();
    initialized = SDL_TRUE;
}

void CacioSDL2_addDamage(SDL_Window *window, int x, int y, int w, int h)
{
    if (!initialized || window == NULL || w <= 0 || h <= 0) {
        return;
    }

    SDL_LockMutex(damageLock);
    if (damaged && damageWindow == window) {
        int x2 = damageRect.x + damageRect.w;
        int y2 = damageRect.y + damageRect.h;
        if (x < damageRect.x) {
            damageRect.x = x;
        }
        if (y < damageRect.y) {
            damageRect.y = y;
        }
        if (x + w > x2) {
            x2 = x + w;
        }
        if (y + h > y2) {
            y2 = y + h;
        }
        damageRect.w = x2 - damageRect.x;
        damageRect.h = y2 - damageRect.y;
    } else {
        damageWindow = window;
        damageRect.x = x;
        damageRect.y = y;
        damageRect.w = w;
        damageRect.h = h;
        damaged = SDL_TRUE;
    }
    SDL_UnlockMutex(damageLock);
}

void CacioSDL2_flushDamage(void)
{
    SDL_Window *window;
    SDL_Rect rect;

    if (!initialized) {
        return;
    }

    SDL_LockMutex(damageLock);
    if (!damaged) {
        SDL_UnlockMutex(damageLock);
        return;
    }
    window = damageWindow;
    rect = damageRect;
    damaged = SDL_FALSE;
    SDL_UnlockMutex(damageLock);

    /* outside the lock: presenting must not block whoever paints next */
    SDL_UpdateWindowSurfaceRects(window, &rect, 1);
}

/** Appends one event, dropping the oldest if the queue is full. */
static void enqueue(const CacioEvent *event)
{
    if (eventCount == EVENT_QUEUE_SIZE) {
        eventHead = (eventHead + 1) % EVENT_QUEUE_SIZE;
        eventCount--;
    }
    eventQueue[(eventHead + eventCount) % EVENT_QUEUE_SIZE] = *event;
    eventCount++;
}

static int buttonMask(Uint8 button)
{
    switch (button) {
    case SDL_BUTTON_LEFT:
        return java_awt_event_MouseEvent_BUTTON1_DOWN_MASK;
    case SDL_BUTTON_MIDDLE:
        return java_awt_event_MouseEvent_BUTTON2_DOWN_MASK;
    case SDL_BUTTON_RIGHT:
        return java_awt_event_MouseEvent_BUTTON3_DOWN_MASK;
    default:
        return 0;
    }
}

/** The AWT extended modifiers for the current keyboard and mouse state. */
static int modifiers(void)
{
    SDL_Keymod mod = SDL_GetModState();
    int result = buttonState;

    if (mod & KMOD_SHIFT) {
        result |= java_awt_event_InputEvent_SHIFT_DOWN_MASK;
    }
    if (mod & KMOD_CTRL) {
        result |= java_awt_event_InputEvent_CTRL_DOWN_MASK;
    }
    if (mod & KMOD_ALT) {
        result |= java_awt_event_InputEvent_ALT_DOWN_MASK;
    }
    if (mod & KMOD_GUI) {
        result |= java_awt_event_InputEvent_META_DOWN_MASK;
    }
    return result;
}

/**
 * Maps an SDL keycode to the AWT VK_ constant with the same meaning.
 *
 * @return the VK_ constant, or VK_UNDEFINED for a key AWT has no name for
 */
static int keyCodeOf(SDL_Keycode sym)
{
    /* the ranges AWT and SDL happen to agree on, bar the case of letters */
    if (sym >= SDLK_a && sym <= SDLK_z) {
        return sym - SDLK_a + 'A';
    }
    if (sym >= SDLK_0 && sym <= SDLK_9) {
        return sym;
    }
    if (sym >= SDLK_F1 && sym <= SDLK_F12) {
        return sym - SDLK_F1 + 112;   /* VK_F1 */
    }

    switch (sym) {
    case SDLK_BACKSPACE:    return 8;
    case SDLK_TAB:          return 9;
    case SDLK_CLEAR:        return 12;
    case SDLK_RETURN:
    case SDLK_KP_ENTER:     return 10;
    case SDLK_PAUSE:        return 19;
    case SDLK_CAPSLOCK:     return 20;
    case SDLK_ESCAPE:       return 27;
    case SDLK_SPACE:        return 32;
    case SDLK_PAGEUP:       return 33;
    case SDLK_PAGEDOWN:     return 34;
    case SDLK_END:          return 35;
    case SDLK_HOME:         return 36;
    case SDLK_LEFT:         return 37;
    case SDLK_UP:           return 38;
    case SDLK_RIGHT:        return 39;
    case SDLK_DOWN:         return 40;
    case SDLK_COMMA:        return 44;
    case SDLK_MINUS:        return 45;
    case SDLK_PERIOD:       return 46;
    case SDLK_SLASH:        return 47;
    case SDLK_SEMICOLON:    return 59;
    case SDLK_EQUALS:       return 61;
    case SDLK_LEFTBRACKET:  return 91;
    case SDLK_BACKSLASH:    return 92;
    case SDLK_RIGHTBRACKET: return 93;
    case SDLK_DELETE:       return 127;
    case SDLK_INSERT:       return 155;
    case SDLK_LSHIFT:
    case SDLK_RSHIFT:       return 16;
    case SDLK_LCTRL:
    case SDLK_RCTRL:        return 17;
    case SDLK_LALT:
    case SDLK_RALT:         return 18;
    case SDLK_LGUI:
    case SDLK_RGUI:         return 157;  /* VK_META */
    case SDLK_BACKQUOTE:    return 192;
    case SDLK_QUOTE:        return 222;
    default:                return java_awt_event_KeyEvent_VK_UNDEFINED;
    }
}

/**
 * Reads one UTF-8 code point.
 *
 * @param text the position to read from, advanced past the code point
 * @return the code point, or 0 at the end of the string
 */
static int nextCodePoint(const char **text)
{
    const unsigned char *p = (const unsigned char *) *text;
    int cp;
    int extra;
    int i;

    if (*p == 0) {
        return 0;
    }
    if (*p < 0x80) {
        cp = *p;
        extra = 0;
    } else if ((*p & 0xE0) == 0xC0) {
        cp = *p & 0x1F;
        extra = 1;
    } else if ((*p & 0xF0) == 0xE0) {
        cp = *p & 0x0F;
        extra = 2;
    } else {
        cp = *p & 0x07;
        extra = 3;
    }
    p++;
    for (i = 0; i < extra; i++) {
        if ((*p & 0xC0) != 0x80) {
            break;
        }
        cp = (cp << 6) | (*p & 0x3F);
        p++;
    }
    *text = (const char *) p;
    return cp;
}

/** Appends one event under the lock, collapsing a run of pointer motion. */
static void publish(const CacioEvent *event)
{
    SDL_LockMutex(eventLock);
    if ((event->id == java_awt_event_MouseEvent_MOUSE_MOVED
         || event->id == java_awt_event_MouseEvent_MOUSE_DRAGGED)
        && eventCount > 0) {
        /*
         * Motion arrives faster than the event pump can consume it and only
         * the newest position matters, so a pending move is replaced rather
         * than letting the queue -- and the pointer -- fall behind.
         */
        CacioEvent *last = &eventQueue[(eventHead + eventCount - 1)
                                       % EVENT_QUEUE_SIZE];
        if (last->id == event->id) {
            *last = *event;
            SDL_UnlockMutex(eventLock);
            return;
        }
    }
    enqueue(event);
    SDL_UnlockMutex(eventLock);
}

/** Turns one SDL event into zero or more AWT-shaped ones. */
static void translate(const SDL_Event *event)
{
    CacioEvent out;

    SDL_memset(&out, 0, sizeof(out));
    out.keyChar = java_awt_event_KeyEvent_CHAR_UNDEFINED;

    switch (event->type) {
    case SDL_QUIT:
        closeRequested = SDL_TRUE;
        return;

    case SDL_WINDOWEVENT:
        if (event->window.event == SDL_WINDOWEVENT_CLOSE) {
            closeRequested = SDL_TRUE;
        }
        return;

    case SDL_MOUSEMOTION:
        lastX = event->motion.x;
        lastY = event->motion.y;
        out.id = buttonState != 0
            ? java_awt_event_MouseEvent_MOUSE_DRAGGED
            : java_awt_event_MouseEvent_MOUSE_MOVED;
        out.x = lastX;
        out.y = lastY;
        out.modifiers = modifiers();
        break;

    case SDL_MOUSEBUTTONDOWN:
        buttonState |= buttonMask(event->button.button);
        lastX = lastPressX = event->button.x;
        lastY = lastPressY = event->button.y;
        lastPressButton = event->button.button;
        out.id = java_awt_event_MouseEvent_MOUSE_PRESSED;
        out.x = lastX;
        out.y = lastY;
        out.modifiers = modifiers();
        out.clickCount = event->button.clicks;
        out.popup = event->button.button == SDL_BUTTON_RIGHT;
        break;

    case SDL_MOUSEBUTTONUP:
        buttonState &= ~buttonMask(event->button.button);
        lastX = event->button.x;
        lastY = event->button.y;
        out.id = java_awt_event_MouseEvent_MOUSE_RELEASED;
        out.x = lastX;
        out.y = lastY;
        out.modifiers = modifiers();
        out.clickCount = event->button.clicks;
        publish(&out);
        /*
         * cacio-shared leaves MOUSE_CLICKED to the backend (cacio-tta has its
         * own MouseClickSupport). A release over the spot the press landed on
         * is a click; SDL has already counted it for us.
         */
        if (event->button.button == lastPressButton
            && event->button.x == lastPressX
            && event->button.y == lastPressY) {
            out.id = java_awt_event_MouseEvent_MOUSE_CLICKED;
            publish(&out);
        }
        return;

    case SDL_MOUSEWHEEL:
        /* wheel events carry no position, so the last one seen is used */
        out.id = java_awt_event_MouseEvent_MOUSE_WHEEL;
        out.x = lastX;
        out.y = lastY;
        out.modifiers = modifiers();
        /* cacio reads button 4 as a scroll up and anything else as down */
        out.button = event->wheel.y > 0 ? 4 : 5;
        break;

    case SDL_KEYDOWN:
    case SDL_KEYUP:
        out.id = event->type == SDL_KEYDOWN
            ? java_awt_event_KeyEvent_KEY_PRESSED
            : java_awt_event_KeyEvent_KEY_RELEASED;
        out.keyCode = keyCodeOf(event->key.keysym.sym);
        out.modifiers = modifiers();
        break;

    case SDL_TEXTINPUT: {
        /*
         * KEY_TYPED comes from text input rather than from the key events, so
         * that dead keys, the input method and the keyboard layout all get
         * their say before the character reaches Swing.
         */
        const char *text = event->text.text;
        int cp;
        while ((cp = nextCodePoint(&text)) != 0) {
            if (cp > 0xFFFF) {
                continue;   /* cacio's EventData carries a single char */
            }
            out.id = java_awt_event_KeyEvent_KEY_TYPED;
            out.keyCode = java_awt_event_KeyEvent_VK_UNDEFINED;
            out.keyChar = cp;
            out.modifiers = modifiers();
            publish(&out);
        }
        return;
    }

    default:
        return;
    }

    publish(&out);
}

void CacioSDL2_pump(int timeoutMillis)
{
    SDL_Event event;
    SDL_bool any = SDL_FALSE;

    if (!initialized) {
        return;
    }

    /* present what was drawn since the last turn before parking */
    CacioSDL2_flushDamage();

    /*
     * SDL does the waiting, and with it the pumping of the platform's event
     * queue. Waiting anywhere else -- in a CFRunLoop, or on a plain sleep --
     * means SDL never gets to read the input.
     */
    if (SDL_WaitEventTimeout(&event, timeoutMillis) == 1) {
        translate(&event);
        any = SDL_TRUE;
        while (SDL_PollEvent(&event) == 1) {
            translate(&event);
        }
    }

    if (any) {
        SDL_CondSignal(eventArrived);
    }

    CacioSDL2_flushDamage();
}

int CacioSDL2_nextEvent(CacioEvent *out, int timeoutMillis)
{
    int found = 0;

    if (!initialized) {
        return 0;
    }

    SDL_LockMutex(eventLock);
    if (eventCount == 0) {
        SDL_CondWaitTimeout(eventArrived, eventLock, timeoutMillis);
    }
    if (eventCount > 0) {
        *out = eventQueue[eventHead];
        eventHead = (eventHead + 1) % EVENT_QUEUE_SIZE;
        eventCount--;
        found = 1;
    }
    SDL_UnlockMutex(eventLock);

    return found;
}

int CacioSDL2_isCloseRequested(void)
{
    return closeRequested ? 1 : 0;
}
