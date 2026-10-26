package dev.nangua.miniagent.context;

import dev.nangua.miniagent.llm.ChatMessage;

import java.util.List;

/**
 * Cheap provider-independent estimate. CJK characters are roughly one token each,
 * Latin text roughly four characters per token. This is only used for budgeting,
 * never for billing.
 *
 * <p>廉价的供应商无关估算：CJK 字符约一字一 token，拉丁文本约四字符一 token，
 * 每条消息再加 4 token 的角色/框架开销。只用于压缩预算判断，绝不用于计费。
 */
public final class TokenEstimator {
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c) || isWidePunctuation(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    public int estimateMessages(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += 4; // role and framing overhead
            total += estimate(message.content());
        }
        return total;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0x3040 && c <= 0x30FF)
                || (c >= 0xAC00 && c <= 0xD7AF);
    }

    private static boolean isWidePunctuation(char c) {
        return (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }
}
