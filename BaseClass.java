package base;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/**
 * BaseClass — WebDriver lifecycle management.
 *
 * All test classes extend this class and inherit:
 *   driver     → WebDriver instance (Chrome)
 *   wait       → WebDriverWait with 15 second timeout
 *   BASE_URL   → Application URL
 *
 * ISSUE-08 FIXED: Duplicate @AfterMethod conflict.
 *   BaseClass had @AfterMethod captureScreenshotOnFailure().
 *   LoginTest also had @AfterMethod resetToLoginPage().
 *   PatientTest also had @AfterMethod resetState().
 *   When a subclass test runs, TestNG calls BOTH the BaseClass @AfterMethod
 *   AND the subclass @AfterMethod — double execution, double screenshot attempts.
 *
 *   Fix: Removed @AfterMethod from BaseClass entirely.
 *   Screenshot capture is now handled exclusively by ScreenshotListener (ITestListener)
 *   which is registered once in testng.xml and applies to all tests without duplication.
 *   Each test class manages its own state reset in its own @AfterMethod.
 */
public class BaseClass {

    protected WebDriver     driver;
    protected WebDriverWait wait;

    protected static final String BASE_URL       = "http://localhost:8080/openmrs";
    protected static final int    WAIT_SECS      = 15;
    protected static final String SCREENSHOT_DIR = "screenshots/";

    @BeforeClass
    public void setUp() {
        System.out.println("[Setup] Initialising WebDriver...");

        // WebDriverManager — auto-downloads the correct ChromeDriver
        // No manual ChromeDriver setup needed on any machine
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--remote-allow-origins=*"); // required for Selenium 4 + Chrome 111+
        options.addArguments("--disable-dev-shm-usage");  // prevents crashes on Linux/CI
        options.addArguments("--no-sandbox");
        // options.addArguments("--headless=new"); // uncomment for headless run

        driver = new ChromeDriver(options);

        // Explicit waits only — no implicit wait
        // Mixing implicit + explicit waits causes unpredictable timeouts
        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECS));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

        new File(SCREENSHOT_DIR).mkdirs();
        driver.get(BASE_URL + "/login.htm");
        System.out.println("[Setup] Browser launched → " + BASE_URL);
    }

    // ISSUE-08 FIXED: @AfterMethod REMOVED from BaseClass.
    // Screenshots are captured by ScreenshotListener (ITestListener) registered in testng.xml.
    // Each subclass (LoginTest, PatientTest) has its own @AfterMethod for state reset.
    // This avoids double @AfterMethod execution per test.

    // alwaysRun=true — browser closes even if @BeforeClass fails
    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
            System.out.println("[Teardown] Browser closed.");
        }
    }
}
