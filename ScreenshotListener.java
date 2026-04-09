package listeners;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ScreenshotListener — implements ITestListener to capture screenshots on failure.
 *
 * ITestListener is a TestNG interface that hooks into the test lifecycle.
 * Registered once in testng.xml and applies to ALL test classes automatically.
 *
 * ISSUES FIXED IN THIS FILE:
 * ─────────────────────────────────────────────────────────────────────
 * ISSUE-04 / ISSUE-05 FIXED:
 *   PatientApiTest does NOT extend BaseClass — it has no "driver" field.
 *   The original getDriverFromResult() called getSuperclass().getDeclaredField("driver")
 *   which throws NoSuchFieldException for API test classes, crashing the listener.
 *
 *   Fix: wrapped reflection in try-catch, and added a null check before casting.
 *   If driver field is not found (API test class), we skip screenshot gracefully
 *   instead of crashing the entire listener for all subsequent tests.
 * ─────────────────────────────────────────────────────────────────────
 */
public class ScreenshotListener implements ITestListener {

    private static final String SCREENSHOT_DIR = "screenshots/";

    @Override
    public void onStart(ITestContext context) {
        new File(SCREENSHOT_DIR).mkdirs();
        System.out.println("[ScreenshotListener] Ready. Screenshots → " + SCREENSHOT_DIR);
    }

    @Override
    public void onTestStart(ITestResult result) {
        System.out.println("[ScreenshotListener] STARTED  → " + result.getName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        System.out.println("[ScreenshotListener] PASSED   → " + result.getName());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        System.out.println("[ScreenshotListener] SKIPPED  → " + result.getName());
    }

    /**
     * Called when a test FAILS — capture screenshot if driver is available.
     *
     * ISSUE-04 FIXED: API tests (PatientApiTest) have no driver field.
     * getDriverFromResult() now returns null safely for those classes,
     * and we skip screenshot with a message instead of crashing.
     */
    @Override
    public void onTestFailure(ITestResult result) {
        System.out.println("[ScreenshotListener] FAILED   → " + result.getName() + " — attempting screenshot...");

        WebDriver driver = getDriverFromResult(result);

        // ISSUE-04 FIXED: null check — API tests have no driver, skip silently
        if (driver == null) {
            System.out.println("[ScreenshotListener] No WebDriver found for "
                + result.getTestClass().getRealClass().getSimpleName()
                + " — screenshot skipped (expected for API tests).");
            return;
        }

        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String className = result.getTestClass().getRealClass().getSimpleName();
            String method    = result.getName();
            String filePath  = SCREENSHOT_DIR + className + "_" + method + "_" + timestamp + ".png";

            // TakesScreenshot — Selenium interface implemented by ChromeDriver
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), Paths.get(filePath));

            System.out.println("[ScreenshotListener] Screenshot saved → " + filePath);

        } catch (IOException e) {
            System.out.println("[ScreenshotListener] Failed to save screenshot: " + e.getMessage());
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        System.out.println("[ScreenshotListener] Suite done → "
            + "Passed: "  + context.getPassedTests().size()
            + " | Failed: " + context.getFailedTests().size()
            + " | Skipped: " + context.getSkippedTests().size());
    }

    /**
     * Gets WebDriver from a Selenium test class via Java Reflection.
     *
     * ISSUE-04 FIXED:
     *   Original code called getSuperclass().getDeclaredField("driver") which
     *   throws NoSuchFieldException if the test class does NOT extend BaseClass
     *   (e.g. PatientApiTest). Now wrapped in try-catch returning null safely.
     *
     * How it works:
     *   - LoginTest/PatientTest extend BaseClass which has "protected WebDriver driver"
     *   - We use reflection to access that protected field at runtime
     *   - setAccessible(true) allows access to protected fields from outside the class
     *   - PatientApiTest has no superclass with "driver" → returns null
     */
    private WebDriver getDriverFromResult(ITestResult result) {
        try {
            Object testInstance = result.getInstance();
            Class<?> clazz = testInstance.getClass();

            // Walk up the class hierarchy looking for a "driver" field
            // This handles both direct BaseClass children and deeper hierarchies
            while (clazz != null && clazz != Object.class) {
                try {
                    Field driverField = clazz.getDeclaredField("driver");
                    driverField.setAccessible(true);
                    Object value = driverField.get(testInstance);
                    if (value instanceof WebDriver) {
                        return (WebDriver) value;
                    }
                } catch (NoSuchFieldException e) {
                    // Field not in this class — check superclass
                    clazz = clazz.getSuperclass();
                }
            }
            // No driver field found anywhere in hierarchy (e.g. PatientApiTest)
            return null;

        } catch (Exception e) {
            System.out.println("[ScreenshotListener] Reflection error: " + e.getMessage());
            return null;
        }
    }
}
