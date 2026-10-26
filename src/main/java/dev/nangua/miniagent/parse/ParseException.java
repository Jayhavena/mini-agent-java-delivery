package dev.nangua.miniagent.parse;

/** 模型输出不符合协议 JSON。Runtime 会计数并回灌 [PROTOCOL_CORRECTION] 纠错提示，
 *  超过 maxParseRetries 转为 PARSE_ERROR 兜底。 */
public class ParseException extends RuntimeException {
    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
