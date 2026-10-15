package net.java.openjdk.awt.peer.web;

import com.github.caciocavallosilano.cacio.peer.managed.FocusManager;
import net.java.openjdk.cacio.servlet.WebSessionManager;


public class WebFocusManager extends FocusManager {

    @Override
    protected FocusManager getContextInstance() {
        WebSessionState state = WebSessionManager.getInstance().getCurrentState();

        WebFocusManager mgr;
        if ((mgr = state.getFocusManager()) == null) {
            mgr = new WebFocusManager();
            state.setFocusManager(mgr);
        }

        return mgr;
    }

}
