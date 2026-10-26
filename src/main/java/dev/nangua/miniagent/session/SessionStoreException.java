package dev.nangua.miniagent.session;

/**
 * session 持久化层失败时抛出的统一运行时异常。
 * JsonSessionRepository 把底层 IOException（建目录、读写、删除失败）与文件
 * 身份校验失败都包装成本类型，调用方只需捕获它即可统一处理存储故障。
 */
public class SessionStoreException extends RuntimeException {
    public SessionStoreException(String message) {
        super(message);
    }

    public SessionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
