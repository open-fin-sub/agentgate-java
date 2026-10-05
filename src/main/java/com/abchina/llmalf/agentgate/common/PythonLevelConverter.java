package com.abchina.llmalf.agentgate.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 对齐 Python logging 的级别名转换(WARN 输出为 WARNING,其余保持原名).
 *
 * <p>供 logback 配置的 pylevel 转换词使用,使双后端日志行格式一致。</p>
 */
public class PythonLevelConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        Level level = event.getLevel();
        if (Level.WARN.equals(level)) {
            return "WARNING";
        }
        return level.toString();
    }
}
