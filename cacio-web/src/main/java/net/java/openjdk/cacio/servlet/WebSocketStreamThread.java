package net.java.openjdk.cacio.servlet;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Proxy;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import net.java.openjdk.awt.peer.web.WebSessionState;
import net.java.openjdk.cacio.servlet.transport.Transport;


@ServerEndpoint(value = "/WebSocket", configurator = WebSocketStreamThread.HttpSessionConfigurator.class)
public class WebSocketStreamThread extends Thread {

    private static final Logger logger = System.getLogger(WebSocketStreamThread.class.getName());

    public static class HttpSessionConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
            Object httpSession = request.getHttpSession();
            if (httpSession != null) {
                config.getUserProperties().put(HttpSession.class.getName(), httpSession);
            }
        }
    }

    WebSessionState state;
    private Session session;
    final WebSessionManager sessionManager;

    volatile boolean close = false;

    public WebSocketStreamThread() {
        this.sessionManager = WebSessionManager.getInstance();
    }

    @OnOpen
    public void onConnect(Session session, EndpointConfig config) {
        this.session = session;
        // why jetty wraps HttpServletRequest?, there is no compatibility.
        // at least the wrapped class should have a getter method for original.
        this.state = WebSessionManager.getInstance().getSessionState((HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> {
                    // depends on getSessionState() implementation uses only two methods bellow.
                    if ("getSession".equals(method.getName())) {
                        // casting depends on UpgradeRequest implementation is only ServletUpgradeRequest.
                        return config.getUserProperties().get(HttpSession.class.getName());
                    } else if ("getParameter".equals(method.getName()) && args != null && args.length == 1) {
                        List<String> values = session.getRequestParameterMap().get((String) args[0]);
                        return values != null && !values.isEmpty() ? values.getFirst() : null;
                    }
                    return null;
                }));
        start();
    }

    @OnMessage
    public void onText(String message) {
        WebSessionManager.getInstance().registerSessionAtCurrentThread(state);
        if (state.getEventManager() != null) {
            state.getEventManager().parseEventData(-1, message);
        }
    }

    @OnClose
    public void onClose(Session session) {
        close = true;
    }

    @Override
    public void run() {
        try {
            sessionManager.registerSessionAtCurrentThread(state);

            while (!close) {
                Transport transport = state.getScreen().pollForScreenUpdates(15000);

                if (!close) {
                    session.getAsyncRemote().sendText(transport.asString());
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