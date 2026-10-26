package dev.nangua.miniagent;

import dev.nangua.miniagent.agent.AgentConfig;
import dev.nangua.miniagent.agent.AgentFactory;
import dev.nangua.miniagent.cli.AgentCli;
import dev.nangua.miniagent.llm.LlmConfig;
import dev.nangua.miniagent.session.Session;
import dev.nangua.miniagent.trace.TraceEvent;
import dev.nangua.miniagent.trace.TraceType;

import java.nio.file.Path;
import java.util.List;

/**
 * 程序入口：解析命令行参数与环境变量，装配 AgentFactory，再拉起 AgentCli REPL。
 * 退出码约定：0 正常结束；2 用法/配置错误（参数非法、缺少 API Key）；1 启动
 * 或运行期异常，设置环境变量 MINI_AGENT_DEBUG 可额外打印堆栈。
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            printHelp();
            return 2;
        }
        if (options.help) {
            printHelp();
            return 0;
        }

        LlmConfig base = LlmConfig.fromEnvironment();
        if (!base.hasApiKey()) {
            System.err.println("缺少 API Key：请设置环境变量 LLM_API_KEY 或 DEEPSEEK_API_KEY。");
            System.err.println("示例（PowerShell）: $env:LLM_API_KEY=\"sk-...\"");
            System.err.println("Provider 默认使用 DeepSeek: https://api.deepseek.com，模型 deepseek-chat。");
            return 2;
        }
        LlmConfig llmConfig = new LlmConfig(
                // 命令行只允许覆盖 baseUrl 与 model，其余 provider 配置一律取自环境变量。
                options.baseUrl != null ? options.baseUrl : base.baseUrl(),
                base.apiKey(),
                options.model != null ? options.model : base.model(),
                base.temperature(), base.maxOutputTokens(), base.timeout(), base.maxRetries(), base.jsonMode()
        );
        AgentConfig agentConfig = AgentConfig.fromEnvironment();
        Path dataDir = Path.of(options.dataDir).toAbsolutePath().normalize();

        try {
            AgentFactory.Wired wired = AgentFactory.create(dataDir, llmConfig, agentConfig);
            Session session;
            if (options.forceNew) {
                session = wired.sessions().create(options.user,
                        options.title == null ? "新窗口" : options.title);
                wired.trace().log(TraceEvent.of(session, TraceType.SESSION_CREATED,
                        TraceEvent.data("source", "cli", "title", session.getTitle())));
            } else {
                session = resolveSession(wired, options);
            }
            AgentCli cli = new AgentCli(wired, session, options.user, dataDir);
            cli.run();
            return 0;
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            if (System.getenv("MINI_AGENT_DEBUG") != null) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    /**
     * 决定启动时使用哪个 session：显式传了 --session 时必须命中，找不到直接报错；
     * 未传时恢复该用户最近更新的窗口（list 按 updatedAt 降序，取第一个）；
     * 该用户一个窗口都没有则新建「主窗口」。每次选择都补一条 SESSION_LOADED
     * 或 SESSION_CREATED trace 事件。
     */
    private static Session resolveSession(AgentFactory.Wired wired, Options options) {
        if (options.sessionId != null) {
            Session session = wired.sessions().find(options.user, options.sessionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "找不到 session " + options.sessionId + "，可先运行 /sessions 查看。"));
            wired.trace().log(TraceEvent.of(session, TraceType.SESSION_LOADED,
                    TraceEvent.data("source", "cli", "explicit", true)));
            return session;
        }
        List<Session> existing = wired.sessions().list(options.user);
        if (!existing.isEmpty()) {
            Session session = existing.get(0);
            wired.trace().log(TraceEvent.of(session, TraceType.SESSION_LOADED,
                    TraceEvent.data("source", "cli", "explicit", false)));
            return session;
        }
        Session created = wired.sessions().create(options.user, "主窗口");
        wired.trace().log(TraceEvent.of(created, TraceType.SESSION_CREATED,
                TraceEvent.data("source", "cli", "title", created.getTitle())));
        return created;
    }

    private static void printHelp() {
        System.out.println("""
                MiniAgent Java — 从零实现的最小 Agent Runtime

                用法:
                  java -jar target/mini-agent.jar [options]

                选项:
                  --user <id>        用户 ID（默认 alice）
                  --session <id>     恢复指定 session
                  --new              强制新建一个窗口
                  --title <text>     配合 --new 指定窗口标题
                  --data-dir <path>  数据目录（默认 ./data）
                  --model <name>     覆盖 LLM_MODEL（默认 deepseek-chat）
                  --base-url <url>   覆盖 LLM_BASE_URL（默认 https://api.deepseek.com）
                  --help             显示帮助

                环境变量:
                  LLM_API_KEY / DEEPSEEK_API_KEY   必填
                  LLM_BASE_URL, LLM_MODEL           provider 配置
                  AGENT_MAX_STEPS, AGENT_MAX_TOOL_ROUNDS, AGENT_CONTEXT_TOKEN_BUDGET
                """);
    }

    static final class Options {
        String user = System.getenv().getOrDefault("MINI_AGENT_USER", "alice");
        String sessionId;
        String title;
        String dataDir = System.getenv().getOrDefault("MINI_AGENT_DATA_DIR", "data");
        String model;
        String baseUrl;
        boolean forceNew;
        boolean help;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--user" -> options.user = value(args, ++i, arg);
                    case "--session" -> options.sessionId = value(args, ++i, arg);
                    case "--title" -> options.title = value(args, ++i, arg);
                    case "--data-dir" -> options.dataDir = value(args, ++i, arg);
                    case "--model" -> options.model = value(args, ++i, arg);
                    case "--base-url" -> options.baseUrl = value(args, ++i, arg);
                    case "--new" -> options.forceNew = true;
                    case "--help", "-h" -> options.help = true;
                    default -> {
                        if (arg.startsWith("--") && arg.contains("=")) {
                            String[] parts = arg.split("=", 2);
                            switch (parts[0]) {
                                case "--user" -> options.user = parts[1];
                                case "--session" -> options.sessionId = parts[1];
                                case "--title" -> options.title = parts[1];
                                case "--data-dir" -> options.dataDir = parts[1];
                                case "--model" -> options.model = parts[1];
                                case "--base-url" -> options.baseUrl = parts[1];
                                default -> throw new IllegalArgumentException("未知选项: " + arg);
                            }
                        } else {
                            throw new IllegalArgumentException("未知选项: " + arg);
                        }
                    }
                }
            }
            // 与 JsonSessionRepository 的 ID 白名单一致：在入口就拦下非法 user，避免拖到首次落盘才在存储层报错。
            if (!options.user.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("--user 只能包含字母、数字、下划线或短横线");
            }
            return options;
        }

        private static String value(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException(name + " 缺少值");
            }
            return args[index];
        }
    }
}
