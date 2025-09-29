package com.universal.reconciliation.service.transform;

import com.universal.reconciliation.domain.entity.CanonicalFieldTransformation;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Applies Excel-style formulas using Apache POI so admins can reuse familiar
 * spreadsheet syntax.
 */
@Component
class ExcelFormulaTransformationEvaluator {

    Object evaluate(CanonicalFieldTransformation transformation, Object currentValue, Map<String, Object> rawRecord) {
        String expression = transformation.getExpression();
        if (expression == null || expression.isBlank()) {
            return currentValue;
        }
        String formula = normaliseFormula(expression);
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet("Transform").createRow(0);

            Cell valueCell = row.createCell(0);
            assignCellValue(valueCell, currentValue);
            defineName(workbook, "VALUE", valueCell);

            int columnIndex = 1;
            for (Map.Entry<String, Object> entry : rawRecord.entrySet()) {
                Cell cell = row.createCell(columnIndex);
                assignCellValue(cell, entry.getValue());
                defineName(workbook, sanitiseName(entry.getKey()), cell);
                columnIndex++;
            }

            Cell formulaCell = row.createCell(columnIndex + 1);
            formulaCell.setCellFormula(formula);

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            CellValue evaluated = evaluator.evaluate(formulaCell);
            if (evaluated == null) {
                return currentValue;
            }
            return switch (evaluated.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(evaluated.getNumberValue());
                case BOOLEAN -> evaluated.getBooleanValue();
                case STRING -> evaluated.getStringValue();
                case BLANK -> currentValue;
                default -> evaluated.formatAsString();
            };
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Failed to evaluate Excel formula", ex);
        } catch (RuntimeException ex) {
            throw new TransformationEvaluationException(
                    "Excel formula evaluation failed: " + ex.getMessage(), ex);
        }
    }

    void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new TransformationEvaluationException("Excel formula cannot be empty");
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet("Validate").createRow(0);
            Cell valueCell = row.createCell(0);
            assignCellValue(valueCell, "sample");
            defineName(workbook, "VALUE", valueCell);
            Cell formulaCell = row.createCell(1);
            formulaCell.setCellFormula(normaliseFormula(expression));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluate(formulaCell);
        } catch (IOException ex) {
            throw new TransformationEvaluationException("Unable to validate Excel formula", ex);
        } catch (RuntimeException ex) {
            throw new TransformationEvaluationException(
                    "Excel formula validation failed: " + ex.getMessage(), ex);
        }
    }

    private void defineName(Workbook workbook, String name, Cell cell) {
        Name definedName = workbook.createName();
        definedName.setNameName(name);
        definedName.setRefersToFormula(String.format("%s!%s", cell.getSheet().getSheetName(), cell.getAddress().formatAsString()));
    }

    private void assignCellValue(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private String sanitiseName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "_";
        }
        String uppercase = rawName.trim().toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (char ch : uppercase.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private String normaliseFormula(String formula) {
        String trimmed = formula.trim();
        return trimmed.startsWith("=") ? trimmed.substring(1) : trimmed;
    }
}
