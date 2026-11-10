package gnu.java.awt.peer.x;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.event.MouseEvent;
import java.util.Map;

import gnu.x11.Display;
import gnu.x11.Window;
import sun.awt.dnd.SunDragSourceContextPeer;
import sun.awt.dnd.SunDropTargetContextPeer;
import sun.awt.dnd.SunDropTargetEvent;


/**
 * A drag within this JVM, between its windows or inside of one. The button that
 * started it is still down, so X keeps sending the pointer to us wherever it
 * goes, and the event pump hands that here instead of to AWT. There is no XDND:
 * other clients do not see these drags and cannot start any here.
 */
final class EscherDragSourceContextPeer extends SunDragSourceContextPeer {

    private static volatile EscherDragSourceContextPeer active;

    /** The window under the pointer. */
    private EscherPlatformWindow over;

    /** Whether the target under the pointer takes the drag. */
    private boolean accepted;

    private long[] formats;
    private int sourceActions;
    private int lastX, lastY, lastModifiers;
    private int lastDropAction = DnDConstants.ACTION_NONE;

    /** The window the drag was started from, it shows the cursor. */
    private EscherPlatformWindow sourceWindow;

    /**
     * @return the drag going on, null if there is none
     */
    static EscherDragSourceContextPeer active() {
        return active;
    }

    EscherDragSourceContextPeer(DragGestureEvent dge) {
        super(dge);
    }

    private static XGraphicsDevice device() {
        return EscherToolkit.getDefaultDevice();
    }

    @Override
    protected void startDrag(Transferable transferable, long[] formats, Map<Long, DataFlavor> formatMap) {
        this.formats = formats;
        sourceActions = getDragSourceContext().getSourceActions();
        sourceWindow = device().getWindowOf(getComponent());
        Point p = getTrigger().getDragOrigin();
        Point location = getComponent().getLocationOnScreen();
        lastX = location.x + p.x;
        lastY = location.y + p.y;
        lastModifiers = getTrigger().getTriggerEvent().getModifiersEx();
        SunDropTargetContextPeer.setCurrentJVMLocalSourceTransferable(transferable);
        active = this;
        if (!device().isButtonDown()) {
            // let go before this got here, the pump will not tell us anymore
            drop(lastX, lastY, lastModifiers);
        }
    }

    @Override
    protected void setNativeCursor(long nativeCtxt, Cursor c, int cType) {
        if (c != null && sourceWindow != null) {
            sourceWindow.setCursor(c);
        }
    }

    /**
     * @return our window at that place on the screen, at the top of the stack
     */
    private EscherPlatformWindow windowAt(int x, int y) {
        XGraphicsDevice device = device();
        Display display = device.getDisplay();
        Window root = display.getDefaultRoot();
        Window window = root;
        // down from the root, a window manager's frames are in the way
        for (int depth = 0; depth < 8; depth++) {
            Window child = window.translateCoordinates(root, x, y).child();
            if (child == null) {
                return null;
            }
            EscherPlatformWindow found = device.getWindow(child.id());
            if (found != null) {
                return found;
            }
            window = child;
        }
        return null;
    }

    private void leave(int x, int y) {
        if (accepted) {
            dragExit(x, y);
            accepted = false;
        }
        EscherDropTargetContextPeer.instance().post(over.getCacioComponent().getAWTComponent(), 0, 0,
                DnDConstants.ACTION_NONE, DnDConstants.ACTION_NONE, formats, MouseEvent.MOUSE_EXITED,
                SunDropTargetContextPeer.DISPATCH_SYNC);
        over = null;
    }

    /** From the event pump, the pointer moved or the modifiers changed. */
    void motion(int x, int y, int modifiers) {
        lastX = x;
        lastY = y;
        lastModifiers = modifiers;
        int dropAction = convertModifiersToDropAction(modifiers, sourceActions);

        EscherPlatformWindow window = windowAt(x, y);
        if (over != null && window != over) {
            leave(x, y);
        }
        int target = DnDConstants.ACTION_NONE;
        if (window != null) {
            Component component = window.getCacioComponent().getAWTComponent();
            Point location = window.getLocationOnScreen();
            int id = over == null ? MouseEvent.MOUSE_ENTERED : MouseEvent.MOUSE_DRAGGED;
            over = window;
            target = EscherDropTargetContextPeer.instance().post(component, x - location.x, y - location.y,
                    dropAction, sourceActions, formats, id, SunDropTargetContextPeer.DISPATCH_SYNC);
        }

        if (target != DnDConstants.ACTION_NONE) {
            if (!accepted) {
                accepted = true;
                dragEnter(target, modifiers, x, y);
            } else if (dropAction != lastDropAction) {
                postDragSourceDragEvent(target, modifiers, x, y, DISPATCH_CHANGED);
            } else {
                postDragSourceDragEvent(target, modifiers, x, y, DISPATCH_MOTION);
            }
        } else if (accepted) {
            accepted = false;
            dragExit(x, y);
        }
        postDragSourceDragEvent(target, modifiers, x, y, DISPATCH_MOUSE_MOVED);
        lastDropAction = dropAction;
    }

    /** From the event pump, a modifier key went up or down. */
    void modifiers(int modifiers) {
        motion(lastX, lastY, modifiers);
    }

    /** From the event pump, the button came up. */
    void drop(int x, int y, int modifiers) {
        lastX = x;
        lastY = y;
        if (over != null && accepted) {
            EscherPlatformWindow window = over;
            Point location = window.getLocationOnScreen();
            int dropAction = convertModifiersToDropAction(modifiers, sourceActions);
            // the target ends this with doDropDone, and it may take its time
            EscherDropTargetContextPeer.instance().post(window.getCacioComponent().getAWTComponent(),
                    x - location.x, y - location.y, dropAction, sourceActions, formats,
                    SunDropTargetEvent.MOUSE_DROPPED, !SunDropTargetContextPeer.DISPATCH_SYNC);
        } else {
            cancel();
        }
    }

    /** From the event pump, or when there is nothing to drop on. */
    void cancel() {
        if (over != null) {
            leave(lastX, lastY);
        }
        finished(false, DnDConstants.ACTION_NONE);
    }

    /** The drop is over and done with. */
    void finished(boolean success, int dropAction) {
        if (active == this) {
            active = null;
            SunDropTargetContextPeer.setCurrentJVMLocalSourceTransferable(null);
            dragDropFinished(success, dropAction, lastX, lastY);
        }
    }
}
