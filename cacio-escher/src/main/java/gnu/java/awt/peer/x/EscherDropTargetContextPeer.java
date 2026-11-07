package gnu.java.awt.peer.x;

import java.awt.Component;
import java.io.IOException;

import sun.awt.dnd.SunDropTargetContextPeer;


/**
 * The drop side of a drag within this JVM, the data comes straight from the
 * local transferable.
 */
final class EscherDropTargetContextPeer extends SunDropTargetContextPeer {

    private static final EscherDropTargetContextPeer instance = new EscherDropTargetContextPeer();

    static EscherDropTargetContextPeer instance() {
        return instance;
    }

    private EscherDropTargetContextPeer() {
    }

    /**
     * @return the action the target settled on, for a synchronous event
     */
    int post(Component window, int x, int y, int dropAction, int actions, long[] formats, int id, boolean sync) {
        return postDropTargetEvent(window, x, y, dropAction, actions, formats, 0, id, sync);
    }

    @Override
    protected Object getNativeData(long format) throws IOException {
        throw new IOException("drags from other clients are not supported");
    }

    @Override
    protected void doDropDone(boolean success, int dropAction, boolean isLocal) {
        EscherDragSourceContextPeer drag = EscherDragSourceContextPeer.active();
        if (drag != null) {
            drag.finished(success, dropAction);
        }
    }
}
