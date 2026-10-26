package dev.nangua.miniagent.agent;

import dev.nangua.miniagent.llm.LlmConfig;
import dev.nangua.miniagent.session.Session;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-API integration test, excluded from normal {@code mvn test} runs by its tag.
 * {@code mvn -Plive test} opts in and requires a configured API key.
 */
@Tag("live")
class DeepSeekLiveTest {
    @TempDir
    Path temp;

    @Test
    void realModelCallsCalculatorAndFinishes() {
        LlmConfig config = LlmConfig.fromEnvironment();
        assertTrue(config.hasApiKey(),
                "Live tests require LLM_API_KEY or DEEPSEEK_API_KEY; configure a key before running mvn -Plive test.");

        AgentFactory.Wired wired = AgentFactory.create(temp, Path.of("docs"), config, AgentConfig.defaults());
        Session session = wired.sessions().create("live", "live-api-test");
        AgentReply reply = wired.runtime().chat(session,
                "请务必先调用 calculator 工具计算 37*89，再根据工具结果回答。不要心算。");

        assertNotNull(reply);
        assertEquals(AgentStopReason.FINAL, reply.stopReason());
        assertFalse(reply.answer().isBlank());
        assertTrue(reply.toolCalls().stream().anyMatch(call -> call.name().equals("calculator")
                        && call.ok() && call.resultPreview().contains("\"result\":\"3293\"")),
                "expected a successful calculator result of 3293, got: " + reply.toolCalls());
        assertTrue(reply.answer().contains("3293"), "unexpected answer: " + reply.answer());
        assertTrue(wired.sessions().find("live", session.getId()).isPresent());
    }
}
