package com.abchina.llmalf.agentgate.service.format;

/**
 * XLSX 工作簿问题定位.
 *
 * <p>对齐 Python dataset/formats/xlsx.py::XlsxIssue。</p>
 */
public final class XlsxIssue {

    private final String sheet;
    private final Integer row;
    private final String column;
    private final String message;

    public XlsxIssue(String sheet, Integer row, String column, String message) {
        this.sheet = sheet;
        this.row = row;
        this.column = column;
        this.message = message;
    }

    public String sheet() {
        return sheet;
    }

    public Integer row() {
        return row;
    }

    public String column() {
        return column;
    }

    public String message() {
        return message;
    }
}
