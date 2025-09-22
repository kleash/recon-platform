package com.universal.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.RunDetailDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Translates reconciliation runs into downloadable Excel workbooks.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ObjectMapper objectMapper;

    public ExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] exportToExcel(RunDetailDto detail) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            populateSummarySheet(detail, workbook.createSheet("Summary"));
            populateBreakSheet(detail, workbook.createSheet("Breaks"));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    private void populateSummarySheet(RunDetailDto detail, Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Run Attribute");
        header.createCell(1).setCellValue("Value");

        sheet.createRow(1).createCell(0).setCellValue("Run ID");
        sheet.getRow(1).createCell(1).setCellValue(detail.summary().runId() != null ? detail.summary().runId() : -1);

        sheet.createRow(2).createCell(0).setCellValue("Run Date Time");
        sheet.getRow(2).createCell(1).setCellValue(detail.summary().runDateTime() != null ? detail.summary().runDateTime().toString() : "N/A");

        sheet.createRow(3).createCell(0).setCellValue("Matched");
        sheet.getRow(3).createCell(1).setCellValue(detail.summary().matched());

        sheet.createRow(4).createCell(0).setCellValue("Mismatched");
        sheet.getRow(4).createCell(1).setCellValue(detail.summary().mismatched());

        sheet.createRow(5).createCell(0).setCellValue("Missing");
        sheet.getRow(5).createCell(1).setCellValue(detail.summary().missing());
    }

    private void populateBreakSheet(RunDetailDto detail, Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Break ID");
        header.createCell(1).setCellValue("Type");
        header.createCell(2).setCellValue("Status");
        header.createCell(3).setCellValue("Detected At");
        header.createCell(4).setCellValue("Source A");
        header.createCell(5).setCellValue("Source B");
        header.createCell(6).setCellValue("Comments");

        int rowIdx = 1;
        for (BreakItemDto item : detail.breaks()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(item.id());
            row.createCell(1).setCellValue(item.breakType().name());
            row.createCell(2).setCellValue(item.status().name());
            row.createCell(3).setCellValue(item.detectedAt().toString());
            row.createCell(4).setCellValue(writeJson(item.sourceA()));
            row.createCell(5).setCellValue(writeJson(item.sourceB()));
            row.createCell(6).setCellValue(item.comments().stream()
                    .map(comment -> String.format("%s: %s", comment.actorDn(), comment.comment()))
                    .collect(Collectors.joining(" | ")));
        }
        int[] columnWidths = {18, 14, 16, 24, 36, 36, 48};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize break payload for export", e);
            throw new IllegalStateException("Failed to serialize break payload for export", e);
        }
    }
}
