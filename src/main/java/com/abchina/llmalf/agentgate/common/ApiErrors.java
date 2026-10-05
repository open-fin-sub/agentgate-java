package com.abchina.llmalf.agentgate.common;

/**
 * 端点级错误映射.
 *
 * <p>对齐 Python raise_unprocessable/raise_not_found 的逐端点选择:
 * Service 抛域语义 IllegalArgumentException,Controller 按端点
 * 转换为 422 或 404。</p>
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    /**
     * 以 422 语义执行调用(域校验失败 → 不可处理)。
     *
     * @param call 业务调用
     * @param <T> 返回类型
     * @return 结果
     */
    public static <T> T unprocessable(ServiceCall<T> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException error) {
            throw new AgentException(422, error.getMessage());
        }
    }

    /**
     * 以 404 语义执行调用(资源不存在/不可见)。
     *
     * @param call 业务调用
     * @param <T> 返回类型
     * @return 结果
     */
    public static <T> T notFound(ServiceCall<T> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException error) {
            throw new AgentException(404, error.getMessage());
        }
    }

    /**
     * 无返回业务调用。
     */
    public interface ServiceCall<T> {

        /**
         * 执行业务.
         *
         * @return 结果
         */
        T get();
    }
}
