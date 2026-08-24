/*
 * Copyright (c) 2023 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

import net.java.openjdk.awt.peer.sdl2.SDL2GraphicsEnvironment;
import net.java.openjdk.awt.peer.sdl2.junit.CacioSDL2Runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2023-06-14 nsano initial version <br>
 */
public class TestCase {

    /** the JDK demo, overridable with -Dswingset2.jar=... */
    static final Path SWINGSET2 = Path.of(System.getProperty("swingset2.jar",
            System.getProperty("java.home") + "/demo/jfc/SwingSet2/SwingSet2.jar"));

    /** how long to keep the demo up, -Dswingset2.seconds=0 runs until the window is closed */
    static final int SECONDS = Integer.getInteger("swingset2.seconds", 20);

    /** how long each turn of the main thread's run loop lasts, so ~60Hz */
    static final int FRAME_MILLIS = 16;

    /**
     * Runs the JDK's SwingSet2 demo on the SDL2 backend.
     *
     * Needs the native library, so it only runs under {@code -P native}:
     * that profile builds {@code libcacio-sdl2} and adds the
     * {@code -XstartOnFirstThread} the SDL2 Cocoa backend requires.
     */
    @Test
    void test1() throws Exception {
        assumeTrue(hasNativeLibrary(), "libcacio-sdl2 not built, run with -P native");
        assumeTrue(java.nio.file.Files.exists(SWINGSET2), SWINGSET2 + " not found");

        CacioSDL2Runner.install();



        Thread demo = new Thread(TestCase::launchSwingSet2, "SwingSet2");
        demo.setDaemon(true);
        demo.start();

        // the SDL2 window lives on this thread, so all it may do from here on
        // is service the platform run loop the backend dispatches its calls to
        pump(() -> showingWindow() != null, 30_000);
        Window window = showingWindow();
        assertNotNull(window, "SwingSet2 did not open a window");
        System.err.println("SwingSet2 window: " + window.getSize());

        // leave it up for a while, so the demo can be looked at and driven
        pump(() -> showingWindow() == null || SDL2GraphicsEnvironment.isCloseRequested(),
             SECONDS > 0 ? SECONDS * 1000L : Long.MAX_VALUE);
    }

    static boolean hasNativeLibrary() {
        String path = System.getProperty("java.library.path", "");
        return Arrays.stream(path.split(File.pathSeparator))
                .anyMatch(dir -> new File(dir, System.mapLibraryName("cacio-sdl2")).exists());
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

    /** Services the platform run loop until {@code done} or the timeout. */
    static void pump(BooleanSupplier done, long timeoutMillis) {
        long start = System.currentTimeMillis();
        while (!done.getAsBoolean() && System.currentTimeMillis() - start < timeoutMillis) {
            SDL2GraphicsEnvironment.pumpMainThread(FRAME_MILLIS);
        }
    }

    /**
     * @return a frame that is up and has been laid out, or null -- a frame
     *         reports itself showing before it has been given a size, so the
     *         size is what says the demo is really up
     */
    static Window showingWindow() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing()
                && frame.getWidth() > 0 && frame.getHeight() > 0) {
                return frame;
            }
        }
        return null;
    }
}
