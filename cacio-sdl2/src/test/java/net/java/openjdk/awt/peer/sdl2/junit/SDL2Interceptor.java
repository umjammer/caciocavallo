package net.java.openjdk.awt.peer.sdl2.junit;

import java.awt.GraphicsEnvironment;

/**
 * Delegation target for the redefined
 * {@link GraphicsEnvironment#getLocalGraphicsEnvironment()}.
 *
 * This class is injected into the bootstrap classloader by
 * {@link CacioSDL2Runner}, because {@code GraphicsEnvironment} lives there, so
 * it must not reference anything outside the JDK. The SDL2 graphics
 * environment itself stays on the application classpath and is handed over by
 * {@link #setLocalGraphicsEnvironment} before the redefinition happens.
 */
public class SDL2Interceptor {

    private static GraphicsEnvironment localEnv;

    public static void setLocalGraphicsEnvironment(GraphicsEnvironment env) {
        localEnv = env;
    }

    public static GraphicsEnvironment intercept() {
        return localEnv;
    }
}
