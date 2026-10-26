package dev.nangua.miniagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Small OpenAI-compatible /chat/completions client built on the JDK HttpClient.
 * No agent framework and no vendor SDK are involved.
 *
 * <p>基于 JDK HttpClient 的 /chat/completions 客户端。内部只重试可恢复的 HTTP
 * 错误（408/409/425/429/5xx）和 IOException，退避尊重 Retry-After；这层重试对
 * AgentRuntime 完全透明——Runtime 只看到最终成功或抛出的 LlmException。
 */
public final class OpenAiCompatibleClient implements LlmClient {
    private final LlmConfig config;
    private final HttpClient httpClient;
    private final URI endpoint;

    public OpenAiCompatibleClient(LlmConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    OpenAiCompatibleClient(LlmConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.endpoint = endpoint(config.baseUrl());
    }

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        if (!config.hasApiKey()) {
            throw new LlmException("Missing API key: set LLM_API_KEY or DEEPSEEK_API_KEY.");
        }
        String requestBody = buildRequestBody(request);
        LlmException lastError = null;

        // 重试只覆盖 HTTP 层可恢复错误。注意：若请求已到达 provider 并计费、响应却在
        // 网络上丢失，重试可能造成重复计费（本项目没有幂等键）。
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                        .timeout(config.timeout())
                        .header("Authorization", "Bearer " + config.apiKey())
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Accept", "application/json")
                        .header("User-Agent", "mini-agent-java/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(response.body());
                }

                LlmException error = httpError(response);
                lastError = error;
                if (!isRetryable(response.statusCode()) || attempt == config.maxRetries()) {
                    throw error;
                }
                sleepBeforeRetry(attempt, retryAfterMillis(response));
            } catch (IOException e) {
                lastError = new LlmException("Network error while calling LLM: " + e.getMessage(), e);
                if (attempt == config.maxRetries()) {
                    throw lastError;
                }
                sleepBeforeRetry(attempt, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted while waiting for the LLM", e);
            }
        }
        throw lastError == null ? new LlmException("Unknown LLM failure") : lastError;
    }

    private String buildRequestBody(LlmRequest request) {
        ObjectNode body = Json.mapper().createObjectNode();
        body.put("model", config.model());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxOutputTokens());
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        // 双重开关：请求侧（AgentRuntime 固定传 true）与配置侧（LLM_JSON_MODE）
        // 都开启才真正下发 response_format。
        if (request.jsonMode() && config.jsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        return Json.write(body);
    }

    private LlmResponse parseResponse(String responseBody) {
        try {
            JsonNode root = Json.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                String message = error.path("message").asText(error.toString());
                throw new LlmException("Provider returned an error: " + Json.preview(message, 500));
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new LlmException("Provider response has no choices: " + Json.preview(responseBody, 500));
            }
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String content = extractContent(message.get("content"));
            String reasoning = extractContent(message.get("reasoning_content"));
            if (reasoning.isBlank()) {
                reasoning = extractContent(message.get("reasoning"));
            }
            String finishReason = choice.path("finish_reason").asText("unknown");
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(-1);
            int completionTokens = usage.path("completion_tokens").asInt(-1);
            String model = root.path("model").asText(config.model());
            return new LlmResponse(content, reasoning, finishReason,
                    promptTokens, completionTokens, model);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Cannot parse provider response: " + Json.preview(responseBody, 500), e);
        }
    }

    /** Handles both the usual string content and content-block arrays. */
    private static String extractContent(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : node) {
                if (block.isTextual()) {
                    sb.append(block.asText());
                } else if (block.has("text")) {
                    sb.append(block.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return node.toString();
    }

    private LlmException httpError(HttpResponse<String> response) {
        String body = Json.preview(response.body(), 800);
        String hint = switch (response.statusCode()) {
            case 401, 403 -> " (check LLM_API_KEY / account permissions)";
            case 402 -> " (insufficient balance)";
            case 429 -> " (rate limited)";
            default -> "";
        };
        return new LlmException("LLM HTTP " + response.statusCode() + hint + ": " + body,
                response.statusCode());
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 409 || status == 425 || status == 429 || status >= 500;
    }

    private static long retryAfterMillis(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try {
                return Long.parseLong(value.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                try {
                    ZonedDateTime when = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                    return Math.max(0, Duration.between(ZonedDateTime.now(), when).toMillis());
                } catch (Exception e) {
                    return -1L;
                }
            }
        }).orElse(-1L);
    }

    private static void sleepBeforeRetry(int attempt, long retryAfterMillis) {
        long backoff = retryAfterMillis >= 0 ? retryAfterMillis : (long) (500 * Math.pow(2, attempt));
        try {
            Thread.sleep(Math.min(backoff, 10_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting to retry the LLM call", e);
        }
    }

    /** 规范化端点：剥掉末尾斜杠，已带 /chat/completions 则原样使用，否则自动补上。 */
    static URI endpoint(String baseUrl) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String lower = base.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/chat/completions")) {
            return URI.create(base);
        }
        return URI.create(base + "/chat/completions");
    }
}
