package com.abchina.llmalf.agentgate.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 确定性 JSON 序列化.
 *
 * <p>字节级对齐 Python domain/base.py::canonical_json:
 * {@code json.dumps(value, ensure_ascii=False, sort_keys=True,
 * separators=(",", ":"), allow_nan=False)}——键按 Unicode 码点排序、紧凑分隔符、
 * 非 ASCII 原样输出、浮点采用 Python repr(最短往返表示)、NaN/Infinity 拒绝。</p>
 *
 * <p>入参限 JSON 兼容类型(Map/List/String/Number/Boolean/null)；非 String 键、
 * 非有限浮点与非 JSON 类型抛 {@link IllegalArgumentException}，
 * 由 Logic 层转换为 {@code AgentException(422)}。</p>
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    /**
     * 序列化为 canonical JSON 字符串.
     *
     * @param value JSON 兼容值
     * @return canonical JSON 字符串
     */
    public static String serialize(Object value) {
        StringBuilder buffer = new StringBuilder();
        write(value, buffer);
        return buffer.toString();
    }

    private static void write(Object value, StringBuilder buffer) {
        if (value == null) {
            buffer.append("null");
        } else if (value instanceof String) {
            writeString((String) value, buffer);
        } else if (value instanceof Boolean) {
            buffer.append(value.toString());
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger) {
            buffer.append(value.toString());
        } else if (value instanceof Float || value instanceof Double) {
            writeFloat(((Number) value).doubleValue(), buffer);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, buffer);
        } else if (value instanceof List) {
            writeArray((List<?>) value, buffer);
        } else {
            throw new IllegalArgumentException(
                    "Object of type " + value.getClass().getSimpleName() + " is not JSON serializable");
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder buffer) {
        String[] keys = new String[map.size()];
        int index = 0;
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            keys[index++] = (String) key;
        }
        Arrays.sort(keys, CanonicalJson::compareCodePoints);
        buffer.append('{');
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                buffer.append(',');
            }
            writeString(keys[i], buffer);
            buffer.append(':');
            write(map.get(keys[i]), buffer);
        }
        buffer.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder buffer) {
        buffer.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                buffer.append(',');
            }
            write(list.get(i), buffer);
        }
        buffer.append(']');
    }

    private static void writeString(String value, StringBuilder buffer) {
        buffer.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\n':
                    buffer.append("\\n");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\r':
                    buffer.append("\\r");
                    break;
                default:
                    if (c < 0x20) {
                        buffer.append(String.format("\\u%04x", (int) c));
                    } else {
                        buffer.append(c);
                    }
            }
        }
        buffer.append('"');
    }

    private static void writeFloat(double value, StringBuilder buffer) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Out of range float values are not JSON compliant");
        }
        buffer.append(doubleRepr(value));
    }

    /**
     * Python float repr: 最短往返十进制表示,定点/科学计数法与 CPython 规则一致.
     */
    static String doubleRepr(double value) {
        if (value == 0.0) {
            return Double.doubleToRawLongBits(value) < 0 ? "-0.0" : "0.0";
        }
        String sign = "";
        double magnitude = value;
        if (magnitude < 0) {
            sign = "-";
            magnitude = -magnitude;
        }
        BigDecimal exact = new BigDecimal(magnitude);
        BigDecimal rounded = exact;
        for (int precision = 1; precision <= 17; precision++) {
            rounded = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
            if (rounded.doubleValue() == magnitude) {
                break;
            }
        }
        String digits = rounded.unscaledValue().toString();
        int digitCount = digits.length();
        int decimalPoint = digitCount - rounded.scale();
        StringBuilder buffer = new StringBuilder(sign);
        if (decimalPoint < -3 || decimalPoint > 16) {
            buffer.append(digits, 0, 1);
            if (digitCount > 1) {
                buffer.append('.').append(digits, 1, digitCount);
            }
            int exponent = decimalPoint - 1;
            buffer.append('e').append(exponent < 0 ? '-' : '+');
            int absExponent = Math.abs(exponent);
            if (absExponent < 10) {
                buffer.append('0');
            }
            buffer.append(absExponent);
        } else if (decimalPoint <= 0) {
            buffer.append("0.");
            for (int i = 0; i < -decimalPoint; i++) {
                buffer.append('0');
            }
            buffer.append(digits);
        } else if (decimalPoint >= digitCount) {
            buffer.append(digits);
            for (int i = 0; i < decimalPoint - digitCount; i++) {
                buffer.append('0');
            }
            buffer.append(".0");
        } else {
            buffer.append(digits, 0, decimalPoint)
                    .append('.')
                    .append(digits, decimalPoint, digitCount);
        }
        return buffer.toString();
    }

    /**
     * 按 Unicode 码点比较(Python sort_keys 语义;与 UTF-16 单元比较在增补平面字符上存在差异).
     */
    static int compareCodePoints(String left, String right) {
        int leftLength = left.length();
        int rightLength = right.length();
        int i = 0;
        int j = 0;
        while (i < leftLength && j < rightLength) {
            int leftCode = left.codePointAt(i);
            int rightCode = right.codePointAt(j);
            if (leftCode != rightCode) {
                return Integer.compare(leftCode, rightCode);
            }
            i += Character.charCount(leftCode);
            j += Character.charCount(rightCode);
        }
        return Integer.compare(leftLength - i, rightLength - j);
    }
}
