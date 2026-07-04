package net.java.openjdk.cacio.ctc.junit;

import java.lang.reflect.Method;

import org.fest.swing.junit.runner.FailureScreenshotTaker;
import org.fest.swing.junit.runner.ImageFolderCreator;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.fest.swing.annotation.GUITestFinder.isGUITest;
import static org.fest.swing.junit.runner.Formatter.testNameFrom;

public class CacioFESTRunner implements AfterTestExecutionCallback {

    static {
        CacioTestRunner.install();
    }

    private final FailureScreenshotTaker screenshotTaker;

    public CacioFESTRunner() {
        screenshotTaker = new FailureScreenshotTaker(new ImageFolderCreator().createImageFolder());
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) throws Exception {
        if (extensionContext.getExecutionException().isPresent()) {
            takeScreenshot(extensionContext);
        }

    }

    private void takeScreenshot(ExtensionContext extensionContext) {
        Method realMethod = extensionContext.getTestMethod().get();
        Class<?> testClass = realMethod.getDeclaringClass();
        if (!(isGUITest(testClass, realMethod))) return;
        screenshotTaker.saveScreenshot(testNameFrom(testClass, realMethod));
    }
}
