/* KeyboardMapping.java -- Maps X keysyms to Java keyCode and keyChar
   Copyright (C) 2008 Mario Torre and Roman Kennke

This file is part of the Caciocavallo project.

Caciocavallo is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

Caciocavallo is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with Caciocavallo; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package gnu.java.awt.peer.x;

import java.awt.event.KeyEvent;

import gnu.x11.Input;
import gnu.x11.keysym.Latin1;
import gnu.x11.keysym.Misc;


/**
 * Defines the keyboard mapping from X keysyms to Java
 * keycodes and keychars.
 *
 * @author Roman Kennke (kennke@aicas.com)
 */
final class KeyboardMapping {

    /**
     * Maps X keycodes to AWT keycodes.
     *
     * @param xInput   the X input event
     * @param xKeyCode the X keycode to map
     * @param xMods    the X modifiers
     * @return the AWT keycode and keychar
     */
    static int mapToKeyCode(gnu.x11.Input xInput, int xKeyCode, int xMods) {
        int mapped = KeyEvent.VK_UNDEFINED;
        int keysym = xInput.keycodeToKeysym(xKeyCode, xMods, true);

        // Special keys.
        if (keysym >= 255 << 8) {
            // FIXME: Add missing mappings.
            mapped = switch (Misc.getCode(keysym)) {
                case BACKSPACE -> KeyEvent.VK_BACK_SPACE;
                case TAB -> KeyEvent.VK_TAB;
                case CLEAR -> KeyEvent.VK_CLEAR;
                case RETURN -> KeyEvent.VK_ENTER;
                case PAUSE -> KeyEvent.VK_PAUSE;
                case SCROLL_LOCK -> KeyEvent.VK_SCROLL_LOCK;
                case ESCAPE -> KeyEvent.VK_ESCAPE;
                case HOME -> KeyEvent.VK_HOME;
                case LEFT -> KeyEvent.VK_LEFT;
                case UP -> KeyEvent.VK_UP;
                case RIGHT -> KeyEvent.VK_RIGHT;
                case DOWN -> KeyEvent.VK_DOWN;
                case PAGE_UP -> KeyEvent.VK_PAGE_UP;
                case PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
                case END -> KeyEvent.VK_END;
                case BEGIN -> KeyEvent.VK_BEGIN;
                case INSERT -> KeyEvent.VK_INSERT;
                case UNDO -> KeyEvent.VK_UNDO;
                case FIND -> KeyEvent.VK_FIND;
                case CANCEL -> KeyEvent.VK_CANCEL;
                case HELP -> KeyEvent.VK_HELP;
                case MODE_SWITCH -> KeyEvent.VK_MODECHANGE;
                case NUM_LOCK -> KeyEvent.VK_NUM_LOCK;
                case KP_LEFT -> KeyEvent.VK_KP_LEFT;
                case KP_UP -> KeyEvent.VK_KP_UP;
                case KP_RIGHT -> KeyEvent.VK_KP_RIGHT;
                case KP_DOWN -> KeyEvent.VK_KP_DOWN;
                case F1 -> KeyEvent.VK_F1;
                case F2 -> KeyEvent.VK_F2;
                case F3 -> KeyEvent.VK_F3;
                case F4 -> KeyEvent.VK_F4;
                case F5 -> KeyEvent.VK_F5;
                case F6 -> KeyEvent.VK_F6;
                case F7 -> KeyEvent.VK_F7;
                case F8 -> KeyEvent.VK_F8;
                case F9 -> KeyEvent.VK_F9;
                case F10 -> KeyEvent.VK_F10;
                case F11 -> KeyEvent.VK_F11;
                case F12 -> KeyEvent.VK_F12;
                case F13 -> KeyEvent.VK_F13;
                case F14 -> KeyEvent.VK_F14;
                case F15 -> KeyEvent.VK_F15;
                case F16 -> KeyEvent.VK_F16;
                case F17 -> KeyEvent.VK_F17;
                case F18 -> KeyEvent.VK_F18;
                case F19 -> KeyEvent.VK_F19;
                case F20 -> KeyEvent.VK_F20;
                case F21 -> KeyEvent.VK_F21;
                case F22 -> KeyEvent.VK_F22;
                case F23 -> KeyEvent.VK_F23;
                case F24 -> KeyEvent.VK_F24;
                case SHIFT_L, SHIFT_R -> KeyEvent.VK_SHIFT;
                case CONTROL_L, CONTROL_R -> KeyEvent.VK_CONTROL;
                case CAPS_LOCK, SHIFT_LOCK -> KeyEvent.VK_CAPS_LOCK;
                case META_L, META_R -> KeyEvent.VK_META;
                case ALT_L, ALT_R -> KeyEvent.VK_ALT;
                case DELETE -> KeyEvent.VK_DELETE;
                default -> KeyEvent.VK_UNDEFINED;
            };
        }
        // Map Latin1 characters.
        else if (keysym < 256) {
            // TODO: Add missing mappings, if any.
            // Lowercase characters are mapped to
            // their corresponding upper case pendants.
            if (keysym >= Latin1.A_SMALL.getCode() && keysym <= Latin1.Z_SMALL.getCode())
                mapped = keysym - 0x20;
                // Uppercase characters are mapped 1:1.
            else if (keysym >= Latin1.A.getCode() && keysym <= Latin1.Z.getCode())
                mapped = keysym;
                // Digits are mapped 1:1.
            else if (keysym >= Latin1.NUM_0.getCode() && keysym <= Latin1.NUM_9.getCode())
                mapped = keysym;
            else {
                mapped = switch (Latin1.getCode(keysym)) {
                    case SPACE -> KeyEvent.VK_SPACE;
                    case EXCLAM -> KeyEvent.VK_EXCLAMATION_MARK;
                    case QUOTE_DBL -> KeyEvent.VK_QUOTEDBL;
                    case NUMBER_SIGN -> KeyEvent.VK_NUMBER_SIGN;
                    case DOLLAR -> KeyEvent.VK_DOLLAR;
                    case AMPERSAND -> KeyEvent.VK_AMPERSAND;
                    case APOSTROPHE -> KeyEvent.VK_QUOTE;
                    case PAREN_LEFT -> KeyEvent.VK_LEFT_PARENTHESIS;
                    case PAREN_RIGHT -> KeyEvent.VK_RIGHT_PARENTHESIS;
                    case ASTERISK -> KeyEvent.VK_ASTERISK;
                    case PLUS -> KeyEvent.VK_PLUS;
                    case COMMA -> KeyEvent.VK_COMMA;
                    case MINUS -> KeyEvent.VK_MINUS;
                    case PERIOD -> KeyEvent.VK_PERIOD;
                    case SLASH -> KeyEvent.VK_SLASH;
                    case COLON -> KeyEvent.VK_COLON;
                    case SEMICOLON -> KeyEvent.VK_SEMICOLON;
                    case LESS -> KeyEvent.VK_LESS;
                    case EQUAL -> KeyEvent.VK_EQUALS;
                    case GREATER -> KeyEvent.VK_GREATER;
                    case AT -> KeyEvent.VK_AT;
                    case BRACKET_LEFT -> KeyEvent.VK_OPEN_BRACKET;
                    case BACKSLASH -> KeyEvent.VK_BACK_SLASH;
                    case BRACKET_RIGHT -> KeyEvent.VK_CLOSE_BRACKET;
                    case ASCII_CIRCUM -> KeyEvent.VK_CIRCUMFLEX;
                    case UNDERSCORE -> KeyEvent.VK_UNDERSCORE;
                    case GRAVE -> KeyEvent.VK_DEAD_GRAVE;
                    case BRACE_LEFT -> KeyEvent.VK_BRACELEFT;
                    case BRACE_RIGHT -> KeyEvent.VK_BRACERIGHT;
                    case ASCII_TILDE -> KeyEvent.VK_DEAD_TILDE;
                    case EXCLAM_DOWN -> KeyEvent.VK_INVERTED_EXCLAMATION_MARK;
                    default -> KeyEvent.VK_UNDEFINED;
                };
            }
        }
        return mapped;
    }

    /**
     * Maps X keycodes+modifiers to Java keychars.
     *
     * @param xInput   The X Input to use for mapping
     * @param xKeyCode the X keycode
     * @param xMods    the X key modifiers
     * @return the Java keychar
     */
    static char mapToKeyChar(gnu.x11.Input xInput, int xKeyCode, int xMods) {
        char mapped = KeyEvent.CHAR_UNDEFINED;
        char keysym = (char) xInput.keycodeToKeysym(xKeyCode, xMods, false);
        // FIXME: Map other encodings properly.
        if (keysym < 256) { // Latin1.
            mapped = keysym;
        }
        return mapped;
    }

    /**
     * Maps X modifier masks to AWT modifier masks.
     *
     * @param xMods the X modifiers
     * @return the AWT modifiers
     */
    static int mapModifiers(int xMods) {
        int mods = 0;

        if ((xMods & Input.KeyMask.SHIFT_MASK.getCode()) != 0)
            mods |= KeyEvent.SHIFT_MASK | KeyEvent.SHIFT_DOWN_MASK;
        if ((xMods & Input.KeyMask.META_MASK.getCode()) != 0)
            mods |= KeyEvent.META_MASK | KeyEvent.META_DOWN_MASK;
        if ((xMods & Input.KeyMask.ALT_MASK.getCode()) != 0)
            mods |= KeyEvent.ALT_MASK | KeyEvent.ALT_DOWN_MASK;
        if ((xMods & Input.KeyMask.MOD5_MASK.getCode()) != 0)
            mods |= KeyEvent.ALT_GRAPH_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK;
        if ((xMods & Input.KeyMask.CONTROL_MASK.getCode()) != 0)
            mods |= KeyEvent.CTRL_MASK | KeyEvent.CTRL_DOWN_MASK;

        return mods;
    }
}
