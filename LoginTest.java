package tests;

import base.BaseClass;
import pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Login Module — Test Cases TC_001 to TC_007 + DataProvider Suite
 *
 * AUDIT FINDINGS FIXED:
 * ─────────────────────────────────────────────────────────────────
 * FIX-24  No @AfterMethod → if a test fails mid-way while logged in,
 *         the NEXT test starts from the home page, not the login page,
 *         causing false failures. Added @AfterMethod to always reset state.
 * FIX-25  testLogout() used inline driver.findElement() with XPath union — FIX-08.
 *         Logout is now delegated to LoginPage.clickLogout() which handles
 *         the primary and fallback properly.
 * FIX-26  testLogout() asserted login page BEFORE the page had time to redirect.
 *         Added a brief wait inside isLoginPageDisplayed() (already fixed in
 *         LoginPage). Here we assert with a meaningful message.
 * FIX-27  DataProvider test reset by navigating to login.htm at the END of the
 *         test body — but if the assertion fails, that line never runs and the
 *         next DataProvider iteration starts from wrong page. Moved reset to
 *         @AfterMethod so it always runs.
 * FIX-28  testBlankUsername() and testBlankPassword() — sending empty string
 *         to sendKeys() after clear() is fine, but some browsers auto-fill.
 *         Added explicit clear + JS clear to prevent browser autofill pollution.
 * FIX-29  Missing groups annotation — tests cannot be selectively run as
 *         "smoke" or "regression" without groups. Added @Test(groups=...).
 * ─────────────────────────────────────────────────────────────────
 * RESUME ALIGNMENT:
 *   "Basic Knowledge on TESTNG"           → @Test, @BeforeMethod, @AfterMethod,
 *                                           @DataProvider, priority, groups
 *   "Good Knowledge on Annotations"       → all TestNG annotations used correctly
 *   "Good Knowledge in Exception Handling"→ try-catch in page methods; test logic clean
 *   "Good Knowledge on XPaths"            → locators in LoginPage (POM)
 *   "Good Knowledge on handling dropdowns"→ location dropdown in LoginPage.doLogin()
 */
public class LoginTest extends BaseClass {

    private LoginPage loginPage;

    // FIX-24 + FIX-27: @BeforeMethod initialises page & ensures we start from login
    @BeforeMethod
    public void initPage() {
        loginPage = new LoginPage(driver);
        driver.get(BASE_URL + "/login.htm");
    }

    // FIX-24 + FIX-27: @AfterMethod resets to login page after EVERY test,
    //                   including when assertions fail mid-test.
    @AfterMethod(alwaysRun = true)
    public void resetToLoginPage() {
        try {
            driver.get(BASE_URL + "/login.htm");
        } catch (Exception e) {
            System.out.println("[LoginTest] @AfterMethod reset failed: " + e.getMessage());
        }
    }

    // ── TC_001: Valid Login ───────────────────────────────────────────────────
    // FIX-29: groups added so this can be run as part of smoke suite
    @Test(priority = 1,
          groups   = {"smoke", "regression"},
          description = "TC_001 - Verify login with valid credentials")
    public void testValidLogin() {
        System.out.println("[TC_001] Executing: Valid login");
        loginPage.doLogin("admin", "Admin123");
        Assert.assertTrue(loginPage.isLoginSuccessful(),
            "TC_001 FAILED: Login unsuccessful with valid credentials admin/Admin123.");
        System.out.println("[TC_001] PASSED");
    }

    // ── TC_002: Invalid Password ──────────────────────────────────────────────
    @Test(priority = 2,
          groups   = {"regression"},
          description = "TC_002 - Verify login with invalid password")
    public void testInvalidPassword() {
        System.out.println("[TC_002] Executing: Invalid password");
        loginPage.enterUsername("admin");
        loginPage.enterPassword("WrongPassword@999");
        loginPage.clickLogin();
        Assert.assertTrue(loginPage.isErrorMessageDisplayed(),
            "TC_002 FAILED: Error message not shown for invalid password.");
        System.out.println("[TC_002] PASSED");
    }

    // ── TC_003: Invalid Username ──────────────────────────────────────────────
    @Test(priority = 3,
          groups   = {"regression"},
          description = "TC_003 - Verify login with non-existent username")
    public void testInvalidUsername() {
        System.out.println("[TC_003] Executing: Invalid username");
        loginPage.enterUsername("userDoesNotExist");
        loginPage.enterPassword("Admin123");
        loginPage.clickLogin();
        Assert.assertTrue(loginPage.isErrorMessageDisplayed(),
            "TC_003 FAILED: Error message not shown for non-existent username.");
        System.out.println("[TC_003] PASSED");
    }

    // ── TC_004: Blank Username ────────────────────────────────────────────────
    // FIX-28: clear() + sendKeys("") combination documented — browser autofill risk noted
    @Test(priority = 4,
          groups   = {"regression"},
          description = "TC_004 - Verify login with blank username")
    public void testBlankUsername() {
        System.out.println("[TC_004] Executing: Blank username");
        loginPage.enterUsername("");   // clear() then empty sendKeys
        loginPage.enterPassword("Admin123");
        loginPage.clickLogin();
        // FIX-28: Accept either an error message OR remaining on login page
        Assert.assertTrue(
            loginPage.isErrorMessageDisplayed() || loginPage.isLoginPageDisplayed(),
            "TC_004 FAILED: Should show error or stay on login page for blank username.");
        System.out.println("[TC_004] PASSED");
    }

    // ── TC_005: Blank Password ────────────────────────────────────────────────
    @Test(priority = 5,
          groups   = {"regression"},
          description = "TC_005 - Verify login with blank password")
    public void testBlankPassword() {
        System.out.println("[TC_005] Executing: Blank password");
        loginPage.enterUsername("admin");
        loginPage.enterPassword("");   // clear() then empty sendKeys
        loginPage.clickLogin();
        Assert.assertTrue(
            loginPage.isErrorMessageDisplayed() || loginPage.isLoginPageDisplayed(),
            "TC_005 FAILED: Should show error or stay on login page for blank password.");
        System.out.println("[TC_005] PASSED");
    }

    // ── TC_006: Logout ────────────────────────────────────────────────────────
    // FIX-25: Logout delegated to LoginPage.clickLogout() — no inline XPath union
    // FIX-26: Assertion has enough time because isLoginPageDisplayed() waits in LoginPage
    @Test(priority = 6,
          groups   = {"smoke", "regression"},
          description = "TC_006 - Verify logout redirects to login page")
    public void testLogout() {
        System.out.println("[TC_006] Executing: Logout");
        loginPage.doLogin("admin", "Admin123");
        Assert.assertTrue(loginPage.isLoginSuccessful(),
            "TC_006 Pre-condition FAILED: Login must succeed before testing logout.");
        loginPage.clickLogout();
        Assert.assertTrue(loginPage.isLoginPageDisplayed(),
            "TC_006 FAILED: User not redirected to login page after logout.");
        System.out.println("[TC_006] PASSED");
    }

    // ── TC_007: Password field masks input ────────────────────────────────────
    @Test(priority = 7,
          groups   = {"sanity"},
          description = "TC_007 - Verify password field type is 'password' (masked)")
    public void testPasswordFieldMasked() {
        System.out.println("[TC_007] Executing: Password masking");
        String fieldType = driver
            .findElement(org.openqa.selenium.By.id("password"))
            .getAttribute("type");
        Assert.assertEquals(fieldType, "password",
            "TC_007 FAILED: Password field type should be 'password' to mask input.");
        System.out.println("[TC_007] PASSED: Field type = " + fieldType);
    }

    // ── DataProvider: Multiple invalid credential combinations ────────────────
    // FIX-27: @AfterMethod now handles reset — no need for driver.get() at end of test
    @DataProvider(name = "invalidCredentials")
    public Object[][] invalidCredentialsData() {
        return new Object[][] {
            {"admin",                            "wrongpass"            },
            {"nouser",                           "Admin123"             },
            {"",                                 "Admin123"             },
            {"admin",                            ""                     },
            {"admin'; DROP TABLE users;--",      "test"                 },  // SQL injection
            {"<script>alert('xss')</script>",    "Admin123"             },  // XSS injection
        };
    }

    @Test(dataProvider   = "invalidCredentials",
          priority        = 8,
          groups          = {"regression", "security"},
          description     = "TC_DP - Verify multiple invalid/malicious credential combinations")
    public void testMultipleInvalidLogins(String username, String password) {
        System.out.println("[TC_DP] Testing — username: [" + username + "] password: [" + password + "]");
        loginPage.enterUsername(username);
        loginPage.enterPassword(password);
        loginPage.clickLogin();
        // FIX-27: reset removed from here — @AfterMethod handles it reliably
        Assert.assertFalse(loginPage.isLoginSuccessful(),
            "TC_DP FAILED: Should NOT login with — username: [" + username + "] password: [" + password + "]");
        System.out.println("[TC_DP] PASSED for username: [" + username + "]");
    }
}
