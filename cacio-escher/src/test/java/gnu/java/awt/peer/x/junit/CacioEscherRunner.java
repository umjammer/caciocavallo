package gnu.java.awt.peer.x.junit;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import javax.swing.plaf.metal.MetalLookAndFeel;

import gnu.java.awt.peer.x.EscherToolkit;
import gnu.java.awt.peer.x.XGraphicsEnvironment;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;


/**
 * Installs the escher toolkit and graphics environment into AWT.
 *
 * Same mechanism as cacio-sdl2's {@code CacioSDL2Runner}: since JDK 9 the
 * {@code awt.toolkit} and {@code java.awt.graphicsenv} system properties are
 * ignored, so the toolkit is set by reflection into {@code Toolkit.toolkit} and
 * {@code GraphicsEnvironment.getLocalGraphicsEnvironment()} is redefined with
 * ByteBuddy.
 */
public final class CacioEscherRunner {

    private static boolean installed;

    private CacioEscherRunner() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        System.setProperty("awt.toolkit", EscherToolkit.class.getName());
        System.setProperty("java.awt.graphicsenv", XGraphicsEnvironment.class.getName());
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
            PostEventShim.installIfNeeded();

            Field toolkit = Toolkit.class.getDeclaredField("toolkit");
            toolkit.setAccessible(true);
            toolkit.set(null, new EscherToolkit());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void injectGraphicsEnvironment() throws IOException {
        injectIntoBootstrapClassLoader(ByteBuddyAgent.install(), EscherInterceptor.class);

        // the injected copy is a different class from the one on the app
        // classpath, so the environment has to be handed to that copy
        try {
            Class<?> injected = Class.forName(EscherInterceptor.class.getName(), true, null);
            injected.getMethod("setLocalGraphicsEnvironment", GraphicsEnvironment.class)
                    .invoke(null, new XGraphicsEnvironment());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        new ByteBuddy()
                .redefine(
                        TypePool.Default.ofSystemLoader().describe("java.awt.GraphicsEnvironment").resolve(),
                        ClassFileLocator.ForClassLoader.ofSystemLoader())
                .method(ElementMatchers.named("getLocalGraphicsEnvironment"))
                .intercept(MethodDelegation.to(EscherInterceptor.class))
                .make()
                .load(Object.class.getClassLoader(),
                      ClassReloadingStrategy.fromInstalledAgent());
    }

    /**
     * Unsafe based injection is gone as of JDK 26, this is the supported way.
     */
    private static void injectIntoBootstrapClassLoader(Instrumentation instrumentation, Class<?>... classes) throws IOException {
        Path jar = Files.createTempFile("cacio-escher-bootstrap", ".jar");
        jar.toFile().deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Class<?> clazz : classes) {
                String name = clazz.getName().replace('.', '/').concat(".class");
                out.putNextEntry(new JarEntry(name));
                try (InputStream in = clazz.getClassLoader().getResourceAsStream(name)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar.toFile()));
    }
}
