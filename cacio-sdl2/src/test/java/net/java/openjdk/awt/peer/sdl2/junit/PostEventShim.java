package net.java.openjdk.awt.peer.sdl2.junit;

import java.awt.AWTEvent;
import java.awt.Toolkit;

import com.github.caciocavallosilano.cacio.peer.CacioToolkit;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;


/**
 * Keeps cacio-shared 2.1.0 running on JDK 27.
 *
 * It was built for JDK 25 and posts its events with
 * {@code SunToolkit.postEvent(AppContext, AWTEvent)}, which went away along with
 * {@code sun.awt.AppContext}. The only two places that do are private
 * {@code postEvent(AWTEvent)} methods, so they are redirected here. This should
 * go once cacio-shared has dropped {@code AppContext}.
 */
public final class PostEventShim {

    private static final String[] AFFECTED = {
        "com.github.caciocavallosilano.cacio.peer.CacioComponentPeer",
        "com.github.caciocavallosilano.cacio.peer.CacioKeyboardFocusManagerPeer",
    };

    private PostEventShim() {
    }

    /** The replacement, it has to be public for the redefined classes to call it. */
    public static void post(AWTEvent event) {
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(event);
    }

    static void installIfNeeded() throws ReflectiveOperationException {
        if (hasAppContext()) {
            return;
        }
        ClassLoader loader = CacioToolkit.class.getClassLoader();
        for (String name : AFFECTED) {
            Class<?> type = Class.forName(name, false, loader);
            new ByteBuddy()
                    .redefine(type, ClassFileLocator.ForClassLoader.of(loader))
                    .method(ElementMatchers.named("postEvent")
                            .and(ElementMatchers.takesArguments(AWTEvent.class)))
                    .intercept(MethodCall.invoke(PostEventShim.class.getMethod("post", AWTEvent.class))
                            .withAllArguments())
                    .make()
                    .load(loader, ClassReloadingStrategy.fromInstalledAgent());
        }
    }

    private static boolean hasAppContext() {
        try {
            Class.forName("sun.awt.AppContext", false, null);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
