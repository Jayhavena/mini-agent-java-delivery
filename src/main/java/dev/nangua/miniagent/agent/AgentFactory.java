package dev.nangua.miniagent.agent;

import dev.nangua.miniagent.context.ContextManager;
import dev.nangua.miniagent.context.SystemPromptBuilder;
import dev.nangua.miniagent.llm.LlmConfig;
import dev.nangua.miniagent.llm.OpenAiCompatibleClient;
import dev.nangua.miniagent.session.JsonSessionRepository;
import dev.nangua.miniagent.session.SessionRepository;
import dev.nangua.miniagent.tool.CalculatorTool;
import dev.nangua.miniagent.tool.ReadDocsTool;
import dev.nangua.miniagent.tool.SearchTool;
import dev.nangua.miniagent.tool.TodoTool;
import dev.nangua.miniagent.tool.ToolRegistry;
import dev.nangua.miniagent.tool.WeatherTool;
import dev.nangua.miniagent.trace.JsonlTraceLogger;
import dev.nangua.miniagent.trace.TraceLogger;

import java.nio.file.Path;

/**
 * Explicit dependency wiring for the CLI, demo and live tests.
 *
 * <p>显式的依赖装配点：CLI、demo 和 live 测试都从这里拿 {@link Wired}。没有任何
 * 自动装配，每个依赖的来源都能在 create 里一眼看到。
 */
public final class AgentFactory {
    private AgentFactory() {
    }

    public static Wired create(Path dataDir, LlmConfig llmConfig, AgentConfig agentConfig) {
        return create(dataDir, Path.of("docs"), llmConfig, agentConfig);
    }

    /** docsDir 是 ReadDocsTool 可读取的文档根目录；其余依赖见 {@link Wired}。 */
    public static Wired create(Path dataDir, Path docsDir, LlmConfig llmConfig, AgentConfig agentConfig) {
        ToolRegistry registry = new ToolRegistry()
                .register(new CalculatorTool())
                .register(new SearchTool())
                .register(new WeatherTool())
                .register(new TodoTool())
                .register(new ReadDocsTool(docsDir));

        SessionRepository sessions = new JsonSessionRepository(dataDir);
        TraceLogger trace = new JsonlTraceLogger(dataDir);
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(registry);
        ContextManager context = new ContextManager(agentConfig, promptBuilder);
        AgentRuntime runtime = new AgentRuntime(
                new OpenAiCompatibleClient(llmConfig), llmConfig, registry,
                sessions, context, trace, agentConfig);
        return new Wired(runtime, registry, sessions, context, trace, llmConfig, agentConfig);
    }

    public record Wired(
            AgentRuntime runtime,
            ToolRegistry tools,
            SessionRepository sessions,
            ContextManager context,
            TraceLogger trace,
            LlmConfig llmConfig,
            AgentConfig agentConfig
    ) {
    }
}
