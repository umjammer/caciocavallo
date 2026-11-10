/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import gnu.java.awt.peer.x.junit.CacioEscherRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Runs the JDK's SwingSet2 demo on the escher backend.
 *
 * Opt in with {@code -Descher.swingset2=true}: it needs an X server, taken from
 * {@code $DISPLAY}, and opens windows on it.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
public class TestCase {

    /** the JDK demo, overridable with -Dswingset2.jar=... */
    static final Path SWINGSET2 = Path.of(System.getProperty("swingset2.jar",
            System.getProperty("java.home") + "/demo/jfc/SwingSet2/SwingSet2.jar"));

    /** how long to keep the demo up, -Dswingset2.seconds=0 runs until the window is closed */
    static final int SECONDS = Integer.getInteger("swingset2.seconds", 20);

    @Test
    @EnabledIfSystemProperty(named = "escher.swingset2", matches = "true")
    void test1() throws Exception {
        assumeTrue(Files.exists(SWINGSET2), SWINGSET2 + " not found");
        run();
    }

    static void run() throws Exception {
        CacioEscherRunner.install();

        Thread demo = new Thread(TestCase::launchSwingSet2, "SwingSet2");
        demo.setDaemon(true);
        demo.start();

        waitFor(() -> showingWindow() != null, 60_000);
        Window window = showingWindow();
        assertNotNull(window, "SwingSet2 did not open a window");
        System.err.println("SwingSet2 window: " + window.getSize());

        // leave it up for a while, so the demo can be looked at and driven
        waitFor(() -> showingWindow() == null, SECONDS > 0 ? SECONDS * 1000L : Long.MAX_VALUE);
    }

    static void launchSwingSet2() {
        try {
            ClassLoader loader = new URLClassLoader("swingset2",
                    new java.net.URL[] { SWINGSET2.toUri().toURL() },
                    TestCase.class.getClassLoader());
            Method main = Class.forName("SwingSet2", true, loader)
                    .getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void waitFor(java.util.function.BooleanSupplier done, long timeoutMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!done.getAsBoolean() && System.currentTimeMillis() - start < timeoutMillis) {
            Thread.sleep(50);
        }
    }

    /**
     * @return a frame that is up and has been laid out, or null -- a frame
     *         reports itself showing before it has been given a size, so the
     *         size is what says the demo is really up
     */
    static Window showingWindow() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing() && frame.getWidth() > 0 && frame.getHeight() > 0) {
                return frame;
            }
        }
        return null;
    }
}
