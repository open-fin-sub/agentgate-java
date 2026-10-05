package com.abchina.llmalf.agentgate.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * pydantic v2 校验错误累积器.
 *
 * <p>复刻 FastAPI RequestValidationError 响应体:错误列表置于信封 data,
 * message 为各错误 msg 以 "; " 连接(对齐 Python ResponseEnvelopeMiddleware)。
 * loc 支持 ["query", name] / ["body", field] 两级。</p>
 */
public final class PydanticErrors {

    private final List<Map<String, Object>> items = new ArrayList<>();

    /**
     * 是否已有错误.
     *
     * @return 有错误为 true
     */
    public boolean hasErrors() {
        return !items.isEmpty();
    }

    /**
     * 缺失字段(type=missing).
     *
     * @param scope loc 前缀(query/body)
     * @param field 字段名
     * @param input 原始输入(缺字段时为父对象)
     * @return this
     */
    public PydanticErrors missing(String scope, String field, Object input) {
        return add("missing", scope, field, "Field required", input, null);
    }

    /**
     * 类型不是字符串(type=string_type).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @return this
     */
    public PydanticErrors stringType(String scope, String field, Object input) {
        return add("string_type", scope, field, "Input should be a valid string",
                input, null);
    }

    /**
     * 字符串过短(type=string_too_short).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @param minLength 最小长度
     * @return this
     */
    public PydanticErrors stringTooShort(String scope, String field, Object input,
            int minLength) {
        return add("string_too_short", scope, field,
                "String should have at least " + minLength + " character"
                        + (minLength == 1 ? "" : "s"),
                input, context("min_length", minLength));
    }

    /**
     * 类型不是整数(type=int_type,严格模式).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @return this
     */
    public PydanticErrors intType(String scope, String field, Object input) {
        return add("int_type", scope, field, "Input should be a valid integer",
                input, null);
    }

    /**
     * 字符串无法解析为整数(type=int_parsing).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @return this
     */
    public PydanticErrors intParsing(String scope, String field, Object input) {
        return add("int_parsing", scope, field,
                "Input should be a valid integer, unable to parse string as an integer",
                input, null);
    }

    /**
     * 小于下界(type=greater_than_equal).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @param bound 下界
     * @return this
     */
    public PydanticErrors greaterEqual(String scope, String field, Object input, long bound) {
        return add("greater_than_equal", scope, field,
                "Input should be greater than or equal to " + bound,
                input, context("ge", bound));
    }

    /**
     * 大于上界(type=less_than_equal).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @param bound 上界
     * @return this
     */
    public PydanticErrors lessEqual(String scope, String field, Object input, long bound) {
        return add("less_than_equal", scope, field,
                "Input should be less than or equal to " + bound,
                input, context("le", bound));
    }

    /**
     * 不大于下界(type=greater_than).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @param bound 下界(开区间)
     * @return this
     */
    public PydanticErrors greaterThan(String scope, String field, Object input, long bound) {
        return add("greater_than", scope, field,
                "Input should be greater than " + bound,
                input, context("gt", bound));
    }

    /**
     * 枚举取值非法(type=enum,消息需与 pydantic 逐字一致).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @param pydanticMessage pydantic 枚举错误消息
     * @return this
     */
    public PydanticErrors enumError(String scope, String field, Object input,
            String pydanticMessage) {
        return add("enum", scope, field, pydanticMessage, input, null);
    }

    /**
     * 禁止的额外字段(type=extra_forbidden).
     *
     * @param scope loc 前缀
     * @param field 字段名
     * @param input 原始输入
     * @return this
     */
    public PydanticErrors extraForbidden(String scope, String field, Object input) {
        return add("extra_forbidden", scope, field,
                "Extra inputs are not permitted", input, null);
    }

    /**
     * 有错误则抛 422(message 为分号连接,data 为错误列表).
     */
    public void throwIfAny() {
        if (items.isEmpty()) {
            return;
        }
        List<String> messages = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            messages.add(String.valueOf(item.get("msg")));
        }
        throw new AgentException(422, String.join("; ", messages),
                new ArrayList<Map<String, Object>>(items));
    }

    /**
     * 自定义错误项(loc 两级).
     *
     * @param type 错误类型
     * @param scope loc 前缀
     * @param field 字段名
     * @param msg 错误消息
     * @param input 原始输入
     * @param ctx 上下文(可空)
     * @return this
     */
    public PydanticErrors custom(String type, String scope, String field, String msg,
            Object input, Map<String, Object> ctx) {
        return add(type, scope, field, msg, input, ctx);
    }

    private PydanticErrors add(String type, String scope, String field, String msg,
            Object input, Map<String, Object> ctx) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        List<String> loc = new ArrayList<>(2);
        loc.add(scope);
        loc.add(field);
        item.put("loc", loc);
        item.put("msg", msg);
        item.put("input", input);
        if (ctx != null) {
            item.put("ctx", ctx);
        }
        items.add(item);
        return this;
    }

    private static Map<String, Object> context(String key, Object value) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(key, value);
        return ctx;
    }
}
