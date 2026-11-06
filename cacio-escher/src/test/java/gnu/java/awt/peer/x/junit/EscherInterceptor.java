package gnu.java.awt.peer.x.junit;

import java.awt.GraphicsEnvironment;


/**
 * Delegation target for the redefined
 * {@link GraphicsEnvironment#getLocalGraphicsEnvironment()}.
 *
 * This class is injected into the bootstrap classloader by
 * {@link CacioEscherRunner}, because {@code GraphicsEnvironment} lives there,
 * so it must not reference anything outside the JDK. The escher graphics
 * environment itself stays on the application classpath and is handed over by
 * {@link #setLocalGraphicsEnvironment} before the redefinition happens.
 */
public class EscherInterceptor {

    private static GraphicsEnvironment localEnv;

    public static void setLocalGraphicsEnvironment(GraphicsEnvironment env) {
        localEnv = env;
    }

    public static GraphicsEnvironment intercept() {
        return localEnv;
    }
}
