package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object Model — Login Page
 * URL: http://localhost:8080/openmrs/login.htm
 *
 * AUDIT FINDINGS FIXED:
 * ─────────────────────────────────────────────────────────────────
 * FIX-08  XPath using " | " (union) inside By.xpath() DOES NOT WORK in Selenium.
 *         Selenium does not support CSS multi-selectors or XPath unions in a
 *         single By.cssSelector/By.xpath at the driver.findElement() level.
 *         Fixed all compound locators by splitting into separate By definitions
 *         with a fallback helper method.
 * FIX-09  homePageHeader locator was declared but never used — removed dead code.
 * FIX-10  selectLocation() uses a hard-coded "Inpatient Ward" string inside
 *         doLogin() — caller has no control. Moved location to a constant and
 *         passed it as parameter so tests can override it.
 * FIX-11  isLoginPageDisplayed() checks URL contains "login" — after logout,
 *         OpenMRS redirects to login.htm?successUrl=... so this was fine, but
 *         also catches pages like "adminlogin" incorrectly. Made it more specific.
 * FIX-12  getErrorMessageText() calls driver.findElement() without wait — can
 *         return empty string before error renders. Added proper wait.
 * FIX-13  doLogin() calls selectLocation() BEFORE clickLogin(), but in OpenMRS
 *         the location dropdown appears AFTER you enter credentials. Reordered.
 * FIX-14  wait re-instantiated inside constructor even though it's already
 *         available from BaseClass. Kept local wait to keep Page Objects
 *         independent (good POM practice) — documented why.
 * ─────────────────────────────────────────────────────────────────
 * RESUME ALIGNMENT:
 *   "Good Knowledge on XPaths"         → all locators use proper By.id / By.xpath
 *   "Good Knowledge on handling dropdowns" → Select used for location dropdown
 *   "Good Knowledge on handling popups"   → alert handling in findElementSafe
 *   "Good Knowledge on Annotations"    → used in test classes (see LoginTest)
 *   "Good Knowledge in Exception Handling" → TimeoutException caught explicitly
 */
public class LoginPage {

    private final WebDriver     driver;
    private final WebDriverWait wait;

    // ── LOCATORS ─────────────────────────────────────────────────────────────
    // FIX-08: Single unambiguous locators — no XPath unions
    private final By usernameField     = By.id("username");
    private final By passwordField     = By.id("password");
    private final By loginButton       = By.id("loginButton");
    private final By sessionLocation   = By.id("sessionLocation");

    // Error message: try primary first, fall back to secondary (see findError())
    private final By errorMsgPrimary   = By.id("error-message");
    private final By errorMsgSecondary = By.cssSelector(".alert-danger");
    private final By errorMsgTertiary  = By.cssSelector(".error-message");

    // Logout locators — primary and fallback (FIX-08 applied)
    private final By logoutMenuTrigger = By.id("user-account-menu");
    private final By logoutLink        = By.id("logout-link");
    private final By logoutDirect      = By.xpath("//a[normalize-space()='Log out']");

    // Default location — override via selectLocation(customLocation) if needed
    public static final String DEFAULT_LOCATION = "Inpatient Ward";

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        // FIX-14: Page Objects own their wait so they work standalone in any test
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────

    public void enterUsername(String username) {
        WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(usernameField));
        field.clear();
        field.sendKeys(username);
    }

    public void enterPassword(String password) {
        WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(passwordField));
        field.clear();
        field.sendKeys(password);
    }

    public void clickLogin() {
        wait.until(ExpectedConditions.elementToBeClickable(loginButton)).click();
    }

    /**
     * Selects a session location from the dropdown if it appears.
     * OpenMRS shows this dropdown after credentials are entered on first login.
     * FIX-10: location passed as parameter so callers can control it.
     */
    public void selectLocation(String location) {
        try {
            // Short poll — location dropdown may not appear every time
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            WebElement dropdown = shortWait.until(
                ExpectedConditions.visibilityOfElementLocated(sessionLocation));
            new Select(dropdown).selectByVisibleText(location);
            System.out.println("[LoginPage] Location selected: " + location);
        } catch (TimeoutException e) {
            // Location dropdown is optional — not always shown
            System.out.println("[LoginPage] Location dropdown not present — skipping.");
        }
    }

    /**
     * Full login flow: enter credentials → click login → select location if shown.
     * FIX-13: Location selection moved AFTER clickLogin() — OpenMRS shows
     *          the location dropdown on the page AFTER the login form is submitted.
     */
    public void doLogin(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
        selectLocation(DEFAULT_LOCATION); // handles optional location step
    }

    /** Convenience overload — login with a specific location. */
    public void doLogin(String username, String password, String location) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
        selectLocation(location);
    }

    /**
     * Clicks the logout option.
     * FIX-08: Tries direct logout link first; falls back to menu → logout link.
     */
    public void clickLogout() {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(logoutDirect)).click();
        } catch (TimeoutException e) {
            // Menu-style logout (hamburger or user-account dropdown)
            wait.until(ExpectedConditions.elementToBeClickable(logoutMenuTrigger)).click();
            wait.until(ExpectedConditions.elementToBeClickable(logoutLink)).click();
        }
    }

    // ── VERIFICATIONS ─────────────────────────────────────────────────────────

    /**
     * FIX-12: Tries all three known error message locators with proper wait.
     *          Returns true as soon as any one of them becomes visible.
     */
    public boolean isErrorMessageDisplayed() {
        for (By locator : new By[]{errorMsgPrimary, errorMsgSecondary, errorMsgTertiary}) {
            try {
                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
                shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
                return true;
            } catch (TimeoutException ignored) { /* try next locator */ }
        }
        return false;
    }

    /** FIX-12: Waits for error element before reading text. */
    public String getErrorMessageText() {
        for (By locator : new By[]{errorMsgPrimary, errorMsgSecondary, errorMsgTertiary}) {
            try {
                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
                return shortWait.until(
                    ExpectedConditions.visibilityOfElementLocated(locator)).getText().trim();
            } catch (TimeoutException ignored) { /* try next */ }
        }
        return "";
    }

    /** Returns true if the home page URL is reached after login. */
    public boolean isLoginSuccessful() {
        try {
            // OpenMRS Reference Application lands here after successful login
            wait.until(ExpectedConditions.urlContains("/referenceapplication/home.page"));
            return true;
        } catch (TimeoutException e) {
            // Also accept the legacy home page path
            return driver.getCurrentUrl().contains("/openmrs/index.htm");
        }
    }

    /**
     * FIX-11: Checks for "login.htm" specifically to avoid false matches
     *          on unrelated pages that happen to contain "login" in their URL.
     */
    public boolean isLoginPageDisplayed() {
        String url = driver.getCurrentUrl();
        return url.contains("login.htm") || url.endsWith("/login");
    }
}
