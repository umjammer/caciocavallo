package net.java.openjdk.cacio.servlet;

import java.util.HashMap;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import net.java.openjdk.awt.peer.web.WebSessionState;


public class TestSessionManager extends WebSessionManager {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    final HashMap<Integer, WebSessionState> idSessionMap = new HashMap<>();

    @Override
    public synchronized WebSessionState register(HttpSession session) {
        int subSessionID = idSessionMap.size();
        WebSessionState sessionState = new WebSessionState(subSessionID, idSessionMap);
        idSessionMap.put(subSessionID, sessionState);

        return sessionState;
    }

    @Override
    public synchronized WebSessionState getCurrentState(HttpSession session, int subSessionID) {
        WebSessionState state = idSessionMap.get(subSessionID);
        registerSessionAtCurrentThread(state);

        return state;
    }

    @Override
    public synchronized void disposeSession(HttpSession session) {
    }

    public synchronized void disposeSession(int subSessionID) {
        WebSessionState state = idSessionMap.remove(subSessionID);
        state.dispose();
    }

    @Override
    public WebSessionState getSessionState(HttpServletRequest request) {
        String subSessionID = request.getParameter("subsessionid");
        return getCurrentState(null, Integer.parseInt(subSessionID));
    }
}
