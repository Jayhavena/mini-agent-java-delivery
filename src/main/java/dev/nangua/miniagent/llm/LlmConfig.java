package dev.nangua.miniagent.llm;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Provider settings, loaded from environment variables but injectable in tests.
 *
 * <p>供应商侧配置，全部来自 LLM_* 环境变量，测试中可直接注入。默认指向 DeepSeek
 * 的 OpenAI 兼容端点；maskedKey 用于日志脱敏输出。
 */
public record LlmConfig(
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxOutputTokens,
        Duration timeout,
        int maxRetries,
        boolean jsonMode
) {
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    public LlmConfig {
        baseUrl = requireText(baseUrl, "baseUrl");
        apiKey = Objects.requireNonNullElse(apiKey, "");
        model = requireText(model, "model");
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be in [0, 2]");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
    }

    public static LlmConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static LlmConfig fromEnvironment(Map<String, String> env) {
        String key = firstNonBlank(env.get("LLM_API_KEY"), env.get("DEEPSEEK_API_KEY"), "");
        String baseUrl = firstNonBlank(env.get("LLM_BASE_URL"), DEFAULT_BASE_URL, DEFAULT_BASE_URL);
        String model = firstNonBlank(env.get("LLM_MODEL"), DEFAULT_MODEL, DEFAULT_MODEL);
        double temperature = parseDouble(env.get("LLM_TEMPERATURE"), 0.2);
        int maxTokens = parseInt(env.get("LLM_MAX_OUTPUT_TOKENS"), 1024);
        int maxRetries = parseInt(env.get("LLM_MAX_RETRIES"), 2);
        boolean jsonMode = !"false".equalsIgnoreCase(firstNonBlank(env.get("LLM_JSON_MODE"), "true", "true"));
        return new LlmConfig(baseUrl, key, model, temperature, maxTokens,
                Duration.ofSeconds(120), maxRetries, jsonMode);
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    public String maskedKey() {
        if (apiKey.isBlank()) {
            return "<missing>";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
