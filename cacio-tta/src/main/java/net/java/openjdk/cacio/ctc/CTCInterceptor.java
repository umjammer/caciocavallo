package net.java.openjdk.cacio.ctc;

import java.awt.GraphicsEnvironment;

/**
 * Delegation target for the redefined
 * {@link GraphicsEnvironment#getLocalGraphicsEnvironment()}. This class is
 * injected into the bootstrap classloader by CacioTestRunner, so it must only
 * reference bootstrap-visible classes (JDK classes plus the CTC graphics
 * classes that are injected alongside it).
 */
public class CTCInterceptor {

    public static GraphicsEnvironment intercept() {
        return CTCGraphicsEnvironment.getInstance();
    }

}
