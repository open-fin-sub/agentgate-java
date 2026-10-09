package com.abchina.llmalf.agentgate.service.format;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 单表 XLSX 用例交换格式.
 *
 * <p>对齐 Python dataset/formats/xlsx.py:归档安全校验(zip 炸弹/宏/
 * 加密/大小)、表头与行解析、用例分组冲突、turn_order 规则、
 * 文本化写出;问题消息逐字一致。</p>
 */
public final class DatasetXlsxFormat {

    /** 工作表名(协议契约常量) */
    public static final String SHEET_NAME = "Cases";
    /** 表头序列 */
    public static final String[] HEADERS = {
            "case_id", "case_name", "category", "difficulty", "tags_json", "case_notes",
            "initial_state_json", "turn_id", "turn_order", "input_json", "expectations_json",
            "turn_notes",
    };
    /** 必填表头 */
    public static final Set<String> REQUIRED_HEADERS = new HashSet<>(Arrays.asList(
            "case_id", "case_name", "input_json"));
    /** 输入大小上限(10 MiB) */
    public static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    /** 数据行上限 */
    public static final int MAX_ROWS = 10_000;
    /** 归档条目上限 */
    public static final int MAX_ARCHIVE_ENTRIES = 2_000;
    /** 解压总量上限(100 MiB) */
    public static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    /** 单条目上限(50 MiB) */
    public static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;
    /** 压缩比上限 */
    public static final int MAX_COMPRESSION_RATIO = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DatasetXlsxFormat() {
    }

    /**
     * 解析 Cases 工作表为有序用例 payload 列表.
     *
     * @param source XLSX 字节
     * @return 用例 payload 列表
     */
    public static List<Map<String, Object>> parse(byte[] source) {
        validateArchive(source);
        List<XlsxIssue> issues = new ArrayList<>();
        List<Map<String, Object>> output;
        XSSFWorkbook workbook;
        try {
            workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
        } catch (RuntimeException | IOException e) {
            throw new XlsxFormatException(Collections.singletonList(
                    new XlsxIssue(SHEET_NAME, null, null, "workbook cannot be opened")));
        }
        try {
            int sheetIndex = workbook.getSheetIndex(SHEET_NAME);
            if (sheetIndex < 0) {
                throw new XlsxFormatException(Collections.singletonList(
                        new XlsxIssue(SHEET_NAME, null, null, "required worksheet is missing")));
            }
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            output = parseSheet(sheet, issues);
        } catch (XlsxFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new XlsxFormatException(Collections.singletonList(
                    new XlsxIssue(SHEET_NAME, null, null, "workbook content is invalid")));
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 关闭失败不影响结果
            }
        }
        if (!issues.isEmpty()) {
            throw new XlsxFormatException(issues);
        }
        return output;
    }

    /**
     * 写出用例 payload 列表为单表 XLSX.
     *
     * @param cases 用例 payload 列表
     * @return XLSX 字节
     */
    public static byte[] dump(List<Map<String, Object>> cases) {
        SXSSFWorkbook workbook = new SXSSFWorkbook();
        try {
            SXSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.createFreezePane(0, 1);
            writeRow(sheet, Arrays.asList(HEADERS));
            for (Map<String, Object> caseItem : cases) {
                List<?> turns = (List<?>) caseItem.get("turns");
                if (turns == null || turns.isEmpty()) {
                    throw new XlsxFormatException(Collections.singletonList(
                            new XlsxIssue(SHEET_NAME, null, null,
                                    "cannot export workbook: XLSX export Case requires at least one Turn")));
                }
                int order = 1;
                for (Object turnObject : turns) {
                    if (!(turnObject instanceof Map)) {
                        throw new XlsxFormatException(Collections.singletonList(
                                new XlsxIssue(SHEET_NAME, null, null,
                                        "cannot export workbook: XLSX export Turn must be an object")));
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> turn = (Map<String, Object>) turnObject;
                    List<Object> row = new ArrayList<>(12);
                    row.add(text(caseItem.get("id")));
                    row.add(text(caseItem.get("name")));
                    row.add(text(caseItem.getOrDefault("category", "positive")));
                    row.add(text(caseItem.getOrDefault("difficulty", "medium")));
                    row.add(jsonText(caseItem.getOrDefault("tags", new ArrayList<>())));
                    row.add(text(caseItem.getOrDefault("notes", "")));
                    row.add(jsonText(caseItem.getOrDefault("initial_state", new LinkedHashMap<>())));
                    row.add(text(turn.getOrDefault("id", "")));
                    row.add(order);
                    row.add(jsonText(turn.getOrDefault("input", new LinkedHashMap<>())));
                    row.add(jsonText(turn.getOrDefault("expectations", new ArrayList<>())));
                    row.add(text(turn.getOrDefault("notes", "")));
                    writeRow(sheet, row);
                    order++;
                }
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            workbook.write(buffer);
            return buffer.toByteArray();
        } catch (XlsxFormatException e) {
            throw e;
        } catch (RuntimeException | IOException e) {
            throw new XlsxFormatException(Collections.singletonList(
                    new XlsxIssue(SHEET_NAME, null, null, "cannot export workbook: " + e)));
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 关闭失败不影响结果
            }
        }
    }

    private static List<Map<String, Object>> parseSheet(Sheet sheet, List<XlsxIssue> issues) {
        Row headerRow = sheet.getRow(0);
        List<String> names = new ArrayList<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                if (cell.getCellType() == CellType.FORMULA) {
                    issue(issues, 1, null, "formulas are not allowed");
                }
                Object value = rawValue(cell);
                if (value instanceof String) {
                    names.add((String) value);
                } else if (value != null) {
                    issue(issues, 1, null, "headers must be text");
                }
            }
        }
        Set<String> nameSet = new HashSet<>(names);
        if (nameSet.size() != names.size()) {
            issue(issues, 1, null, "headers must not contain duplicates");
        }
        for (String name : names) {
            if (!Arrays.asList(HEADERS).contains(name)) {
                issue(issues, 1, name, "unexpected header");
            }
        }
        Set<String> missingRequired = new TreeSet<>(REQUIRED_HEADERS);
        missingRequired.removeAll(nameSet);
        for (String required : missingRequired) {
            issue(issues, 1, required, "required header is missing");
        }
        if (!issues.isEmpty()) {
            throw new XlsxFormatException(issues);
        }

        Map<String, List<int[]>> placeholder = null;
        Map<String, List<RowData>> grouped = new LinkedHashMap<>();
        for (int rowNumber = 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
            Row row = sheet.getRow(rowNumber);
            if (row == null) {
                continue;
            }
            if (rowNumber + 1 > MAX_ROWS + 1) {
                issue(issues, rowNumber + 1, null, "worksheet exceeds 10,000 data rows");
                break;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            boolean allEmpty = true;
            for (int i = 0; i < names.size(); i++) {
                Cell cell = row.getCell(i);
                Object value;
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    issue(issues, rowNumber + 1, names.get(i), "formulas are not allowed");
                    value = null;
                } else {
                    value = rawValue(cell);
                }
                values.put(names.get(i), value);
                if (value != null && !"".equals(value)) {
                    allEmpty = false;
                }
            }
            if (allEmpty) {
                continue;
            }
            RowData data = parseDataRow(values, rowNumber + 1, issues);
            if (data.caseId != null && !data.caseId.isEmpty()) {
                grouped.computeIfAbsent(data.caseId, key -> new ArrayList<>()).add(data);
            }
        }

        List<Map<String, Object>> output = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<RowData>> entry : grouped.entrySet()) {
            output.add(buildCase(entry.getKey(), entry.getValue(), issues));
        }
        return output;
    }

    private static Map<String, Object> buildCase(String caseId, List<RowData> rows,
            List<XlsxIssue> issues) {
        RowData first = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            RowData other = rows.get(i);
            conflictCheck(issues, other.rowNumber, "case_name", other.caseName, first.caseName,
                    first.rowNumber, caseId);
            conflictCheck(issues, other.rowNumber, "category", other.category, first.category,
                    first.rowNumber, caseId);
            conflictCheck(issues, other.rowNumber, "difficulty", other.difficulty,
                    first.difficulty, first.rowNumber, caseId);
            conflictCheck(issues, other.rowNumber, "tags_json", other.tags, first.tags,
                    first.rowNumber, caseId);
            conflictCheck(issues, other.rowNumber, "case_notes", other.notes, first.notes,
                    first.rowNumber, caseId);
            conflictCheck(issues, other.rowNumber, "initial_state_json", other.initialState,
                    first.initialState, first.rowNumber, caseId);
        }

        List<RowData> orderedRows = rows;
        boolean allNull = true;
        for (RowData row : rows) {
            if (row.turnOrder != null) {
                allNull = false;
                break;
            }
        }
        if (!allNull) {
            boolean anyNull = false;
            for (RowData row : rows) {
                if (row.turnOrder == null) {
                    anyNull = true;
                    break;
                }
            }
            if (anyNull) {
                issue(issues, first.rowNumber, "turn_order",
                        "must be provided for every Turn in this Case");
            } else {
                List<Integer> sorted = new ArrayList<>(rows.size());
                for (RowData row : rows) {
                    sorted.add(row.turnOrder);
                }
                Collections.sort(sorted);
                boolean contiguous = true;
                for (int i = 0; i < sorted.size(); i++) {
                    if (sorted.get(i) != i + 1) {
                        contiguous = false;
                        break;
                    }
                }
                if (!contiguous) {
                    issue(issues, first.rowNumber, "turn_order",
                            "must be unique and contiguous from 1");
                }
                orderedRows = new ArrayList<>(rows);
                orderedRows.sort((a, b) -> Integer.compare(
                        a.turnOrder == null ? 0 : a.turnOrder,
                        b.turnOrder == null ? 0 : b.turnOrder));
            }
        }

        List<Map<String, Object>> turns = new ArrayList<>(orderedRows.size());
        Set<String> turnIds = new HashSet<>();
        for (RowData row : orderedRows) {
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("input", row.input);
            turn.put("expectations", row.expectations);
            turn.put("notes", row.turnNotes);
            String turnId = row.turnId;
            if (turnId != null && !turnId.isEmpty()) {
                if (!turnIds.add(turnId)) {
                    issue(issues, row.rowNumber, "turn_id", "duplicate within Case");
                }
                turn.put("id", turnId);
            }
            turns.add(turn);
        }
        Map<String, Object> casePayload = new LinkedHashMap<>();
        casePayload.put("id", caseId);
        casePayload.put("name", first.caseName);
        casePayload.put("category", first.category);
        casePayload.put("difficulty", first.difficulty);
        casePayload.put("tags", first.tags);
        casePayload.put("notes", first.notes);
        casePayload.put("initial_state", first.initialState);
        casePayload.put("turns", turns);
        return casePayload;
    }

    private static void conflictCheck(List<XlsxIssue> issues, int rowNumber, String column,
            Object value, Object firstValue, int firstRowNumber, String caseId) {
        if (!Objects.equals(value, firstValue)) {
            issue(issues, rowNumber, column,
                    "conflicts with row " + firstRowNumber + " for Case " + caseId);
        }
    }

    private static RowData parseDataRow(Map<String, Object> values, int rowNumber,
            List<XlsxIssue> issues) {
        RowData data = new RowData();
        data.rowNumber = rowNumber;
        data.caseId = parseText(values.get("case_id"), rowNumber, "case_id", issues, true, "");
        data.caseName = parseText(values.get("case_name"), rowNumber, "case_name", issues, true,
                "");
        data.category = parseText(values.get("category"), rowNumber, "category", issues, false,
                "positive");
        data.difficulty = parseText(values.get("difficulty"), rowNumber, "difficulty", issues,
                false, "medium");
        data.tags = parseJsonCell(values.get("tags_json"), rowNumber, "tags_json", issues,
                new ArrayList<>(), true, false);
        data.notes = parseText(values.get("case_notes"), rowNumber, "case_notes", issues, false,
                "");
        data.initialState = parseJsonCell(values.get("initial_state_json"), rowNumber,
                "initial_state_json", issues, new LinkedHashMap<>(), false, false);
        data.turnId = parseText(values.get("turn_id"), rowNumber, "turn_id", issues, false, "");
        data.turnOrder = parseOrder(values.get("turn_order"), rowNumber, issues);
        data.input = parseJsonCell(values.get("input_json"), rowNumber, "input_json", issues,
                new LinkedHashMap<>(), false, true);
        data.expectations = parseJsonCell(values.get("expectations_json"), rowNumber,
                "expectations_json", issues, new ArrayList<>(), true, false);
        data.turnNotes = parseText(values.get("turn_notes"), rowNumber, "turn_notes", issues,
                false, "");
        return data;
    }

    private static String parseText(Object value, int row, String column, List<XlsxIssue> issues,
            boolean required, String fallback) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            if (required) {
                issue(issues, row, column, "value is required");
            }
            return fallback;
        }
        if (!(value instanceof String)) {
            issue(issues, row, column, "must be text");
            return fallback;
        }
        return required ? ((String) value).trim() : (String) value;
    }

    private static Object parseJsonCell(Object value, int row, String column,
            List<XlsxIssue> issues, Object fallback, boolean expectList, boolean required) {
        if (value == null || "".equals(value)) {
            if (required) {
                issue(issues, row, column, "value is required");
            }
            return fallback;
        }
        if (!(value instanceof String)) {
            issue(issues, row, column, "must contain JSON text");
            return fallback;
        }
        try {
            JsonNode node = MAPPER.readTree((String) value);
            if (hasDuplicateKeys((String) value)) {
                throw new IllegalArgumentException("duplicate");
            }
            if (expectList) {
                if (!node.isArray()) {
                    issue(issues, row, column, "must contain a JSON list");
                    return fallback;
                }
                return MAPPER.convertValue(node, List.class);
            }
            if (!node.isObject()) {
                issue(issues, row, column, "must contain a JSON dict");
                return fallback;
            }
            return MAPPER.convertValue(node, Map.class);
        } catch (IllegalArgumentException e) {
            if ("duplicate".equals(e.getMessage())) {
                issue(issues, row, column, "invalid JSON: duplicate object key");
            } else {
                issue(issues, row, column, "invalid JSON: Expecting value");
            }
            return fallback;
        } catch (Exception e) {
            issue(issues, row, column, "invalid JSON: Expecting value");
            return fallback;
        }
    }

    private static boolean hasDuplicateKeys(String text) {
        try {
            JsonFactory factory = MAPPER.getFactory();
            try (JsonParser parser = factory.createParser(text)) {
                Deque<Set<String>> stack = new ArrayDeque<>();
                while (true) {
                    JsonToken token = parser.nextToken();
                    if (token == null) {
                        return false;
                    }
                    if (token == JsonToken.START_OBJECT) {
                        stack.push(new HashSet<String>());
                    } else if (token == JsonToken.FIELD_NAME) {
                        Set<String> seen = stack.peek();
                        if (seen != null && !seen.add(parser.getCurrentName())) {
                            return true;
                        }
                    } else if (token == JsonToken.END_OBJECT) {
                        if (!stack.isEmpty()) {
                            stack.pop();
                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static Integer parseOrder(Object value, int row, List<XlsxIssue> issues) {
        if (value == null || "".equals(value)) {
            return null;
        }
        Integer order = null;
        if (value instanceof Integer) {
            order = (Integer) value;
        } else if (value instanceof Double && ((Double) value) == Math.floor((Double) value)) {
            order = Integer.valueOf(((Double) value).intValue());
        } else if (value instanceof String) {
            try {
                order = Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException e) {
                order = null;
            }
        }
        if (order == null || order < 1) {
            issue(issues, row, "turn_order", "must be a positive integer");
            return null;
        }
        return order;
    }

    private static void validateArchive(byte[] source) {
        List<XlsxIssue> issues = new ArrayList<>();
        if (source.length > MAX_INPUT_BYTES) {
            throw new XlsxFormatException(Collections.singletonList(
                    new XlsxIssue(SHEET_NAME, null, null, "file exceeds the 10 MiB limit")));
        }
        Set<String> names = new HashSet<>();
        long totalSize = 0;
        int entryCount = 0;
        try (ZipArchiveInputStream archive =
                new ZipArchiveInputStream(
                        new ByteArrayInputStream(source))) {
            ZipArchiveEntry entry;
            while ((entry = archive.getNextZipEntry()) != null) {
                entryCount++;
                names.add(entry.getName());
                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (size >= 0) {
                    totalSize += size;
                }
                if (size > MAX_ENTRY_BYTES) {
                    issue(issues, null, null, "archive entry is too large: " + entry.getName());
                }
                if (size > 0 && compressed > 0 && size / compressed > MAX_COMPRESSION_RATIO) {
                    issue(issues, null, null,
                            "archive entry compression ratio is too high: " + entry.getName());
                }
            }
        } catch (Exception e) {
            throw new XlsxFormatException(Collections.singletonList(
                    new XlsxIssue(SHEET_NAME, null, null, "file is not a valid XLSX archive")));
        }
        if (entryCount == 0) {
            throw new XlsxFormatException(Collections.singletonList(
                    new XlsxIssue(SHEET_NAME, null, null, "file is not a valid XLSX archive")));
        }
        if (!names.contains("[Content_Types].xml")) {
            issue(issues, null, null, "XLSX content types are missing");
        }
        if (entryCount > MAX_ARCHIVE_ENTRIES) {
            issue(issues, null, null, "archive contains too many entries");
        }
        if (totalSize > MAX_UNCOMPRESSED_BYTES) {
            issue(issues, null, null, "archive expands beyond the 100 MiB limit");
        }
        for (String name : names) {
            if (name.endsWith("vbaProject.bin")) {
                issue(issues, null, null, "workbook macros are not allowed");
            }
            if (name.startsWith("xl/externalLinks/")) {
                issue(issues, null, null, "workbook external links are not allowed");
            }
            if (name.startsWith("xl/embeddings/")) {
                issue(issues, null, null, "workbook embedded objects are not allowed");
            }
            if (name.startsWith("xl/activeX/")) {
                issue(issues, null, null, "workbook ActiveX controls are not allowed");
            }
        }
        if (names.contains("xl/connections.xml")) {
            issue(issues, null, null, "workbook data connections are not allowed");
        }
        if (!issues.isEmpty()) {
            throw new XlsxFormatException(issues);
        }
    }

    private static void writeRow(SXSSFSheet sheet, List<?> values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            Object value = values.get(i);
            if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : String.valueOf(value));
            }
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String jsonText(Object value) {
        return CanonicalJson.serialize(value);
    }

    private static Object rawValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double number = cell.getNumericCellValue();
                if (number == Math.floor(number) && !Double.isInfinite(number)
                        && Math.abs(number) < Integer.MAX_VALUE) {
                    long rounded = Math.round(number);
                    if (rounded >= Integer.MIN_VALUE && rounded <= Integer.MAX_VALUE) {
                        return (int) rounded;
                    }
                }
                return number;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    private static void issue(List<XlsxIssue> issues, Integer row, String column, String message) {
        if (issues.size() < 200) {
            issues.add(new XlsxIssue(SHEET_NAME, row, column, message));
        }
    }

    private static final class RowData {
        private int rowNumber;
        private String caseId;
        private String caseName;
        private String category;
        private String difficulty;
        private Object tags;
        private String notes;
        private Object initialState;
        private String turnId;
        private Integer turnOrder;
        private Object input;
        private Object expectations;
        private String turnNotes;
    }

}
