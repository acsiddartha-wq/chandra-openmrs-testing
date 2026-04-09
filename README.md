# 🏥 OpenMRS Healthcare Testing Project

**Author:** Chandra Siddartha | **Email:** acsiddartha@gmail.com
**App Under Test:** OpenMRS — Open Medical Record System | **Target:** Optum

## Project Overview
End-to-end QA project testing OpenMRS, a real-world healthcare app used in hospitals globally.
Covers Manual Testing, Selenium Automation, Database Testing, and Agile/SDLC knowledge.

## Tech Stack
| Tool | Purpose |
|------|---------|
| Selenium 4 + Java | UI Automation |
| TestNG 7 | Test Framework — Annotations, DataProvider |
| Maven | Build & dependency management |
| Apache POI | Excel reading for data-driven tests |
| MySQL | Database testing with SQL queries |
| Excel | Test cases, bug reports, RTM |

## Project Structure
```
chandra-openmrs-testing/
├── manual-testing/
│   └── TestCases_BugReport_RTM.xlsx   (5 sheets: TestPlan, 25 TCs, 8 Bugs, RTM, Summary)
├── automation/
│   ├── pom.xml
│   ├── testng.xml
│   └── src/test/java/
│       ├── base/BaseClass.java         (WebDriver setup/teardown)
│       ├── pages/LoginPage.java        (XPaths, actions)
│       ├── pages/PatientPage.java      (Dropdowns, popups)
│       ├── tests/LoginTest.java        (TC_001-TC_005 + DataProvider)
│       ├── tests/PatientTest.java      (TC_006-TC_017 + popup handling)
│       └── utils/ExcelReader.java      (Apache POI data reader)
├── database-testing/
│   └── SQLQueries.sql                 (22 queries across 6 modules)
├── agile-concepts/
│   └── Agile_SDLC_QA_Guide.docx      (STLC, Agile, Defect Lifecycle, Interview Q&A)
└── README.md
```

## Run Tests
```bash
cd automation
mvn test                          # Full suite
mvn test -Dtest=LoginTest         # Only login tests
mvn test -Dtest=PatientTest       # Only patient tests
```

## Coverage Summary
| Module | TCs | Bugs |
|--------|-----|------|
| Login | 5 | 2 |
| Patient Registration | 5 | 3 |
| Patient Search | 4 | 1 |
| Update Patient | 3 | 1 |
| Appointment | 4 | 1 |
| Role & UI | 4 | 0 |
| **Total** | **25** | **8** |

## Interview One-Liner
> "I tested OpenMRS, a real-world open-source healthcare application, building a complete test suite with 25 manual test cases, Selenium automation using Java and TestNG, 22 SQL database validation queries, and full Agile/STLC documentation."

---

## 🌐 API Testing — Postman Collection

**File:** `API-Testing/OpenMRS_API_Tests.postman_collection.json`
**Base URL:** `http://localhost:8080/openmrs/ws/rest/v1`
**Auth:** Basic Auth — `admin / Admin123`

### Import & Run
1. Open Postman → Import → select the `.json` file
2. Collection variables are pre-set (base_url, username, password)
3. Run the full collection or individual folders

### API Test Coverage — 35 Tests across 9 Folders

| Folder | APIs Covered | Count |
|--------|-------------|-------|
| 01 — Authentication | Session, Invalid Auth, No Auth | 3 |
| 02 — Patient API | GET All, POST Create, GET by UUID, Search, Invalid UUID, Duplicate, DELETE | 8 |
| 03 — Person API | GET All, POST Create, GET by UUID | 3 |
| 04 — Visit API | GET All, POST Create, GET by UUID, End Visit, GET by Patient | 5 |
| 05 — Encounter API | GET All, POST Create, GET by UUID | 3 |
| 06 — User API | GET All Users, GET Current User | 2 |
| 07 — Concept API | GET All, Search by Name | 2 |
| 08 — Schema Validation | Schema fields, Headers, Pagination, v=ref vs v=full | 4 |
| 09 — Negative & Edge Cases | Invalid endpoint, Empty body, SQL injection, XSS, Voided | 5 |
| **Total** | | **35** |

### Key Features
- **Auto-saved variables** — Patient UUID, Visit UUID, Encounter UUID passed between requests
- **Chained tests** — POST creates resource → UUID saved → GET uses it → DELETE cleans up
- **Security tests** — SQL injection and XSS payload tests included
- **Schema validation** — Response structure and data types verified
- **Newman CLI run** — `newman run OpenMRS_API_Tests.postman_collection.json`
