package net.java.openjdk.awt.peer.sdl2.junit;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import javax.swing.plaf.metal.MetalLookAndFeel;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

import net.java.openjdk.awt.peer.sdl2.SDL2GraphicsEnvironment;
import net.java.openjdk.awt.peer.sdl2.SDL2Toolkit;

/**
 * Installs the SDL2 toolkit and graphics environment into AWT.
 *
 * Same mechanism as cacio-tta's {@code CacioTestRunner}: since JDK 9 the
 * {@code awt.toolkit} and {@code java.awt.graphicsenv} system properties are
 * ignored, so the toolkit is set by reflection into {@code Toolkit.toolkit} and
 * {@code GraphicsEnvironment.getLocalGraphicsEnvironment()} is redefined with
 * ByteBuddy. Only {@link SDL2Interceptor} has to be injected into the bootstrap
 * classloader here -- it holds the graphics environment as a plain
 * {@code GraphicsEnvironment}, so the SDL2 classes can stay on the application
 * classpath where their native library and cacio-shared are visible.
 *
 * Everything below has to run on the process main thread: creating the toolkit
 * opens the SDL window. See
 * {@link SDL2GraphicsEnvironment#pumpMainThread(int)}.
 */
public final class CacioSDL2Runner {

    private static boolean installed;

    private CacioSDL2Runner() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        System.setProperty("awt.toolkit", SDL2Toolkit.class.getName());
        System.setProperty("java.awt.graphicsenv", SDL2GraphicsEnvironment.class.getName());
        System.setProperty("swing.defaultlaf", MetalLookAndFeel.class.getName());
        System.setProperty("java.awt.headless", "false");

        try {
            Field defaultHeadlessField = GraphicsEnvironment.class.getDeclaredField("defaultHeadless");
            defaultHeadlessField.setAccessible(true);
            defaultHeadlessField.set(null, Boolean.FALSE);
            Field headlessField = GraphicsEnvironment.class.getDeclaredField("headless");
            headlessField.setAccessible(true);
            headlessField.set(null, Boolean.FALSE);

            injectGraphicsEnvironment();

            Field toolkit = Toolkit.class.getDeclaredField("toolkit");
            toolkit.setAccessible(true);
            toolkit.set(null, new SDL2Toolkit());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void injectGraphicsEnvironment() throws IOException {
        ByteBuddyAgent.install();

        injectIntoBootstrapClassLoader(SDL2Interceptor.class);

        // the injected copy is a different class from the one on the app
        // classpath, so the environment has to be handed to that copy
        try {
            Class<?> injected = Class.forName(SDL2Interceptor.class.getName(), true, null);
            injected.getMethod("setLocalGraphicsEnvironment", GraphicsEnvironment.class)
                    .invoke(null, new SDL2GraphicsEnvironment());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        new ByteBuddy()
                .redefine(
                        TypePool.Default.ofSystemLoader().describe("java.awt.GraphicsEnvironment").resolve(),
                        ClassFileLocator.ForClassLoader.ofSystemLoader())
                .method(ElementMatchers.named("getLocalGraphicsEnvironment"))
                .intercept(MethodDelegation.to(SDL2Interceptor.class))
                .make()
                .load(Object.class.getClassLoader(),
                      ClassReloadingStrategy.fromInstalledAgent());
    }

    private static void injectIntoBootstrapClassLoader(Class<?>... classes) throws IOException {
        for (Class<?> clazz : classes) {
            byte[] buffer = clazz.getClassLoader()
                    .getResourceAsStream(clazz.getName().replace('.', '/').concat(".class"))
                    .readAllBytes();
            ClassInjector.UsingUnsafe injector = new ClassInjector.UsingUnsafe(null);
            injector.injectRaw(Map.of(clazz.getName(), buffer));
        }
    }
}
