package gnu.java.awt.peer.x;

import java.awt.Component;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.event.MouseEvent;
import java.io.Serial;

import sun.awt.dnd.SunDragSourceContextPeer;


/**
 * Recognizes a drag for plain AWT, Swing has its own for {@code TransferHandler}:
 * a press followed by moving further than the drag threshold.
 */
final class EscherDragGestureRecognizer extends MouseDragGestureRecognizer {

    @Serial
    private static final long serialVersionUID = 1L;

    EscherDragGestureRecognizer(DragSource ds, Component c, int actions, DragGestureListener dgl) {
        super(ds, c, actions, dgl);
    }

    private int action(MouseEvent e) {
        return SunDragSourceContextPeer.convertModifiersToDropAction(e.getModifiersEx(), getSourceActions());
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        events.clear();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        events.clear();
        if (action(e) != DnDConstants.ACTION_NONE) {
            appendEvent(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        events.clear();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (events.isEmpty()) {
            return;
        }
        int action = action(e);
        if (action == DnDConstants.ACTION_NONE) {
            events.clear();
            return;
        }
        MouseEvent trigger = (MouseEvent) events.get(0);
        Point origin = trigger.getPoint();
        int threshold = DragSource.getDragThreshold();
        if (Math.abs(e.getX() - origin.x) > threshold || Math.abs(e.getY() - origin.y) > threshold) {
            fireDragGestureRecognized(action, origin);
        } else {
            appendEvent(e);
        }
    }
}
