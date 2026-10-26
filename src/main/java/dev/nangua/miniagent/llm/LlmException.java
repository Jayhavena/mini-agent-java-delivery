package dev.nangua.miniagent.llm;

/** LLM 调用失败（网络 / HTTP 错误 / 响应不可解析）。httpStatus 为 -1 表示网络层
 *  错误（没拿到 HTTP 状态码）；isAuthenticationError 供上层给出针对性提示。 */
public class LlmException extends RuntimeException {
    private final int httpStatus;

    public LlmException(String message) {
        this(message, -1, null);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public LlmException(String message, int httpStatus) {
        this(message, httpStatus, null);
    }

    public LlmException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean isAuthenticationError() {
        return httpStatus == 401 || httpStatus == 403;
    }
}
