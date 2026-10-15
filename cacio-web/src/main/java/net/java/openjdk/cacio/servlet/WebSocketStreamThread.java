package net.java.openjdk.cacio.servlet;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.servlet.http.HttpSession;

import net.java.openjdk.awt.peer.web.WebSessionState;
import net.java.openjdk.cacio.servlet.transport.Transport;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.servlet.UpgradeHttpServletRequest;


@WebSocket
public class WebSocketStreamThread extends Thread {

    private static final Logger logger = System.getLogger(WebSocketStreamThread.class.getName());

    WebSessionState state;
    private Session session;
    final WebSessionManager sessionManager;

    volatile boolean close = false;

    public WebSocketStreamThread() {
        this.sessionManager = WebSessionManager.getInstance();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        // why jetty wraps HttpServletRequest?, there is no compatibility.
        // at least the wrapped class should have a getter method for original.
        this.state = WebSessionManager.getInstance().getSessionState(new UpgradeHttpServletRequest(null) {
            // depends on getSessionState() implementation uses only two methods bellow.
            @Override
            public HttpSession getSession(boolean create) {
                // casting depends on UpgradeRequest implementation is only ServletUpgradeRequest.
                return (HttpSession) session.getUpgradeRequest().getSession();
            }

            @Override
            public String getParameter(String name) {
                return session.getUpgradeRequest().getParameterMap().get(name).getFirst();
            }
        });
        start();
    }

    @OnWebSocketMessage
    public void onText(String message) {
        WebSessionManager.getInstance().registerSessionAtCurrentThread(state);
        if (state.getEventManager() != null) {
            state.getEventManager().parseEventData(-1, message);
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        close = true;
    }

    @Override
    public void run() {
        try {
            sessionManager.registerSessionAtCurrentThread(state);

            while (!close) {
                Transport transport = state.getScreen().pollForScreenUpdates(15000);

                if (!close) {
                    session.getRemote().sendStringByFuture(transport.asString());
                }
            }
        } catch (IOException e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            close = true;
        }

        // null: depends on disposeSessionState() implementation doesn't use session parameter inside.
        sessionManager.disposeSessionState(null, state);
    }
}