package utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Utility — Read test data from Excel files for TestNG @DataProvider.
 *
 * AUDIT FINDINGS FIXED:
 * ─────────────────────────────────────────────────────────────────
 * FIX-39  FileInputStream and Workbook were never closed in the original code
 *         if an exception was thrown mid-read — resource leak. Replaced with
 *         try-with-resources which guarantees closure.
 * FIX-40  getLastRowNum() returns LAST ROW INDEX (0-based), not row count.
 *         Loop was iterating correctly by chance (i <= rowCount skips header
 *         and coincidentally matches). Clarified with rowCount = lastRowNum.
 * FIX-41  No null check for entire row — if an Excel sheet has empty rows at
 *         the bottom, row.getCell(j) throws NullPointerException. Added row
 *         null guard.
 * FIX-42  getCellValue() had no case for BLANK cell type — returns "" now
 *         via default, but BLANK is a distinct type in POI. Added explicitly.
 * FIX-43  No validation that the sheet name exists — returns NullPointerException
 *         if wrong sheet name passed. Added descriptive error.
 * ─────────────────────────────────────────────────────────────────
 * RESUME ALIGNMENT:
 *   "Good Knowledge in Exception Handling" → IOException handled, NPE prevented,
 *                                           descriptive error messages added
 *   "Good Knowledge on OOPS"               → utility class with static method
 *   "Basic Knowledge on TESTNG"            → used with @DataProvider in tests
 */
public class ExcelReader {

    /**
     * Reads all data rows from a given sheet (skips header row 0).
     *
     * @param filePath  Absolute or relative path to the .xlsx file
     * @param sheetName Name of the sheet to read
     * @return 2D Object array suitable for TestNG @DataProvider
     * @throws IOException if the file cannot be read
     */
    public static Object[][] getData(String filePath, String sheetName) throws IOException {

        // FIX-39: try-with-resources ensures stream + workbook always closed
        try (FileInputStream fis      = new FileInputStream(filePath);
             Workbook         workbook = new XSSFWorkbook(fis)) {

            // FIX-43: validate sheet exists before proceeding
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException(
                    "Sheet '" + sheetName + "' not found in file: " + filePath +
                    ". Available sheets: " + getSheetNames(workbook));
            }

            // FIX-40: getLastRowNum() is 0-based index of last row
            int lastRow  = sheet.getLastRowNum();  // e.g. 5 means rows 0..5
            int colCount = sheet.getRow(0).getLastCellNum();

            // Data starts at row 1 (row 0 = header), so dataRows = lastRow
            Object[][] data = new Object[lastRow][colCount];

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);

                // FIX-41: guard against completely empty rows
                if (row == null) {
                    for (int j = 0; j < colCount; j++) {
                        data[i - 1][j] = "";
                    }
                    continue;
                }

                for (int j = 0; j < colCount; j++) {
                    data[i - 1][j] = getCellValue(row.getCell(j));
                }
            }
            return data;
        }
    }

    /**
     * Safely reads a cell value regardless of its type.
     * FIX-42: Added explicit BLANK case.
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC:
                // Distinguish between integer and decimal values
                double num = cell.getNumericCellValue();
                return (num == Math.floor(num))
                    ? String.valueOf((long) num)   // e.g. 42.0 → "42"
                    : String.valueOf(num);          // e.g. 3.14 → "3.14"
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            case BLANK:   return "";               // FIX-42: explicit BLANK handling
            default:      return "";
        }
    }

    /** Helper: returns comma-separated list of all sheet names in workbook. */
    private static String getSheetNames(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(workbook.getSheetName(i));
        }
        return sb.toString();
    }
}
