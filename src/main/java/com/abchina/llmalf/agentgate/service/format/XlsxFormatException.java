package com.abchina.llmalf.agentgate.service.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * XLSX 格式校验失败(有界问题集合).
 *
 * <p>对齐 Python XlsxFormatError:消息为问题明细拼接,
 * 由 Controller 映射为 422 结构化 detail
 * (code=xlsx_validation_failed)。</p>
 */
public final class XlsxFormatException extends IllegalArgumentException {

    private static final int MAX_ISSUES = 200;

    private final List<XlsxIssue> issues;

    public XlsxFormatException(List<XlsxIssue> issues) {
        super(join(issues));
        this.issues = Collections.unmodifiableList(
                new ArrayList<>(issues.subList(0, Math.min(issues.size(), MAX_ISSUES))));
    }

    public List<XlsxIssue> issues() {
        return issues;
    }

    private static String join(List<XlsxIssue> issues) {
        StringBuilder builder = new StringBuilder();
        for (XlsxIssue issue : issues) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(issue.sheet());
            if (issue.row() != null) {
                builder.append(" row ").append(issue.row());
            }
            if (issue.column() != null && !issue.column().isEmpty()) {
                builder.append(" column ").append(issue.column());
            }
            builder.append(": ").append(issue.message());
        }
        return builder.length() > 0 ? builder.toString() : "XLSX validation failed";
    }
}
