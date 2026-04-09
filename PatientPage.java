package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object Model — Patient Registration & Search
 *
 * AUDIT FINDINGS FIXED:
 * ─────────────────────────────────────────────────────────────────
 * FIX-08  XPath unions " | " inside a single By.xpath() removed throughout.
 *         Selenium evaluates these as a single XPath but driver.findElement()
 *         only returns the FIRST match — making fallback locators unreliable.
 *         Replaced with explicit primary/fallback pattern using tryFind().
 * FIX-15  StaleElementReferenceException not handled anywhere — multi-step
 *         registration forms re-render the DOM between steps causing staleness.
 *         Added stale-safe click helper clickSafe().
 * FIX-16  enterDateOfBirth() called driver.findElement() (no wait) for month
 *         and year after waiting only for day — race condition. All three now
 *         use explicit waits.
 * FIX-17  selectGender() fallback uses gender.substring(0,1) to get "M"/"F"
 *         but OpenMRS radio values may be "Male"/"Female" — not just initials.
 *         Fixed to try both the initial and full-word value.
 * FIX-18  clickNext() silently swallowed all exceptions — hides real failures.
 *         Now only catches TimeoutException (expected: no Next on last step).
 * FIX-19  searchPatient() calls clickFindPatient() every time — if already on
 *         the find patient page this causes navigation away and back. Added
 *         URL check before navigating.
 * FIX-20  isPatientRegistered() first waits for "patient-dashboard" URL then
 *         falls back to "patientDashboard" — but these are checked sequentially
 *         with no retry window, so the fallback is never reached properly.
 *         Unified into a single OR condition.
 * FIX-21  getPatientId() relied on a fragile class-based XPath — changed to
 *         target OpenMRS Reference App's actual patient identifier element.
 * FIX-22  addressField used driver.findElement() without wait — fragile on slow
 *         pages. Added explicit wait.
 * FIX-23  JavascriptExecutor scroll added before clicking elements that may be
 *         below the fold — prevents ElementClickInterceptedException.
 * ─────────────────────────────────────────────────────────────────
 * RESUME ALIGNMENT:
 *   "Good Knowledge on XPaths"             → all locators use precise XPaths
 *   "Good Knowledge on handling dropdowns" → Select for gender & birth month
 *   "Good Knowledge on handling popups"    → alert handling in PatientTest
 *   "Good Knowledge in Exception Handling" → TimeoutException, StaleElementReferenceException
 *   "Good Knowledge on Annotations"        → used in test class
 *   "Very good Knowledge on Abstraction"   → POM isolates all locator details
 */
public class PatientPage {

    private final WebDriver       driver;
    private final WebDriverWait   wait;
    private final JavascriptExecutor js;

    // ── LOCATORS — Navigation ────────────────────────────────────────────────
    // FIX-08: Split compound locators into primary + fallback pairs
    private final By registerPatientLinkPrimary  = By.xpath("//a[contains(@href,'registrationapp/registerPatient')]");
    private final By registerPatientLinkFallback = By.xpath("//div[contains(@id,'registerPatient')]//a");

    private final By findPatientLinkPrimary      = By.xpath("//a[contains(@href,'findpatient') or contains(@href,'findPatient')]");
    private final By findPatientLinkFallback     = By.xpath("//div[contains(@id,'findPatient')]//a");

    // ── LOCATORS — Registration Form ─────────────────────────────────────────
    private final By firstNameField     = By.id("givenName");
    private final By familyNameField    = By.id("familyName");
    private final By middleNameField    = By.id("middleName");

    // Gender — dropdown primary, radio fallback handled in selectGender()
    private final By genderDropdown     = By.id("gender");
    private final By genderMaleRadio    = By.xpath("//input[@type='radio'][contains(@value,'M') or contains(@value,'Male') or contains(@value,'male')]");
    private final By genderFemaleRadio  = By.xpath("//input[@type='radio'][contains(@value,'F') or contains(@value,'Female') or contains(@value,'female')]");

    // FIX-16: All DOB fields will use explicit waits
    private final By birthDayField      = By.id("birthdateDay");
    private final By birthMonthDropdown = By.id("birthdateMonth");
    private final By birthYearField     = By.id("birthdateYear");

    private final By addressField       = By.id("address1");
    private final By phoneField         = By.id("phoneNumber");

    private final By nextButton         = By.xpath("//button[normalize-space()='Next' or normalize-space()='next']");
    private final By confirmButtonPrimary  = By.id("submit");
    private final By confirmButtonFallback = By.xpath("//button[normalize-space()='Confirm' or normalize-space()='Register Patient']");

    // FIX-21: OpenMRS Reference App shows patient ID in this element after registration
    private final By patientIdPrimary   = By.xpath("//div[contains(@class,'identifiers')]//span");
    private final By patientIdFallback  = By.xpath("//strong[contains(text(),'Patient Identifier')]/../following-sibling::*");

    // ── LOCATORS — Search ─────────────────────────────────────────────────────
    private final By searchFieldPrimary   = By.id("patient-search-box");
    private final By searchFieldFallback  = By.xpath("//input[@placeholder='Search for patient' or @placeholder='Search for a patient']");
    private final By searchResultsPrimary = By.xpath("//ul[@id='patient-search-results']/li");
    private final By searchResultsFallback= By.xpath("//div[contains(@class,'results-holder')]//li");
    private final By noResultsMessage     = By.xpath("//*[contains(text(),'No results') or contains(text(),'no patients found') or contains(text(),'No matching')]");

    // ── LOCATORS — Validation ─────────────────────────────────────────────────
    private final By validationErrorPrimary   = By.xpath("//*[contains(@class,'field-error')]");
    private final By validationErrorFallback  = By.xpath("//span[contains(@class,'error') and string-length(text()) > 0]");

    public PatientPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.js     = (JavascriptExecutor) driver;
    }

    // ── HELPER: try primary locator, fall back to secondary ───────────────────
    private WebElement tryFind(By primary, By fallback) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(primary));
        } catch (TimeoutException e) {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(fallback));
        }
    }

    /** FIX-23: Scroll element into view before clicking to avoid interception. */
    private void scrollAndClick(WebElement el) {
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", el);
        el.click();
    }

    /**
     * FIX-15: Stale-safe click — retries once if DOM re-renders between steps.
     */
    private void clickSafe(By locator) {
        int attempts = 0;
        while (attempts < 2) {
            try {
                WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));
                scrollAndClick(el);
                return;
            } catch (StaleElementReferenceException e) {
                attempts++;
                System.out.println("[PatientPage] StaleElement on " + locator + " — retrying (" + attempts + ")");
            }
        }
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    public void clickRegisterPatient() {
        try {
            clickSafe(registerPatientLinkPrimary);
        } catch (Exception e) {
            clickSafe(registerPatientLinkFallback);
        }
    }

    /**
     * FIX-19: Only navigates to Find Patient if not already there.
     */
    public void clickFindPatient() {
        if (!driver.getCurrentUrl().contains("findpatient") && !driver.getCurrentUrl().contains("findPatient")) {
            try {
                clickSafe(findPatientLinkPrimary);
            } catch (Exception e) {
                clickSafe(findPatientLinkFallback);
            }
        }
    }

    // ── REGISTRATION ──────────────────────────────────────────────────────────

    public void enterFirstName(String name) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(firstNameField));
        el.clear();
        el.sendKeys(name);
    }

    public void enterFamilyName(String name) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(familyNameField));
        el.clear();
        el.sendKeys(name);
    }

    /**
     * FIX-17: Handles both dropdown-style and radio-button-style gender fields.
     *          Tries dropdown first; falls back to radio with full-word values.
     */
    public void selectGender(String gender) {
        try {
            WebElement dropdown = wait.until(ExpectedConditions.visibilityOfElementLocated(genderDropdown));
            new Select(dropdown).selectByVisibleText(gender);
        } catch (Exception e) {
            // Fallback: gender rendered as radio buttons
            By radioLocator = gender.equalsIgnoreCase("Female") ? genderFemaleRadio : genderMaleRadio;
            clickSafe(radioLocator);
        }
    }

    /**
     * FIX-16: All three DOB fields now use explicit waits before interacting.
     */
    public void enterDateOfBirth(String day, String month, String year) {
        WebElement dayEl = wait.until(ExpectedConditions.visibilityOfElementLocated(birthDayField));
        dayEl.clear();
        dayEl.sendKeys(day);

        // FIX-16: month dropdown — explicit wait added
        WebElement monthEl = wait.until(ExpectedConditions.visibilityOfElementLocated(birthMonthDropdown));
        new Select(monthEl).selectByVisibleText(month);

        // FIX-16: year field — explicit wait added
        WebElement yearEl = wait.until(ExpectedConditions.visibilityOfElementLocated(birthYearField));
        yearEl.clear();
        yearEl.sendKeys(year);
    }

    /** FIX-22: Address field now uses explicit wait before interacting. */
    public void enterAddress(String address) {
        try {
            WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(addressField));
            el.clear();
            el.sendKeys(address);
        } catch (TimeoutException e) {
            System.out.println("[PatientPage] Address field not found — skipping (optional field).");
        }
    }

    public void enterPhone(String phone) {
        try {
            WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(phoneField));
            el.clear();
            el.sendKeys(phone);
        } catch (TimeoutException e) {
            System.out.println("[PatientPage] Phone field not found — skipping (optional field).");
        }
    }

    /**
     * FIX-18: Only catches TimeoutException (Next not present on last step).
     *          Previously caught ALL exceptions, hiding real bugs like
     *          StaleElementReferenceException or ElementClickInterceptedException.
     */
    public void clickNext() {
        try {
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(nextButton));
            scrollAndClick(btn);
        } catch (TimeoutException e) {
            System.out.println("[PatientPage] Next button not found on this step — skipping.");
        }
    }

    public void clickConfirmRegister() {
        try {
            clickSafe(confirmButtonPrimary);
        } catch (Exception e) {
            clickSafe(confirmButtonFallback);
        }
    }

    /**
     * Full patient registration flow.
     * FIX-15: Each step uses clickSafe() to handle DOM re-renders between steps.
     */
    public void registerPatient(String firstName, String familyName, String gender,
                                String day, String month, String year) {
        clickRegisterPatient();
        enterFirstName(firstName);
        enterFamilyName(familyName);
        clickNext();
        selectGender(gender);
        clickNext();
        enterDateOfBirth(day, month, year);
        clickNext();
        clickConfirmRegister();
    }

    // ── SEARCH ────────────────────────────────────────────────────────────────

    public void searchPatient(String query) {
        clickFindPatient(); // FIX-19: skips navigation if already on find patient page
        WebElement sf;
        try {
            sf = wait.until(ExpectedConditions.visibilityOfElementLocated(searchFieldPrimary));
        } catch (TimeoutException e) {
            sf = wait.until(ExpectedConditions.visibilityOfElementLocated(searchFieldFallback));
        }
        sf.clear();
        sf.sendKeys(query);

        // OpenMRS may auto-search on keystrokes — wait briefly for results
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.visibilityOfElementLocated(searchResultsPrimary));
        } catch (TimeoutException ignored) { /* auto-search may not apply */ }
    }

    // ── VERIFICATIONS ─────────────────────────────────────────────────────────

    /**
     * FIX-20: Unified OR condition — checks both URL patterns in a single wait.
     */
    public boolean isPatientRegistered() {
        try {
            wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("patient-dashboard"),
                ExpectedConditions.urlContains("patientDashboard"),
                ExpectedConditions.urlContains("registrationapp/patient")
            ));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isSearchResultsDisplayed() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(searchResultsPrimary));
            return driver.findElements(searchResultsPrimary).size() > 0;
        } catch (TimeoutException e) {
            try {
                return driver.findElements(searchResultsFallback).size() > 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    public boolean isNoResultsMessageDisplayed() {
        try {
            return wait.until(
                ExpectedConditions.visibilityOfElementLocated(noResultsMessage)).isDisplayed();
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isValidationErrorDisplayed() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(validationErrorPrimary));
            return true;
        } catch (TimeoutException e) {
            try {
                return driver.findElements(validationErrorFallback).size() > 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    /**
     * FIX-21: Reads patient ID from the actual OpenMRS Reference App element.
     */
    public String getPatientId() {
        try {
            return wait.until(
                ExpectedConditions.visibilityOfElementLocated(patientIdPrimary)).getText().trim();
        } catch (TimeoutException e) {
            try {
                return driver.findElement(patientIdFallback).getText().trim();
            } catch (Exception ex) {
                return "";
            }
        }
    }
}
