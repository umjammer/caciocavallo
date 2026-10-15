package net.java.openjdk.cacio.ctc.junit;

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

import net.java.openjdk.cacio.ctc.CTCGraphicsConfiguration;
import net.java.openjdk.cacio.ctc.CTCGraphicsDevice;
import net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment;
import net.java.openjdk.cacio.ctc.CTCInterceptor;
import net.java.openjdk.cacio.ctc.CTCToolkit;
import org.junit.jupiter.api.extension.Extension;

/**
 * Installs the CTC toolkit and graphics environment into AWT.
 *
 * Since JDK 9 the {@code awt.toolkit} and {@code java.awt.graphicsenv} system
 * properties are ignored ({@code Toolkit}/{@code GraphicsEnvironment} use the
 * platform defaults from {@code PlatformGraphicsInfo}), so the CTC
 * implementations have to be injected: the toolkit via reflection into
 * {@code Toolkit.toolkit}, and the graphics environment by redefining
 * {@code GraphicsEnvironment.getLocalGraphicsEnvironment()} with ByteBuddy.
 * The delegation target and the CTC graphics classes are injected into the
 * bootstrap classloader because {@code GraphicsEnvironment} lives there.
 */
public class CacioTestRunner implements Extension {

    static {
        install();
    }

    private static boolean installed;

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        System.setProperty("awt.toolkit", CTCToolkit.class.getName());
        System.setProperty("java.awt.graphicsenv", CTCGraphicsEnvironment.class.getName());
        System.setProperty("swing.defaultlaf", MetalLookAndFeel.class.getName());
        System.setProperty("java.awt.headless", "false");

        try {
            injectGraphicsEnvironment();

            Field toolkit = Toolkit.class.getDeclaredField("toolkit");
            toolkit.setAccessible(true);
            toolkit.set(null, new CTCToolkit());

            Field defaultHeadlessField = GraphicsEnvironment.class.getDeclaredField("defaultHeadless");
            defaultHeadlessField.setAccessible(true);
            defaultHeadlessField.set(null, Boolean.FALSE);
            Field headlessField = GraphicsEnvironment.class.getDeclaredField("headless");
            headlessField.setAccessible(true);
            headlessField.set(null, Boolean.FALSE);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void injectGraphicsEnvironment() throws IOException {
        ByteBuddyAgent.install();

        injectIntoBootstrapClassLoader(
                CTCInterceptor.class,
                CTCGraphicsEnvironment.class,
                CTCGraphicsConfiguration.class,
                CTCGraphicsDevice.class);

        new ByteBuddy()
                .redefine(
                        TypePool.Default.ofSystemLoader().describe("java.awt.GraphicsEnvironment").resolve(),
                        ClassFileLocator.ForClassLoader.ofSystemLoader())
                .method(ElementMatchers.named("getLocalGraphicsEnvironment"))
                .intercept(MethodDelegation.to(CTCInterceptor.class))
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
