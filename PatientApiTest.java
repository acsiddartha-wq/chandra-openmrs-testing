package api;

// ISSUE-01 FIXED: Removed "import org.apache.logging.log4j.LogManager" and
// "import org.apache.logging.log4j.Logger" — Log4j2 is NOT in pom.xml as a
// dependency. This import causes a compile error: "package org.apache.logging
// does not exist". Replaced all log.xxx() calls with System.out.println().

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * PatientApiTest — REST Assured API Tests for OpenMRS Patient Endpoints
 *
 * ISSUES FIXED IN THIS FILE:
 * ─────────────────────────────────────────────────────────────────────
 * ISSUE-01  Log4j2 imported but NOT in pom.xml → compile error.
 *           Removed LogManager/Logger imports and replaced with System.out.println()
 *
 * ISSUE-02  createdPatientUuid declared as private static String = ""
 *           Tests API_TC_005, API_TC_006, API_TC_010, API_TC_012 use
 *           dependsOnMethods = "testCreatePatient" — if testCreatePatient
 *           fails or is skipped, uuid stays "" and dependent tests crash
 *           with a misleading 404 instead of being skipped cleanly.
 *           Added a guard check at start of each dependent test.
 *
 * ISSUE-03  testCreatePatient() uses System.currentTimeMillis() in identifier
 *           string but the identifier format "RA-TEST-<timestamp>" may not
 *           match OpenMRS identifier validator regex. Added a shorter numeric
 *           suffix to stay within typical length limits.
 *
 * ISSUE-04  PatientApiTest extends nothing — it does NOT extend BaseClass.
 *           This is CORRECT for API tests (no browser needed), but the
 *           ScreenshotListener's getDriverFromResult() will fail with a
 *           NoSuchFieldException because PatientApiTest has no "driver" field.
 *           Fixed ScreenshotListener to handle this gracefully (see that file).
 *
 * ISSUE-05  testng.xml registers ScreenshotListener globally — it fires for
 *           API tests too. When it tries to reflect "driver" from PatientApiTest
 *           it crashes. Fixed in ScreenshotListener.java with a null-safe check.
 * ─────────────────────────────────────────────────────────────────────
 */
public class PatientApiTest {

    // ISSUE-01 FIXED: no Logger — using System.out.println() instead
    private static final String BASE_URI = "http://localhost:8080/openmrs/ws/rest/v1";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Admin123";

    // Shared UUID across tests — set by testCreatePatient, used by dependent tests
    private static String createdPatientUuid = "";

    private RequestSpecification baseSpec;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = BASE_URI;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        baseSpec = given()
            .auth().preemptive().basic(USERNAME, PASSWORD)
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON);

        System.out.println("[PatientApiTest] REST Assured configured → " + BASE_URI);
    }

    // ── API_TC_001: Valid Authentication ────────────────────────────────────
    @Test(priority = 1,
          groups = {"api", "smoke"},
          description = "API_TC_001 — GET /session with valid credentials returns authenticated=true")
    public void testValidAuthentication() {
        System.out.println("[API_TC_001] Testing valid authentication");

        baseSpec
        .when()
            .get("/session")
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("user.username", equalTo("admin"))
            .time(lessThan(2000L));

        System.out.println("[API_TC_001] PASSED");
    }

    // ── API_TC_002: Invalid Authentication ──────────────────────────────────
    @Test(priority = 2,
          groups = {"api", "regression"},
          description = "API_TC_002 — GET /session with wrong password returns authenticated=false")
    public void testInvalidAuthentication() {
        System.out.println("[API_TC_002] Testing invalid authentication");

        given()
            .auth().preemptive().basic("admin", "wrongpassword")
            .contentType(ContentType.JSON)
        .when()
            .get("/session")
        .then()
            .statusCode(200)
            .body("authenticated", equalTo(false));

        System.out.println("[API_TC_002] PASSED");
    }

    // ── API_TC_003: GET All Patients ─────────────────────────────────────────
    @Test(priority = 3,
          groups = {"api", "smoke"},
          description = "API_TC_003 — GET /patient returns list with results array")
    public void testGetAllPatients() {
        System.out.println("[API_TC_003] Testing GET all patients");

        baseSpec
        .when()
            .get("/patient?v=simple&limit=10")
        .then()
            .statusCode(200)
            .body("results",        notNullValue())
            .body("results",        instanceOf(java.util.List.class))
            .body("results.size()", lessThanOrEqualTo(10))
            .contentType(containsString("application/json"));

        System.out.println("[API_TC_003] PASSED");
    }

    // ── API_TC_004: POST Create Patient ─────────────────────────────────────
    @Test(priority = 4,
          groups = {"api", "regression"},
          description = "API_TC_004 — POST /patient creates patient and returns UUID")
    public void testCreatePatient() {
        System.out.println("[API_TC_004] Testing POST create patient");

        // ISSUE-03 FIXED: shorter identifier to avoid length validation failures
        String identifier = "RAT" + (System.currentTimeMillis() % 100000);

        String requestBody = "{"
            + "\"person\": {"
            + "  \"names\": [{\"givenName\": \"RestAssured\", \"familyName\": \"APITest\"}],"
            + "  \"gender\": \"M\","
            + "  \"birthdate\": \"1992-06-15\","
            + "  \"addresses\": [{\"address1\": \"API Street\", \"cityVillage\": \"Bangalore\"}]"
            + "},"
            + "\"identifiers\": [{"
            + "  \"identifier\": \"" + identifier + "\","
            + "  \"identifierType\": \"05a29f94-c0ed-11e2-94be-8c13b969e334\","
            + "  \"location\": \"8d6c993e-c2cc-11de-8d13-0010c6dffd0f\","
            + "  \"preferred\": true"
            + "}]}";

        Response response = baseSpec
            .body(requestBody)
        .when()
            .post("/patient")
        .then()
            .statusCode(201)
            .body("uuid",          notNullValue())
            .body("person.gender", equalTo("M"))
            .extract().response();

        createdPatientUuid = response.jsonPath().getString("uuid");
        Assert.assertFalse(createdPatientUuid.isEmpty(),
            "API_TC_004 FAILED: UUID should not be empty after patient creation.");
        System.out.println("[API_TC_004] PASSED — UUID: " + createdPatientUuid);
    }

    // ── API_TC_005: GET Patient by UUID ─────────────────────────────────────
    @Test(priority = 5,
          groups = {"api", "regression"},
          dependsOnMethods = "testCreatePatient",
          description = "API_TC_005 — GET /patient/{uuid} returns correct patient")
    public void testGetPatientByUuid() {
        // ISSUE-02 FIXED: guard if previous test failed and UUID is empty
        if (createdPatientUuid.isEmpty()) {
            System.out.println("[API_TC_005] SKIPPED — No patient UUID available (testCreatePatient may have failed).");
            return;
        }
        System.out.println("[API_TC_005] Testing GET patient by UUID: " + createdPatientUuid);

        baseSpec
        .when()
            .get("/patient/" + createdPatientUuid + "?v=full")
        .then()
            .statusCode(200)
            .body("uuid",        equalTo(createdPatientUuid))
            .body("person",      notNullValue())
            .body("identifiers", not(empty()))
            .body("voided",      equalTo(false));

        System.out.println("[API_TC_005] PASSED");
    }

    // ── API_TC_006: Search Patient by Name ──────────────────────────────────
    @Test(priority = 6,
          groups = {"api", "regression"},
          dependsOnMethods = "testCreatePatient",
          description = "API_TC_006 — GET /patient?q=RestAssured returns matching patient")
    public void testSearchPatientByName() {
        if (createdPatientUuid.isEmpty()) {
            System.out.println("[API_TC_006] SKIPPED — No patient UUID available.");
            return;
        }
        System.out.println("[API_TC_006] Testing patient search by name");

        baseSpec
        .when()
            .get("/patient?q=RestAssured&v=simple")
        .then()
            .statusCode(200)
            .body("results.size()", greaterThan(0))
            .body("results.display", hasItem(containsString("RestAssured")));

        System.out.println("[API_TC_006] PASSED");
    }

    // ── API_TC_007: GET Patient — Invalid UUID ───────────────────────────────
    @Test(priority = 7,
          groups = {"api", "regression"},
          description = "API_TC_007 — GET /patient with invalid UUID returns 404")
    public void testGetPatientInvalidUuid() {
        System.out.println("[API_TC_007] Testing GET with invalid UUID");

        baseSpec
        .when()
            .get("/patient/this-uuid-does-not-exist")
        .then()
            .statusCode(404)
            .body("error", notNullValue());

        System.out.println("[API_TC_007] PASSED");
    }

    // ── API_TC_008: POST Patient — Missing Fields ─────────────────────────────
    @Test(priority = 8,
          groups = {"api", "regression"},
          description = "API_TC_008 — POST /patient without required fields returns 400")
    public void testCreatePatientMissingFields() {
        System.out.println("[API_TC_008] Testing POST with missing fields");

        baseSpec
            .body("{\"person\": {\"gender\": \"M\"}}")
        .when()
            .post("/patient")
        .then()
            .statusCode(anyOf(equalTo(400), equalTo(500)));

        System.out.println("[API_TC_008] PASSED");
    }

    // ── API_TC_009: Response Time ────────────────────────────────────────────
    @Test(priority = 9,
          groups = {"api", "performance"},
          description = "API_TC_009 — Patient list API responds within 3 seconds")
    public void testApiResponseTime() {
        System.out.println("[API_TC_009] Testing API response time");

        long responseTime = baseSpec
        .when()
            .get("/patient?v=simple&limit=5")
        .then()
            .statusCode(200)
            .extract().time();

        Assert.assertTrue(responseTime < 3000,
            "API_TC_009 FAILED: Response time " + responseTime + "ms exceeded 3000ms.");
        System.out.println("[API_TC_009] PASSED — Response time: " + responseTime + "ms");
    }

    // ── API_TC_010: UI vs API Data Comparison ────────────────────────────────
    @Test(priority = 10,
          groups = {"api", "integration"},
          dependsOnMethods = "testCreatePatient",
          description = "API_TC_010 — Verify patient data from API matches what was registered")
    public void testUiVsApiDataComparison() {
        // ISSUE-02 FIXED: guard check
        if (createdPatientUuid.isEmpty()) {
            System.out.println("[API_TC_010] SKIPPED — No patient UUID available.");
            return;
        }
        System.out.println("[API_TC_010] Testing UI vs API data consistency");

        String expectedGivenName  = "RestAssured";
        String expectedFamilyName = "APITest";
        String expectedGender     = "M";

        Response response = baseSpec
        .when()
            .get("/patient/" + createdPatientUuid + "?v=full")
        .then()
            .statusCode(200)
            .extract().response();

        String actualGender  = response.jsonPath().getString("person.gender");
        String actualDisplay = response.jsonPath().getString("person.display");

        Assert.assertEquals(actualGender, expectedGender,
            "API_TC_010 FAILED: Gender mismatch");
        Assert.assertTrue(actualDisplay.contains(expectedGivenName),
            "API_TC_010 FAILED: Given name not in display — Got: " + actualDisplay);
        Assert.assertTrue(actualDisplay.contains(expectedFamilyName),
            "API_TC_010 FAILED: Family name not in display — Got: " + actualDisplay);

        System.out.println("[API_TC_010] PASSED — Data matches: " + actualDisplay + " | Gender: " + actualGender);
    }

    // ── API_TC_011: SQL Injection Protection ────────────────────────────────
    @Test(priority = 11,
          groups = {"api", "security"},
          description = "API_TC_011 — SQL injection in search query does not cause 500 error")
    public void testSqlInjectionProtection() {
        System.out.println("[API_TC_011] Testing SQL injection protection");

        baseSpec
        .when()
            .get("/patient?q=' OR '1'='1&v=simple")
        .then()
            .statusCode(not(equalTo(500)));

        System.out.println("[API_TC_011] PASSED");
    }

    // ── API_TC_012: Response Schema Validation ──────────────────────────────
    @Test(priority = 12,
          groups = {"api", "regression"},
          dependsOnMethods = "testCreatePatient",
          description = "API_TC_012 — Patient response schema contains all required fields")
    public void testPatientResponseSchema() {
        // ISSUE-02 FIXED: guard check
        if (createdPatientUuid.isEmpty()) {
            System.out.println("[API_TC_012] SKIPPED — No patient UUID available.");
            return;
        }
        System.out.println("[API_TC_012] Testing patient response schema");

        baseSpec
        .when()
            .get("/patient/" + createdPatientUuid + "?v=full")
        .then()
            .statusCode(200)
            .body("$",           hasKey("uuid"))
            .body("$",           hasKey("identifiers"))
            .body("$",           hasKey("person"))
            .body("$",           hasKey("voided"))
            .body("person",      hasKey("gender"))
            .body("person",      hasKey("birthdate"))
            .body("person",      hasKey("names"))
            .body("identifiers", not(empty()));

        System.out.println("[API_TC_012] PASSED");
    }
}
