package com.krista.kme.agent.usage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates Excel reports for session LLM usage
 */
public class UsageReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(UsageReportGenerator.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Generate Excel report for a session
     * 
     * @param sessionId The session ID
     * @param collector The usage collector with all records
     * @param outputPath Path where to save the Excel file
     */
    public void generateReport(String sessionId, SessionUsageCollector collector, String outputPath) 
            throws IOException {
        
        logger.info("Generating usage report for session: {} at {}", sessionId, outputPath);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create sheets
            createSummarySheet(workbook, sessionId, collector);
            createDetailedCallsSheet(workbook, collector);
            createAgentSummarySheet(workbook, collector);
            
            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
            }
            
            logger.info("✓ Usage report generated successfully: {}", outputPath);
        }
    }
    
    /**
     * Create summary sheet with overall statistics
     */
    private void createSummarySheet(Workbook workbook, String sessionId, SessionUsageCollector collector) {
        Sheet sheet = workbook.createSheet("Summary");
        
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle labelStyle = createLabelStyle(workbook);
        CellStyle valueStyle = createValueStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        
        int rowNum = 0;
        
        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("LLM Usage Report - Session: " + sessionId);
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        rowNum++; // Empty row

        // LLM Configuration
        Row configHeaderRow = sheet.createRow(rowNum++);
        Cell configHeaderCell = configHeaderRow.createCell(0);
        configHeaderCell.setCellValue("LLM Configuration");
        configHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

        createLabelValueRow(sheet, rowNum++, "Provider:", collector.getLlmProvider(), labelStyle, valueStyle);
        createLabelValueRow(sheet, rowNum++, "Model:", collector.getLlmModel(), labelStyle, valueStyle);
        createLabelValueRow(sheet, rowNum++, "API Key:", collector.getLlmApiKeyMasked(), labelStyle, valueStyle);

        rowNum++; // Empty row

        // Overall statistics
        Row statsHeaderRow = sheet.createRow(rowNum++);
        Cell statsHeaderCell = statsHeaderRow.createCell(0);
        statsHeaderCell.setCellValue("Usage Statistics");
        statsHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

        createLabelValueRow(sheet, rowNum++, "Total LLM Calls:", collector.getCallCount(), labelStyle, valueStyle);
        createLabelValueRow(sheet, rowNum++, "Total Input Tokens:", collector.getTotalInputTokens(), labelStyle, valueStyle);
        createLabelValueRow(sheet, rowNum++, "Total Output Tokens:", collector.getTotalOutputTokens(), labelStyle, valueStyle);
        createLabelValueRow(sheet, rowNum++, "Total Tokens:", collector.getTotalTokens(), labelStyle, valueStyle);

        Row costRow = sheet.createRow(rowNum++);
        Cell costLabel = costRow.createCell(0);
        costLabel.setCellValue("Total Cost:");
        costLabel.setCellStyle(labelStyle);
        Cell costValue = costRow.createCell(1);
        costValue.setCellValue(collector.getTotalCost());
        costValue.setCellStyle(currencyStyle);

        // Auto-size columns
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);
    }
    
    /**
     * Create detailed calls sheet with all LLM calls
     */
    private void createDetailedCallsSheet(Workbook workbook, SessionUsageCollector collector) {
        Sheet sheet = workbook.createSheet("Detailed Calls");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        
        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"#", "Timestamp", "Agent Type", "Agent Name", "Input Tokens", 
                           "Output Tokens", "Total Tokens", "Cost", "Model", "Prompt (truncated)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Data rows
        List<LLMUsageRecord> records = collector.getRecords();
        int rowNum = 1;
        for (int i = 0; i < records.size(); i++) {
            LLMUsageRecord record = records.get(i);
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(record.getTimestamp().format(TIME_FORMATTER));
            row.createCell(2).setCellValue(record.getAgentType());
            row.createCell(3).setCellValue(record.getAgentName());
            row.createCell(4).setCellValue(record.getInputTokens());
            row.createCell(5).setCellValue(record.getOutputTokens());
            row.createCell(6).setCellValue(record.getTotalTokens());
            
            Cell costCell = row.createCell(7);
            costCell.setCellValue(record.getCost());
            costCell.setCellStyle(currencyStyle);
            
            row.createCell(8).setCellValue(record.getModelName());
            
            // Truncate prompt for display
            String prompt = record.getPrompt();
            if (prompt.length() > 100) {
                prompt = prompt.substring(0, 100) + "...";
            }
            row.createCell(9).setCellValue(prompt);
            
            // Apply cell style
            for (int j = 0; j < 10; j++) {
                if (j != 7) { // Skip cost cell (already styled)
                    row.getCell(j).setCellStyle(cellStyle);
                }
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Create agent summary sheet with aggregated stats per agent type
     */
    private void createAgentSummarySheet(Workbook workbook, SessionUsageCollector collector) {
        Sheet sheet = workbook.createSheet("Agent Summary");

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle cellStyle = createCellStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Agent Type", "Total Calls", "Input Tokens", "Output Tokens",
                           "Total Tokens", "Total Cost"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        Map<String, SessionUsageCollector.AgentUsageSummary> summaries = collector.getAgentSummaries();
        int rowNum = 1;
        for (SessionUsageCollector.AgentUsageSummary summary : summaries.values()) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(summary.getAgentType());
            row.createCell(1).setCellValue(summary.getCallCount());
            row.createCell(2).setCellValue(summary.getTotalInputTokens());
            row.createCell(3).setCellValue(summary.getTotalOutputTokens());
            row.createCell(4).setCellValue(summary.getTotalTokens());

            Cell costCell = row.createCell(5);
            costCell.setCellValue(summary.getTotalCost());
            costCell.setCellStyle(currencyStyle);

            // Apply cell style
            for (int j = 0; j < 6; j++) {
                if (j != 5) { // Skip cost cell
                    row.getCell(j).setCellStyle(cellStyle);
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Style helper methods

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createValueStyle(Workbook workbook) {
        return workbook.createCellStyle();
    }

    private CellStyle createCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = createCellStyle(workbook);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.000000"));
        return style;
    }

    private void createLabelValueRow(Sheet sheet, int rowNum, String label, Object value,
                                     CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        if (value instanceof Integer) {
            valueCell.setCellValue((Integer) value);
        } else if (value instanceof Double) {
            valueCell.setCellValue((Double) value);
        } else {
            valueCell.setCellValue(value.toString());
        }
        valueCell.setCellStyle(valueStyle);
    }
}
