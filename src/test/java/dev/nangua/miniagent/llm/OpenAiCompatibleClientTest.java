package dev.nangua.miniagent.llm;

import com.sun.net.httpserver.HttpServer;
import dev.nangua.miniagent.util.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {
    @Test
    void sendsOpenAiCompatibleRequestAndParsesResponse() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"model":"test-model","choices":[{"message":{"role":"assistant",
                    "content":"{\\"type\\":\\"final\\",\\"answer\\":\\"4\\"}"},
                    "finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":8}}
                    """);
        });
        try {
            LlmConfig config = config(server, 0);
            LlmResponse response = new OpenAiCompatibleClient(config).complete(
                    new LlmRequest(List.of(ChatMessage.system("system"), ChatMessage.user("2+2?")),
                            0.1, 64, true));

            assertTrue(response.content().contains("\"answer\":\"4\""));
            assertEquals("stop", response.finishReason());
            assertEquals(20, response.totalTokens());
            assertEquals("test-model", response.model());
            assertEquals("Bearer test-key", authorization.get());

            var request = Json.readTree(body.get());
            assertEquals("test-model", request.path("model").asText());
            assertEquals(64, request.path("max_tokens").asInt());
            assertEquals("json_object", request.path("response_format").path("type").asText());
            assertEquals("user", request.path("messages").get(1).path("role").asText());
            assertTrue(!body.get().contains("test-key"), "API key must not be in the JSON body");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsAuthenticationFailureWithoutRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 401, "{\"error\":{\"message\":\"invalid api key\"}}");
        });
        try {
            LlmException error = assertThrows(LlmException.class, () ->
                    new OpenAiCompatibleClient(config(server, 2)).complete(
                            new LlmRequest(List.of(ChatMessage.user("hi")), 0, 16, false)));
            assertEquals(401, error.httpStatus());
            assertTrue(error.isAuthenticationError());
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesServerErrorsThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                respond(exchange, 500, "{\"error\":{\"message\":\"temporary\"}}");
            } else {
                respond(exchange, 200, """
                        {"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}
                        """);
            }
        });
        try {
            LlmResponse response = new OpenAiCompatibleClient(config(server, 1)).complete(
                    new LlmRequest(List.of(ChatMessage.user("hi")), 0, 16, false));
            assertEquals("ok", response.content());
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void joinsBaseUrlWithoutDuplicatingChatCompletions() {
        assertEquals("https://api.deepseek.com/chat/completions",
                OpenAiCompatibleClient.endpoint("https://api.deepseek.com/").toString());
        assertEquals("https://api.openai.com/v1/chat/completions",
                OpenAiCompatibleClient.endpoint("https://api.openai.com/v1").toString());
        assertEquals("https://example.com/chat/completions",
                OpenAiCompatibleClient.endpoint("https://example.com/chat/completions").toString());
    }

    private static LlmConfig config(HttpServer server, int retries) {
        return new LlmConfig(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "test-model", 0.0, 64,
                Duration.ofSeconds(5), retries, true);
    }

    private static HttpServer server(Handler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }
}
