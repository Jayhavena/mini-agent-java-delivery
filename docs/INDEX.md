# 主源码方法索引

> 按问题定位源码的补充索引，不要求通读。返回[项目说明与验收入口](<../README.md>)；测试入口见[测试目录](<../src/test/java/dev/nangua/miniagent/>)。

## 怎样用

- **A：先理解主线**，包括运行时、协议、状态与关键契约；不是每个 getter 都要精读。
- **B：按工程问题补读**，包括启动、HTTP、持久化、观测与错误边界。
- **C：专题选读**，如计算器算法、mock 数据、演示与辅助实现；不阻塞 Agent 主线。
- 每个显式方法/构造器独占一行；签名保留返回类型、参数、可见性及重载差异，`Owner` 区分嵌套类型。
- 链接的 `#L起始-L结束` 指向当前 UTF-8 源码的物理行，起点可能包括方法注解。在 IDE 中打开文件后也可用 Ctrl+G 输入起始行。
- record 自动生成的访问器、equals/hashCode/toString、隐式构造器及枚举 values/valueOf **没有源码声明，不伪造行号**；显式紧凑构造器单独标识。类型声明入口也列出，纯数据类不会漏掉。
- 不把“token 估算”“文件原子替换”“Schema 提示”误读为严格预算保证、并发事务或通用参数验证。

## 第一遍：只走一次计算器请求

1. 从 [callsCalculatorThenReturnsFinalAndFeedsResultBack](<../src/test/java/dev/nangua/miniagent/agent/AgentRuntimeTest.java#L50-L75>) 进入；这是预设模型响应的离线测试，不需要 Key。
2. 用下方文件入口依次找：AgentFactory.create → AgentRuntime.chat → ContextManager.build → LlmClient.complete → AgentOutputParser.parse → ToolRegistry.execute → CalculatorTool.execute。
3. 第二次模型请求应看到第一次工具结果；闭卷复述 `USER → ASSISTANT_TOOL_CALL → TOOL_RESULT → ASSISTANT_FINAL`，解释为何是 2 steps / 1 tool round。

```powershell
mvn -Dtest=AgentRuntimeTest test
```

## 覆盖与维护

- 覆盖：**49 个主源码文件 / 58 个显式类型 / 256 个显式方法与构造器声明**。
- 源码指纹（按路径与 LF 规范化的 UTF-8 内容计算 SHA-256）：`f1b6f051ae789a5a6749f78c0836bb49f39ccebc876a78f2522b917fc0a689da`。
- 生成器使用 JDK 编译器的语法树，不靠正则猜方法，不编译业务代码，也不访问模型/网络。
- 维护脚本：[GenerateSourceIndex.java](<../scripts/GenerateSourceIndex.java>)。修改源码后重新生成并检查；此检查针对本索引，正文中的源码链接仍需随对应修改复核。

```powershell
java scripts/GenerateSourceIndex.java
java scripts/GenerateSourceIndex.java --check
```

## 文件导航

| 文件 | 阅读层级与职责 | 显式方法/构造器 |
|---|---|---:|
| [Main.java](#file-main) | B｜启动参数与会话选择 | 7 |
| [AgentConfig.java](#file-agent-agentconfig) | A｜上限、预算与环境配置 | 5 |
| [AgentException.java](#file-agent-agentexception) | B｜运行时异常边界 | 2 |
| [AgentFactory.java](#file-agent-agentfactory) | A｜显式装配与工具注册 | 3 |
| [AgentReply.java](#file-agent-agentreply) | A｜turn 返回值与工具调用记录 | 1 |
| [AgentRuntime.java](#file-agent-agentruntime) | A｜请求主循环与终止分支 | 5 |
| [AgentStopReason.java](#file-agent-agentstopreason) | A｜终止原因 | 0 |
| [AgentCli.java](#file-cli-agentcli) | B｜REPL、会话命令与终端展示 | 17 |
| [ContextManager.java](#file-context-contextmanager) | A｜消息构建、裁剪与压缩游标 | 11 |
| [ContextWindow.java](#file-context-contextwindow) | A｜单次请求上下文及统计 | 1 |
| [SessionSummarizer.java](#file-context-sessionsummarizer) | A｜确定性摘要与合并 | 4 |
| [SystemPromptBuilder.java](#file-context-systempromptbuilder) | A｜工具说明、记忆与输出协议 | 3 |
| [TokenEstimator.java](#file-context-tokenestimator) | A｜启发式 token 估算 | 4 |
| [LiveDemo.java](#file-demo-livedemo) | C｜真实 API 双窗口演示（可能付费） | 3 |
| [ChatMessage.java](#file-llm-chatmessage) | A｜发给模型的角色消息 | 4 |
| [LlmClient.java](#file-llm-llmclient) | A｜模型调用端口 | 1 |
| [LlmConfig.java](#file-llm-llmconfig) | B｜provider 参数与环境配置 | 9 |
| [LlmException.java](#file-llm-llmexception) | B｜模型错误及 HTTP 状态 | 6 |
| [LlmRequest.java](#file-llm-llmrequest) | A｜模型请求数据 | 1 |
| [LlmResponse.java](#file-llm-llmresponse) | A｜模型响应与用量数据 | 2 |
| [OpenAiCompatibleClient.java](#file-llm-openaicompatibleclient) | B｜HTTP、重试与响应解析 | 11 |
| [AgentOutput.java](#file-parse-agentoutput) | A｜ToolCall / FinalAnswer 协议类型 | 3 |
| [AgentOutputParser.java](#file-parse-agentoutputparser) | A｜文本协议提取与字段校验 | 8 |
| [ParseException.java](#file-parse-parseexception) | B｜协议解析错误 | 2 |
| [JsonSessionRepository.java](#file-session-jsonsessionrepository) | B｜按用户/窗口存储、替换与加载 | 11 |
| [MessageKind.java](#file-session-messagekind) | A｜消息类型 | 0 |
| [Session.java](#file-session-session) | A｜会话消息、摘要与私有工具状态 | 24 |
| [SessionMessage.java](#file-session-sessionmessage) | A｜持久化消息及工厂方法 | 7 |
| [SessionRepository.java](#file-session-sessionrepository) | A｜会话持久化端口 | 5 |
| [SessionStoreException.java](#file-session-sessionstoreexception) | B｜会话存储错误 | 2 |
| [CalculatorTool.java](#file-tool-calculatortool) | A｜完整工具调用的最小样例 | 5 |
| [ExpressionEvaluator.java](#file-tool-expressionevaluator) | C｜独立的表达式解析算法 | 17 |
| [ReadDocsTool.java](#file-tool-readdocstool) | B｜受限 Markdown 文件读取 | 5 |
| [Schemas.java](#file-tool-schemas) | C｜工具参数 Schema 构建辅助 | 7 |
| [SearchTool.java](#file-tool-searchtool) | C｜本地 mock 搜索 | 5 |
| [TodoTool.java](#file-tool-todotool) | B｜会话级有状态工具 | 12 |
| [Tool.java](#file-tool-tool) | A｜工具契约与默认 spec | 5 |
| [ToolContext.java](#file-tool-toolcontext) | A｜当前会话的工具状态引用 | 1 |
| [ToolException.java](#file-tool-toolexception) | B｜工具可报告错误 | 2 |
| [ToolRegistry.java](#file-tool-toolregistry) | A｜名称注册、查询与分发 | 7 |
| [ToolResult.java](#file-tool-toolresult) | A｜工具成功/失败结果 | 3 |
| [ToolSpec.java](#file-tool-toolspec) | A｜模型可见的工具说明 | 0 |
| [WeatherTool.java](#file-tool-weathertool) | C｜本地 mock 天气 | 4 |
| [JsonlTraceLogger.java](#file-trace-jsonltracelogger) | B｜JSONL 写入与尾部查询 | 6 |
| [NoopTraceLogger.java](#file-trace-nooptracelogger) | C｜空观测实现 | 2 |
| [TraceEvent.java](#file-trace-traceevent) | B｜事件数据与构造辅助 | 3 |
| [TraceLogger.java](#file-trace-tracelogger) | B｜观测端口 | 3 |
| [TraceType.java](#file-trace-tracetype) | B｜事件类型 | 0 |
| [Json.java](#file-util-json) | B｜统一 JSON 序列化与预览 | 7 |

## 入口

<a id="file-main"></a>

### [Main.java](<../src/main/java/dev/nangua/miniagent/Main.java>)

- 类型：[Main · class :19](<../src/main/java/dev/nangua/miniagent/Main.java#L19>)。
- 类型：[Main.Options · class :136](<../src/main/java/dev/nangua/miniagent/Main.java#L136>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `Main` | `private Main()` | [20–21](<../src/main/java/dev/nangua/miniagent/Main.java#L20-L21>) |
| `Main` | `public static void main(String[] args)` | [23–28](<../src/main/java/dev/nangua/miniagent/Main.java#L23-L28>) |
| `Main` | `static int run(String[] args)` | [30–82](<../src/main/java/dev/nangua/miniagent/Main.java#L30-L82>) |
| `Main` | `private static Session resolveSession(AgentFactory.Wired wired, Options options)` | [90–110](<../src/main/java/dev/nangua/miniagent/Main.java#L90-L110>) |
| `Main` | `private static void printHelp()` | [112–134](<../src/main/java/dev/nangua/miniagent/Main.java#L112-L134>) |
| `Main.Options` | `static Options parse(String[] args)` | [146–182](<../src/main/java/dev/nangua/miniagent/Main.java#L146-L182>) |
| `Main.Options` | `private static String value(String[] args, int index, String name)` | [184–189](<../src/main/java/dev/nangua/miniagent/Main.java#L184-L189>) |

## agent

<a id="file-agent-agentconfig"></a>

### [AgentConfig.java](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java>)

- 类型：[AgentConfig · record :13](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java#L13>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentConfig` | `public AgentConfig [compact constructor]` | [21–40](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java#L21-L40>) |
| `AgentConfig` | `public static AgentConfig defaults()` | [42–44](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java#L42-L44>) |
| `AgentConfig` | `public static AgentConfig fromEnvironment()` | [46–48](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java#L46-L48>) |
| `AgentConfig` | `public static AgentConfig fromEnvironment(Map<String, String> env)` | [50–59](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java#L50-L59>) |
| `AgentConfig` | `private static int parseInt(String value, int fallback)` | [61–70](<../src/main/java/dev/nangua/miniagent/agent/AgentConfig.java#L61-L70>) |

<a id="file-agent-agentexception"></a>

### [AgentException.java](<../src/main/java/dev/nangua/miniagent/agent/AgentException.java>)

- 类型：[AgentException · class :5](<../src/main/java/dev/nangua/miniagent/agent/AgentException.java#L5>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentException` | `public AgentException(String message)` | [6–8](<../src/main/java/dev/nangua/miniagent/agent/AgentException.java#L6-L8>) |
| `AgentException` | `public AgentException(String message, Throwable cause)` | [10–12](<../src/main/java/dev/nangua/miniagent/agent/AgentException.java#L10-L12>) |

<a id="file-agent-agentfactory"></a>

### [AgentFactory.java](<../src/main/java/dev/nangua/miniagent/agent/AgentFactory.java>)

- 类型：[AgentFactory · class :26](<../src/main/java/dev/nangua/miniagent/agent/AgentFactory.java#L26>)。
- 类型：[AgentFactory.Wired · record :53](<../src/main/java/dev/nangua/miniagent/agent/AgentFactory.java#L53>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentFactory` | `private AgentFactory()` | [27–28](<../src/main/java/dev/nangua/miniagent/agent/AgentFactory.java#L27-L28>) |
| `AgentFactory` | `public static Wired create(Path dataDir, LlmConfig llmConfig, AgentConfig agentConfig)` | [30–32](<../src/main/java/dev/nangua/miniagent/agent/AgentFactory.java#L30-L32>) |
| `AgentFactory` | `public static Wired create(Path dataDir, Path docsDir, LlmConfig llmConfig, AgentConfig agentConfig)` | [35–51](<../src/main/java/dev/nangua/miniagent/agent/AgentFactory.java#L35-L51>) |

<a id="file-agent-agentreply"></a>

### [AgentReply.java](<../src/main/java/dev/nangua/miniagent/agent/AgentReply.java>)

- 类型：[AgentReply · record :12](<../src/main/java/dev/nangua/miniagent/agent/AgentReply.java#L12>)。
- 类型：[AgentReply.ToolCallRecord · record :30](<../src/main/java/dev/nangua/miniagent/agent/AgentReply.java#L30>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentReply` | `public AgentReply [compact constructor]` | [23–27](<../src/main/java/dev/nangua/miniagent/agent/AgentReply.java#L23-L27>) |

<a id="file-agent-agentruntime"></a>

### [AgentRuntime.java](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java>)

- 类型：[AgentRuntime · class :40](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java#L40>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentRuntime` | `public AgentRuntime(LlmClient llm, LlmConfig llmConfig, ToolRegistry tools, SessionRepository sessions, ContextManager context, TraceLogger trace, AgentConfig config)` | [50–64](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java#L50-L64>) |
| `AgentRuntime` | `public AgentReply chat(Session session, String userInput)` | [74–261](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java#L74-L261>) |
| `AgentRuntime` | `private AgentReply fallback(Session session, long started, int steps, List<AgentReply.ToolCallRecord> toolRecords, AgentStopReason reason, String answer)` | [268–285](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java#L268-L285>) |
| `AgentRuntime` | `private void persist(Session session, String reason)` | [287–293](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java#L287-L293>) |
| `AgentRuntime` | `private static long elapsedMs(long startedNanos)` | [295–297](<../src/main/java/dev/nangua/miniagent/agent/AgentRuntime.java#L295-L297>) |

<a id="file-agent-agentstopreason"></a>

### [AgentStopReason.java](<../src/main/java/dev/nangua/miniagent/agent/AgentStopReason.java>)

- 类型：[AgentStopReason · enum :8](<../src/main/java/dev/nangua/miniagent/agent/AgentStopReason.java#L8>)。

无显式方法/构造器；按类型声明查看数据字段或枚举常量。

## cli

<a id="file-cli-agentcli"></a>

### [AgentCli.java](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java>)

- 类型：[AgentCli · class :31](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L31>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentCli` | `public AgentCli(AgentFactory.Wired wired, Session session, String userId, Path dataDir)` | [41–44](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L41-L44>) |
| `AgentCli` | `AgentCli(AgentFactory.Wired wired, Session session, String userId, Path dataDir, BufferedReader in, PrintStream out)` | [46–54](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L46-L54>) |
| `AgentCli` | `public void run()` | [56–84](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L56-L84>) |
| `AgentCli` | `private void banner()` | [86–97](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L86-L97>) |
| `AgentCli` | `private void handleChat(String input)` | [104–133](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L104-L133>) |
| `AgentCli` | `private boolean handleCommand(String input)` | [136–157](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L136-L157>) |
| `AgentCli` | `private void newSession(String title)` | [159–165](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L159-L165>) |
| `AgentCli` | `private void useSession(String token)` | [171–196](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L171-L196>) |
| `AgentCli` | `private void listSessions()` | [198–213](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L198-L213>) |
| `AgentCli` | `private void printHistory(String argument)` | [215–228](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L215-L228>) |
| `AgentCli` | `private static String preview(SessionMessage message)` | [230–236](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L230-L236>) |
| `AgentCli` | `private void printTrace(String argument)` | [238–252](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L238-L252>) |
| `AgentCli` | `private void printState()` | [254–260](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L254-L260>) |
| `AgentCli` | `private void compact()` | [262–273](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L262-L273>) |
| `AgentCli` | `private void rename(String title)` | [275–283](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L275-L283>) |
| `AgentCli` | `private void printHelp()` | [285–301](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L285-L301>) |
| `AgentCli` | `private static int parseLimit(String argument, int fallback)` | [303–312](<../src/main/java/dev/nangua/miniagent/cli/AgentCli.java#L303-L312>) |

## context

<a id="file-context-contextmanager"></a>

### [ContextManager.java](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java>)

- 类型：[ContextManager · class :29](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L29>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ContextManager` | `public ContextManager(AgentConfig config, SystemPromptBuilder promptBuilder)` | [39–42](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L39-L42>) |
| `ContextManager` | `public boolean maybeCompact(Session session, TraceLogger trace)` | [51–53](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L51-L53>) |
| `ContextManager` | `public boolean forceCompact(Session session, TraceLogger trace)` | [56–58](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L56-L58>) |
| `ContextManager` | `private boolean compact(Session session, TraceLogger trace, boolean force)` | [60–99](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L60-L99>) |
| `ContextManager` | `public ContextWindow build(Session session)` | [105–114](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L105-L114>) |
| `ContextManager` | `private List<ChatMessage> buildMessages(Session session, boolean enforceBudget)` | [116–143](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L116-L143>) |
| `ContextManager` | `private ChatMessage toChatMessage(SessionMessage message)` | [150–167](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L150-L167>) |
| `ContextManager` | `private int estimatedRecent(Session session, int from, int to)` | [169–175](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L169-L175>) |
| `ContextManager` | `private static String cap(String value, int max)` | [177–182](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L177-L182>) |
| `ContextManager` | `public int estimateFullPrompt(Session session)` | [185–187](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L185-L187>) |
| `ContextManager` | `public Map<String, Object> stats(Session session)` | [189–196](<../src/main/java/dev/nangua/miniagent/context/ContextManager.java#L189-L196>) |

<a id="file-context-contextwindow"></a>

### [ContextWindow.java](<../src/main/java/dev/nangua/miniagent/context/ContextWindow.java>)

- 类型：[ContextWindow · record :14](<../src/main/java/dev/nangua/miniagent/context/ContextWindow.java#L14>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ContextWindow` | `public ContextWindow [compact constructor]` | [21–23](<../src/main/java/dev/nangua/miniagent/context/ContextWindow.java#L21-L23>) |

<a id="file-context-sessionsummarizer"></a>

### [SessionSummarizer.java](<../src/main/java/dev/nangua/miniagent/context/SessionSummarizer.java>)

- 类型：[SessionSummarizer · class :16](<../src/main/java/dev/nangua/miniagent/context/SessionSummarizer.java#L16>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `SessionSummarizer` | `public String digest(List<SessionMessage> messages)` | [19–42](<../src/main/java/dev/nangua/miniagent/context/SessionSummarizer.java#L19-L42>) |
| `SessionSummarizer` | `public String merge(String existing, String newDigest, int maxChars)` | [46–65](<../src/main/java/dev/nangua/miniagent/context/SessionSummarizer.java#L46-L65>) |
| `SessionSummarizer` | `private static String cap(String value, int maxChars)` | [67–69](<../src/main/java/dev/nangua/miniagent/context/SessionSummarizer.java#L67-L69>) |
| `SessionSummarizer` | `private static String trim(String value, int maxChars)` | [71–74](<../src/main/java/dev/nangua/miniagent/context/SessionSummarizer.java#L71-L74>) |

<a id="file-context-systempromptbuilder"></a>

### [SystemPromptBuilder.java](<../src/main/java/dev/nangua/miniagent/context/SystemPromptBuilder.java>)

- 类型：[SystemPromptBuilder · class :21](<../src/main/java/dev/nangua/miniagent/context/SystemPromptBuilder.java#L21>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `SystemPromptBuilder` | `public SystemPromptBuilder(ToolRegistry registry)` | [24–26](<../src/main/java/dev/nangua/miniagent/context/SystemPromptBuilder.java#L24-L26>) |
| `SystemPromptBuilder` | `public String build(Session session)` | [28–87](<../src/main/java/dev/nangua/miniagent/context/SystemPromptBuilder.java#L28-L87>) |
| `SystemPromptBuilder` | `private static String toolStateBrief(Session session)` | [89–103](<../src/main/java/dev/nangua/miniagent/context/SystemPromptBuilder.java#L89-L103>) |

<a id="file-context-tokenestimator"></a>

### [TokenEstimator.java](<../src/main/java/dev/nangua/miniagent/context/TokenEstimator.java>)

- 类型：[TokenEstimator · class :15](<../src/main/java/dev/nangua/miniagent/context/TokenEstimator.java#L15>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `TokenEstimator` | `public int estimate(String text)` | [16–31](<../src/main/java/dev/nangua/miniagent/context/TokenEstimator.java#L16-L31>) |
| `TokenEstimator` | `public int estimateMessages(List<ChatMessage> messages)` | [33–40](<../src/main/java/dev/nangua/miniagent/context/TokenEstimator.java#L33-L40>) |
| `TokenEstimator` | `private static boolean isCjk(char c)` | [42–48](<../src/main/java/dev/nangua/miniagent/context/TokenEstimator.java#L42-L48>) |
| `TokenEstimator` | `private static boolean isWidePunctuation(char c)` | [50–52](<../src/main/java/dev/nangua/miniagent/context/TokenEstimator.java#L50-L52>) |

## demo

<a id="file-demo-livedemo"></a>

### [LiveDemo.java](<../src/main/java/dev/nangua/miniagent/demo/LiveDemo.java>)

- 类型：[LiveDemo · class :27](<../src/main/java/dev/nangua/miniagent/demo/LiveDemo.java#L27>)。
- 类型：[LiveDemo.Turn · record :149](<../src/main/java/dev/nangua/miniagent/demo/LiveDemo.java#L149>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `LiveDemo` | `public static void main(String[] args) throws Exception` | [30–113](<../src/main/java/dev/nangua/miniagent/demo/LiveDemo.java#L30-L113>) |
| `LiveDemo` | `private static String repositoryRelativePath(Path path)` | [116–129](<../src/main/java/dev/nangua/miniagent/demo/LiveDemo.java#L116-L129>) |
| `LiveDemo` | `private static Turn turn(AgentFactory.Wired wired, Session session, String input)` | [131–147](<../src/main/java/dev/nangua/miniagent/demo/LiveDemo.java#L131-L147>) |

## llm

<a id="file-llm-chatmessage"></a>

### [ChatMessage.java](<../src/main/java/dev/nangua/miniagent/llm/ChatMessage.java>)

- 类型：[ChatMessage · record :11](<../src/main/java/dev/nangua/miniagent/llm/ChatMessage.java#L11>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ChatMessage` | `public ChatMessage [compact constructor]` | [12–15](<../src/main/java/dev/nangua/miniagent/llm/ChatMessage.java#L12-L15>) |
| `ChatMessage` | `public static ChatMessage system(String content)` | [17–19](<../src/main/java/dev/nangua/miniagent/llm/ChatMessage.java#L17-L19>) |
| `ChatMessage` | `public static ChatMessage user(String content)` | [21–23](<../src/main/java/dev/nangua/miniagent/llm/ChatMessage.java#L21-L23>) |
| `ChatMessage` | `public static ChatMessage assistant(String content)` | [25–27](<../src/main/java/dev/nangua/miniagent/llm/ChatMessage.java#L25-L27>) |

<a id="file-llm-llmclient"></a>

### [LlmClient.java](<../src/main/java/dev/nangua/miniagent/llm/LlmClient.java>)

- 类型：[LlmClient · interface :9](<../src/main/java/dev/nangua/miniagent/llm/LlmClient.java#L9>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `LlmClient` | `LlmResponse complete(LlmRequest request) throws LlmException` | [11–11](<../src/main/java/dev/nangua/miniagent/llm/LlmClient.java#L11-L11>) |

<a id="file-llm-llmconfig"></a>

### [LlmConfig.java](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java>)

- 类型：[LlmConfig · record :13](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L13>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `LlmConfig` | `public LlmConfig [compact constructor]` | [26–39](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L26-L39>) |
| `LlmConfig` | `public static LlmConfig fromEnvironment()` | [41–43](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L41-L43>) |
| `LlmConfig` | `public static LlmConfig fromEnvironment(Map<String, String> env)` | [45–55](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L45-L55>) |
| `LlmConfig` | `public boolean hasApiKey()` | [57–59](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L57-L59>) |
| `LlmConfig` | `public String maskedKey()` | [61–69](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L61-L69>) |
| `LlmConfig` | `private static String requireText(String value, String name)` | [71–76](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L71-L76>) |
| `LlmConfig` | `private static String firstNonBlank(String... values)` | [78–85](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L78-L85>) |
| `LlmConfig` | `private static int parseInt(String value, int fallback)` | [87–96](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L87-L96>) |
| `LlmConfig` | `private static double parseDouble(String value, double fallback)` | [98–107](<../src/main/java/dev/nangua/miniagent/llm/LlmConfig.java#L98-L107>) |

<a id="file-llm-llmexception"></a>

### [LlmException.java](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java>)

- 类型：[LlmException · class :5](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L5>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `LlmException` | `public LlmException(String message)` | [8–10](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L8-L10>) |
| `LlmException` | `public LlmException(String message, Throwable cause)` | [12–15](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L12-L15>) |
| `LlmException` | `public LlmException(String message, int httpStatus)` | [17–19](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L17-L19>) |
| `LlmException` | `public LlmException(String message, int httpStatus, Throwable cause)` | [21–24](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L21-L24>) |
| `LlmException` | `public int httpStatus()` | [26–28](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L26-L28>) |
| `LlmException` | `public boolean isAuthenticationError()` | [30–32](<../src/main/java/dev/nangua/miniagent/llm/LlmException.java#L30-L32>) |

<a id="file-llm-llmrequest"></a>

### [LlmRequest.java](<../src/main/java/dev/nangua/miniagent/llm/LlmRequest.java>)

- 类型：[LlmRequest · record :9](<../src/main/java/dev/nangua/miniagent/llm/LlmRequest.java#L9>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `LlmRequest` | `public LlmRequest [compact constructor]` | [10–18](<../src/main/java/dev/nangua/miniagent/llm/LlmRequest.java#L10-L18>) |

<a id="file-llm-llmresponse"></a>

### [LlmResponse.java](<../src/main/java/dev/nangua/miniagent/llm/LlmResponse.java>)

- 类型：[LlmResponse · record :17](<../src/main/java/dev/nangua/miniagent/llm/LlmResponse.java#L17>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `LlmResponse` | `public LlmResponse [compact constructor]` | [25–30](<../src/main/java/dev/nangua/miniagent/llm/LlmResponse.java#L25-L30>) |
| `LlmResponse` | `public int totalTokens()` | [32–37](<../src/main/java/dev/nangua/miniagent/llm/LlmResponse.java#L32-L37>) |

<a id="file-llm-openaicompatibleclient"></a>

### [OpenAiCompatibleClient.java](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java>)

- 类型：[OpenAiCompatibleClient · class :27](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L27>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `OpenAiCompatibleClient` | `public OpenAiCompatibleClient(LlmConfig config)` | [32–37](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L32-L37>) |
| `OpenAiCompatibleClient` | `OpenAiCompatibleClient(LlmConfig config, HttpClient httpClient)` | [39–43](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L39-L43>) |
| `OpenAiCompatibleClient` | `@Override public LlmResponse complete(LlmRequest request) throws LlmException` | [45–91](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L45-L91>) |
| `OpenAiCompatibleClient` | `private String buildRequestBody(LlmRequest request)` | [93–112](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L93-L112>) |
| `OpenAiCompatibleClient` | `private LlmResponse parseResponse(String responseBody)` | [114–145](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L114-L145>) |
| `OpenAiCompatibleClient` | `private static String extractContent(JsonNode node)` | [148–167](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L148-L167>) |
| `OpenAiCompatibleClient` | `private LlmException httpError(HttpResponse<String> response)` | [169–179](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L169-L179>) |
| `OpenAiCompatibleClient` | `private static boolean isRetryable(int status)` | [181–183](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L181-L183>) |
| `OpenAiCompatibleClient` | `private static long retryAfterMillis(HttpResponse<String> response)` | [185–198](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L185-L198>) |
| `OpenAiCompatibleClient` | `private static void sleepBeforeRetry(int attempt, long retryAfterMillis)` | [200–208](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L200-L208>) |
| `OpenAiCompatibleClient` | `static URI endpoint(String baseUrl)` | [211–221](<../src/main/java/dev/nangua/miniagent/llm/OpenAiCompatibleClient.java#L211-L221>) |

## parse

<a id="file-parse-agentoutput"></a>

### [AgentOutput.java](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java>)

- 类型：[AgentOutput · interface :11](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java#L11>)。
- 类型：[AgentOutput.ToolCall · record :14](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java#L14>)。
- 类型：[AgentOutput.FinalAnswer · record :21](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java#L21>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentOutput` | `String thought()` | [12–12](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java#L12-L12>) |
| `AgentOutput.ToolCall` | `public ToolCall [compact constructor]` | [15–18](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java#L15-L18>) |
| `AgentOutput.FinalAnswer` | `public FinalAnswer [compact constructor]` | [22–25](<../src/main/java/dev/nangua/miniagent/parse/AgentOutput.java#L22-L25>) |

<a id="file-parse-agentoutputparser"></a>

### [AgentOutputParser.java](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java>)

- 类型：[AgentOutputParser · class :21](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L21>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `AgentOutputParser` | `public AgentOutput parse(LlmResponse response)` | [27–38](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L27-L38>) |
| `AgentOutputParser` | `public AgentOutput parse(String raw)` | [40–76](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L40-L76>) |
| `AgentOutputParser` | `private AgentOutput parseToolCall(JsonNode root, String thought)` | [78–106](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L78-L106>) |
| `AgentOutputParser` | `private AgentOutput parseFinal(JsonNode root, String thought)` | [108–125](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L108-L125>) |
| `AgentOutputParser` | `static String extractJsonObject(String raw)` | [128–154](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L128-L154>) |
| `AgentOutputParser` | `private static String text(JsonNode root, String field)` | [156–159](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L156-L159>) |
| `AgentOutputParser` | `private static String firstText(JsonNode root, String... fields)` | [161–169](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L161-L169>) |
| `AgentOutputParser` | `private static String truncate(String value, int max)` | [171–176](<../src/main/java/dev/nangua/miniagent/parse/AgentOutputParser.java#L171-L176>) |

<a id="file-parse-parseexception"></a>

### [ParseException.java](<../src/main/java/dev/nangua/miniagent/parse/ParseException.java>)

- 类型：[ParseException · class :5](<../src/main/java/dev/nangua/miniagent/parse/ParseException.java#L5>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ParseException` | `public ParseException(String message)` | [6–8](<../src/main/java/dev/nangua/miniagent/parse/ParseException.java#L6-L8>) |
| `ParseException` | `public ParseException(String message, Throwable cause)` | [10–12](<../src/main/java/dev/nangua/miniagent/parse/ParseException.java#L10-L12>) |

## session

<a id="file-session-jsonsessionrepository"></a>

### [JsonSessionRepository.java](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java>)

- 类型：[JsonSessionRepository · class :29](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L29>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `JsonSessionRepository` | `public JsonSessionRepository(Path dataRoot)` | [36–38](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L36-L38>) |
| `JsonSessionRepository` | `public JsonSessionRepository(Path root, ObjectMapper mapper)` | [40–48](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L40-L48>) |
| `JsonSessionRepository` | `public Path root()` | [50–52](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L50-L52>) |
| `JsonSessionRepository` | `@Override public Session create(String userId, String title)` | [54–64](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L54-L64>) |
| `JsonSessionRepository` | `@Override public void save(Session session)` | [66–91](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L66-L91>) |
| `JsonSessionRepository` | `@Override public Optional<Session> find(String userId, String sessionId)` | [99–120](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L99-L120>) |
| `JsonSessionRepository` | `@Override public List<Session> list(String userId)` | [122–148](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L122-L148>) |
| `JsonSessionRepository` | `@Override public void delete(String userId, String sessionId)` | [150–164](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L150-L164>) |
| `JsonSessionRepository` | `private Path pathFor(String userId, String sessionId)` | [166–168](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L166-L168>) |
| `JsonSessionRepository` | `private ReentrantLock lockFor(Path file)` | [170–173](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L170-L173>) |
| `JsonSessionRepository` | `private static String validate(String value, String field)` | [175–180](<../src/main/java/dev/nangua/miniagent/session/JsonSessionRepository.java#L175-L180>) |

<a id="file-session-messagekind"></a>

### [MessageKind.java](<../src/main/java/dev/nangua/miniagent/session/MessageKind.java>)

- 类型：[MessageKind · enum :9](<../src/main/java/dev/nangua/miniagent/session/MessageKind.java#L9>)。

无显式方法/构造器；按类型声明查看数据字段或枚举常量。

<a id="file-session-session"></a>

### [Session.java](<../src/main/java/dev/nangua/miniagent/session/Session.java>)

- 类型：[Session · class :22](<../src/main/java/dev/nangua/miniagent/session/Session.java#L22>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `Session` | `public Session()` | [37–38](<../src/main/java/dev/nangua/miniagent/session/Session.java#L37-L38>) |
| `Session` | `public Session(String id, String userId, String title)` | [40–46](<../src/main/java/dev/nangua/miniagent/session/Session.java#L40-L46>) |
| `Session` | `public String getId()` | [48–50](<../src/main/java/dev/nangua/miniagent/session/Session.java#L48-L50>) |
| `Session` | `public void setId(String id)` | [52–54](<../src/main/java/dev/nangua/miniagent/session/Session.java#L52-L54>) |
| `Session` | `public String getUserId()` | [56–58](<../src/main/java/dev/nangua/miniagent/session/Session.java#L56-L58>) |
| `Session` | `public void setUserId(String userId)` | [60–62](<../src/main/java/dev/nangua/miniagent/session/Session.java#L60-L62>) |
| `Session` | `public String getTitle()` | [64–66](<../src/main/java/dev/nangua/miniagent/session/Session.java#L64-L66>) |
| `Session` | `public void setTitle(String title)` | [68–70](<../src/main/java/dev/nangua/miniagent/session/Session.java#L68-L70>) |
| `Session` | `public Instant getCreatedAt()` | [72–74](<../src/main/java/dev/nangua/miniagent/session/Session.java#L72-L74>) |
| `Session` | `public void setCreatedAt(Instant createdAt)` | [76–78](<../src/main/java/dev/nangua/miniagent/session/Session.java#L76-L78>) |
| `Session` | `public Instant getUpdatedAt()` | [80–82](<../src/main/java/dev/nangua/miniagent/session/Session.java#L80-L82>) |
| `Session` | `public void setUpdatedAt(Instant updatedAt)` | [84–86](<../src/main/java/dev/nangua/miniagent/session/Session.java#L84-L86>) |
| `Session` | `public List<SessionMessage> getMessages()` | [88–93](<../src/main/java/dev/nangua/miniagent/session/Session.java#L88-L93>) |
| `Session` | `public void setMessages(List<SessionMessage> messages)` | [95–97](<../src/main/java/dev/nangua/miniagent/session/Session.java#L95-L97>) |
| `Session` | `public String getSummary()` | [99–101](<../src/main/java/dev/nangua/miniagent/session/Session.java#L99-L101>) |
| `Session` | `public void setSummary(String summary)` | [103–105](<../src/main/java/dev/nangua/miniagent/session/Session.java#L103-L105>) |
| `Session` | `public int getCompactedMessages()` | [107–109](<../src/main/java/dev/nangua/miniagent/session/Session.java#L107-L109>) |
| `Session` | `public void setCompactedMessages(int compactedMessages)` | [111–113](<../src/main/java/dev/nangua/miniagent/session/Session.java#L111-L113>) |
| `Session` | `public ObjectNode getToolState()` | [115–120](<../src/main/java/dev/nangua/miniagent/session/Session.java#L115-L120>) |
| `Session` | `public void setToolState(ObjectNode toolState)` | [122–124](<../src/main/java/dev/nangua/miniagent/session/Session.java#L122-L124>) |
| `Session` | `public Session append(SessionMessage message)` | [126–130](<../src/main/java/dev/nangua/miniagent/session/Session.java#L126-L130>) |
| `Session` | `public void clearMessages()` | [132–137](<../src/main/java/dev/nangua/miniagent/session/Session.java#L132-L137>) |
| `Session` | `public int messageCount()` | [139–141](<../src/main/java/dev/nangua/miniagent/session/Session.java#L139-L141>) |
| `Session` | `public String statePreview()` | [143–145](<../src/main/java/dev/nangua/miniagent/session/Session.java#L143-L145>) |

<a id="file-session-sessionmessage"></a>

### [SessionMessage.java](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java>)

- 类型：[SessionMessage · record :20](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L20>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `SessionMessage` | `public SessionMessage [compact constructor]` | [32–39](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L32-L39>) |
| `SessionMessage` | `public static SessionMessage user(String text)` | [41–43](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L41-L43>) |
| `SessionMessage` | `public static SessionMessage assistantToolCall(String thought, String toolName, ObjectNode arguments, int step)` | [45–56](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L45-L56>) |
| `SessionMessage` | `public static SessionMessage toolResult(String toolName, boolean ok, String content, int step)` | [58–61](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L58-L61>) |
| `SessionMessage` | `public static SessionMessage assistantFinal(String thought, String answer, int step)` | [63–66](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L63-L66>) |
| `SessionMessage` | `public static SessionMessage contextNote(String content)` | [68–71](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L68-L71>) |
| `SessionMessage` | `private static String newId()` | [73–75](<../src/main/java/dev/nangua/miniagent/session/SessionMessage.java#L73-L75>) |

<a id="file-session-sessionrepository"></a>

### [SessionRepository.java](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java>)

- 类型：[SessionRepository · interface :11](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java#L11>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `SessionRepository` | `Session create(String userId, String title)` | [12–12](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java#L12-L12>) |
| `SessionRepository` | `void save(Session session)` | [14–14](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java#L14-L14>) |
| `SessionRepository` | `Optional<Session> find(String userId, String sessionId)` | [16–16](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java#L16-L16>) |
| `SessionRepository` | `List<Session> list(String userId)` | [18–18](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java#L18-L18>) |
| `SessionRepository` | `void delete(String userId, String sessionId)` | [20–20](<../src/main/java/dev/nangua/miniagent/session/SessionRepository.java#L20-L20>) |

<a id="file-session-sessionstoreexception"></a>

### [SessionStoreException.java](<../src/main/java/dev/nangua/miniagent/session/SessionStoreException.java>)

- 类型：[SessionStoreException · class :8](<../src/main/java/dev/nangua/miniagent/session/SessionStoreException.java#L8>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `SessionStoreException` | `public SessionStoreException(String message)` | [9–11](<../src/main/java/dev/nangua/miniagent/session/SessionStoreException.java#L9-L11>) |
| `SessionStoreException` | `public SessionStoreException(String message, Throwable cause)` | [13–15](<../src/main/java/dev/nangua/miniagent/session/SessionStoreException.java#L13-L15>) |

## tool

<a id="file-tool-calculatortool"></a>

### [CalculatorTool.java](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java>)

- 类型：[CalculatorTool · class :15](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java#L15>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `CalculatorTool` | `@Override public String name()` | [16–19](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java#L16-L19>) |
| `CalculatorTool` | `@Override public String description()` | [21–25](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java#L21-L25>) |
| `CalculatorTool` | `@Override public ObjectNode parameterSchema()` | [27–36](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java#L27-L36>) |
| `CalculatorTool` | `@Override public ToolResult execute(ToolContext context, ObjectNode arguments)` | [38–51](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java#L38-L51>) |
| `CalculatorTool` | `static String format(double value)` | [53–64](<../src/main/java/dev/nangua/miniagent/tool/CalculatorTool.java#L53-L64>) |

<a id="file-tool-expressionevaluator"></a>

### [ExpressionEvaluator.java](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java>)

- 类型：[ExpressionEvaluator · class :19](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L19>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ExpressionEvaluator` | `private ExpressionEvaluator(String input)` | [28–30](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L28-L30>) |
| `ExpressionEvaluator` | `static double evaluate(String expression)` | [32–49](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L32-L49>) |
| `ExpressionEvaluator` | `private double parseExpression()` | [51–63](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L51-L63>) |
| `ExpressionEvaluator` | `private double parseTerm()` | [65–87](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L65-L87>) |
| `ExpressionEvaluator` | `private double parseUnary()` | [89–98](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L89-L98>) |
| `ExpressionEvaluator` | `private double parsePower()` | [100–108](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L100-L108>) |
| `ExpressionEvaluator` | `private double parsePrimary()` | [110–130](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L110-L130>) |
| `ExpressionEvaluator` | `private double parseIdentifier()` | [132–159](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L132-L159>) |
| `ExpressionEvaluator` | `private double parseNumber()` | [161–191](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L161-L191>) |
| `ExpressionEvaluator` | `private double applyFunction(String name, List<Double> args)` | [193–228](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L193-L228>) |
| `ExpressionEvaluator` | `private static void requireArity(String name, List<Double> args, int count)` | [230–234](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L230-L234>) |
| `ExpressionEvaluator` | `private static void requireAtLeast(String name, List<Double> args, int count)` | [236–240](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L236-L240>) |
| `ExpressionEvaluator` | `private void enter()` | [242–247](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L242-L247>) |
| `ExpressionEvaluator` | `private void skipWhitespace()` | [249–253](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L249-L253>) |
| `ExpressionEvaluator` | `private boolean consume(char expected)` | [255–261](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L255-L261>) |
| `ExpressionEvaluator` | `private void expect(char expected)` | [263–267](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L263-L267>) |
| `ExpressionEvaluator` | `private ToolException error(String message)` | [269–271](<../src/main/java/dev/nangua/miniagent/tool/ExpressionEvaluator.java#L269-L271>) |

<a id="file-tool-readdocstool"></a>

### [ReadDocsTool.java](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java>)

- 类型：[ReadDocsTool · class :22](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java#L22>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ReadDocsTool` | `public ReadDocsTool(Path docsRoot)` | [29–31](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java#L29-L31>) |
| `ReadDocsTool` | `@Override public String name()` | [33–36](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java#L33-L36>) |
| `ReadDocsTool` | `@Override public String description()` | [38–42](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java#L38-L42>) |
| `ReadDocsTool` | `@Override public ObjectNode parameterSchema()` | [44–53](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java#L44-L53>) |
| `ReadDocsTool` | `@Override public ToolResult execute(ToolContext context, ObjectNode arguments)` | [55–87](<../src/main/java/dev/nangua/miniagent/tool/ReadDocsTool.java#L55-L87>) |

<a id="file-tool-schemas"></a>

### [Schemas.java](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java>)

- 类型：[Schemas · class :17](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L17>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `Schemas` | `private Schemas()` | [20–21](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L20-L21>) |
| `Schemas` | `public static ObjectNode object(String description, List<String> required, Map<String, ObjectNode> properties)` | [23–36](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L23-L36>) |
| `Schemas` | `public static Map<String, ObjectNode> properties(Object... nameSchemaPairs)` | [38–47](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L38-L47>) |
| `Schemas` | `public static ObjectNode string(String description)` | [49–54](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L49-L54>) |
| `Schemas` | `public static ObjectNode enumeration(String description, String... values)` | [56–63](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L56-L63>) |
| `Schemas` | `public static ObjectNode integer(String description, Integer minimum, Integer maximum)` | [65–76](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L65-L76>) |
| `Schemas` | `public static ObjectNode bool(String description)` | [78–83](<../src/main/java/dev/nangua/miniagent/tool/Schemas.java#L78-L83>) |

<a id="file-tool-searchtool"></a>

### [SearchTool.java](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java>)

- 类型：[SearchTool · class :21](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L21>)。
- 类型：[SearchTool.SearchDoc · record :123](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L123>)。
- 类型：[SearchTool.ScoredDoc · record :126](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L126>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `SearchTool` | `@Override public String name()` | [39–42](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L39-L42>) |
| `SearchTool` | `@Override public String description()` | [44–48](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L44-L48>) |
| `SearchTool` | `@Override public ObjectNode parameterSchema()` | [50–60](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L50-L60>) |
| `SearchTool` | `@Override public ToolResult execute(ToolContext context, ObjectNode arguments)` | [62–96](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L62-L96>) |
| `SearchTool` | `private static int score(String query, SearchDoc doc)` | [98–121](<../src/main/java/dev/nangua/miniagent/tool/SearchTool.java#L98-L121>) |

<a id="file-tool-todotool"></a>

### [TodoTool.java](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java>)

- 类型：[TodoTool · class :20](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L20>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `TodoTool` | `@Override public String name()` | [24–27](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L24-L27>) |
| `TodoTool` | `@Override public String description()` | [29–33](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L29-L33>) |
| `TodoTool` | `@Override public ObjectNode parameterSchema()` | [35–47](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L35-L47>) |
| `TodoTool` | `@Override public ToolResult execute(ToolContext context, ObjectNode arguments)` | [49–60](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L49-L60>) |
| `TodoTool` | `private ToolResult add(ObjectNode state, ObjectNode arguments)` | [62–84](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L62-L84>) |
| `TodoTool` | `private ToolResult list(ObjectNode state, ObjectNode arguments)` | [86–105](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L86-L105>) |
| `TodoTool` | `private ToolResult complete(ObjectNode state, ObjectNode arguments)` | [107–119](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L107-L119>) |
| `TodoTool` | `private ToolResult delete(ObjectNode state, ObjectNode arguments)` | [121–136](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L121-L136>) |
| `TodoTool` | `private static int requireId(ObjectNode arguments, String action)` | [138–144](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L138-L144>) |
| `TodoTool` | `private static ObjectNode todoState(ObjectNode sessionState)` | [146–155](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L146-L155>) |
| `TodoTool` | `private static ArrayNode items(ObjectNode state)` | [157–167](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L157-L167>) |
| `TodoTool` | `static List<String> texts(ObjectNode sessionState)` | [169–179](<../src/main/java/dev/nangua/miniagent/tool/TodoTool.java#L169-L179>) |

<a id="file-tool-tool"></a>

### [Tool.java](<../src/main/java/dev/nangua/miniagent/tool/Tool.java>)

- 类型：[Tool · interface :12](<../src/main/java/dev/nangua/miniagent/tool/Tool.java#L12>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `Tool` | `String name()` | [13–13](<../src/main/java/dev/nangua/miniagent/tool/Tool.java#L13-L13>) |
| `Tool` | `String description()` | [15–15](<../src/main/java/dev/nangua/miniagent/tool/Tool.java#L15-L15>) |
| `Tool` | `ObjectNode parameterSchema()` | [17–17](<../src/main/java/dev/nangua/miniagent/tool/Tool.java#L17-L17>) |
| `Tool` | `ToolResult execute(ToolContext context, ObjectNode arguments) throws ToolException` | [19–19](<../src/main/java/dev/nangua/miniagent/tool/Tool.java#L19-L19>) |
| `Tool` | `default ToolSpec spec()` | [21–23](<../src/main/java/dev/nangua/miniagent/tool/Tool.java#L21-L23>) |

<a id="file-tool-toolcontext"></a>

### [ToolContext.java](<../src/main/java/dev/nangua/miniagent/tool/ToolContext.java>)

- 类型：[ToolContext · record :14](<../src/main/java/dev/nangua/miniagent/tool/ToolContext.java#L14>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ToolContext` | `public ToolContext [compact constructor]` | [15–19](<../src/main/java/dev/nangua/miniagent/tool/ToolContext.java#L15-L19>) |

<a id="file-tool-toolexception"></a>

### [ToolException.java](<../src/main/java/dev/nangua/miniagent/tool/ToolException.java>)

- 类型：[ToolException · class :10](<../src/main/java/dev/nangua/miniagent/tool/ToolException.java#L10>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ToolException` | `public ToolException(String message)` | [11–13](<../src/main/java/dev/nangua/miniagent/tool/ToolException.java#L11-L13>) |
| `ToolException` | `public ToolException(String message, Throwable cause)` | [15–17](<../src/main/java/dev/nangua/miniagent/tool/ToolException.java#L15-L17>) |

<a id="file-tool-toolregistry"></a>

### [ToolRegistry.java](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java>)

- 类型：[ToolRegistry · class :18](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L18>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ToolRegistry` | `public ToolRegistry register(Tool tool)` | [21–31](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L21-L31>) |
| `ToolRegistry` | `public Optional<Tool> find(String name)` | [33–35](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L33-L35>) |
| `ToolRegistry` | `public Tool require(String name)` | [37–40](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L37-L40>) |
| `ToolRegistry` | `public ToolResult execute(String name, ToolContext context, ObjectNode arguments)` | [42–46](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L42-L46>) |
| `ToolRegistry` | `public List<ToolSpec> specs()` | [48–52](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L48-L52>) |
| `ToolRegistry` | `public List<String> names()` | [54–56](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L54-L56>) |
| `ToolRegistry` | `public int size()` | [58–60](<../src/main/java/dev/nangua/miniagent/tool/ToolRegistry.java#L58-L60>) |

<a id="file-tool-toolresult"></a>

### [ToolResult.java](<../src/main/java/dev/nangua/miniagent/tool/ToolResult.java>)

- 类型：[ToolResult · record :9](<../src/main/java/dev/nangua/miniagent/tool/ToolResult.java#L9>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `ToolResult` | `public ToolResult [compact constructor]` | [10–12](<../src/main/java/dev/nangua/miniagent/tool/ToolResult.java#L10-L12>) |
| `ToolResult` | `public static ToolResult success(String content)` | [14–16](<../src/main/java/dev/nangua/miniagent/tool/ToolResult.java#L14-L16>) |
| `ToolResult` | `public static ToolResult failure(String content)` | [18–20](<../src/main/java/dev/nangua/miniagent/tool/ToolResult.java#L18-L20>) |

<a id="file-tool-toolspec"></a>

### [ToolSpec.java](<../src/main/java/dev/nangua/miniagent/tool/ToolSpec.java>)

- 类型：[ToolSpec · record :11](<../src/main/java/dev/nangua/miniagent/tool/ToolSpec.java#L11>)。

无显式方法/构造器；按类型声明查看数据字段或枚举常量。

<a id="file-tool-weathertool"></a>

### [WeatherTool.java](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java>)

- 类型：[WeatherTool · class :20](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java#L20>)。
- 类型：[WeatherTool.Weather · record :97](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java#L97>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `WeatherTool` | `@Override public String name()` | [41–44](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java#L41-L44>) |
| `WeatherTool` | `@Override public String description()` | [46–50](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java#L46-L50>) |
| `WeatherTool` | `@Override public ObjectNode parameterSchema()` | [52–62](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java#L52-L62>) |
| `WeatherTool` | `@Override public ToolResult execute(ToolContext context, ObjectNode arguments)` | [64–95](<../src/main/java/dev/nangua/miniagent/tool/WeatherTool.java#L64-L95>) |

## trace

<a id="file-trace-jsonltracelogger"></a>

### [JsonlTraceLogger.java](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java>)

- 类型：[JsonlTraceLogger · class :24](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L24>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `JsonlTraceLogger` | `public JsonlTraceLogger(Path dataRoot)` | [30–32](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L30-L32>) |
| `JsonlTraceLogger` | `public JsonlTraceLogger(Path root, ObjectMapper mapper)` | [34–42](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L34-L42>) |
| `JsonlTraceLogger` | `@Override public void log(TraceEvent event)` | [44–59](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L44-L59>) |
| `JsonlTraceLogger` | `@Override public List<TraceEvent> tail(String sessionId, int limit)` | [67–94](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L67-L94>) |
| `JsonlTraceLogger` | `public Path fileFor(String sessionId)` | [96–99](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L96-L99>) |
| `JsonlTraceLogger` | `private static List<String> readTailLines(Path file) throws IOException` | [101–119](<../src/main/java/dev/nangua/miniagent/trace/JsonlTraceLogger.java#L101-L119>) |

<a id="file-trace-nooptracelogger"></a>

### [NoopTraceLogger.java](<../src/main/java/dev/nangua/miniagent/trace/NoopTraceLogger.java>)

- 类型：[NoopTraceLogger · class :8](<../src/main/java/dev/nangua/miniagent/trace/NoopTraceLogger.java#L8>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `NoopTraceLogger` | `private NoopTraceLogger()` | [11–12](<../src/main/java/dev/nangua/miniagent/trace/NoopTraceLogger.java#L11-L12>) |
| `NoopTraceLogger` | `@Override public void log(TraceEvent event)` | [14–17](<../src/main/java/dev/nangua/miniagent/trace/NoopTraceLogger.java#L14-L17>) |

<a id="file-trace-traceevent"></a>

### [TraceEvent.java](<../src/main/java/dev/nangua/miniagent/trace/TraceEvent.java>)

- 类型：[TraceEvent · record :15](<../src/main/java/dev/nangua/miniagent/trace/TraceEvent.java#L15>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `TraceEvent` | `public TraceEvent [compact constructor]` | [22–26](<../src/main/java/dev/nangua/miniagent/trace/TraceEvent.java#L22-L26>) |
| `TraceEvent` | `public static TraceEvent of(Session session, TraceType type, Map<String, Object> data)` | [28–30](<../src/main/java/dev/nangua/miniagent/trace/TraceEvent.java#L28-L30>) |
| `TraceEvent` | `public static Map<String, Object> data(Object... keyValues)` | [32–41](<../src/main/java/dev/nangua/miniagent/trace/TraceEvent.java#L32-L41>) |

<a id="file-trace-tracelogger"></a>

### [TraceLogger.java](<../src/main/java/dev/nangua/miniagent/trace/TraceLogger.java>)

- 类型：[TraceLogger · interface :11](<../src/main/java/dev/nangua/miniagent/trace/TraceLogger.java#L11>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `TraceLogger` | `void log(TraceEvent event)` | [12–12](<../src/main/java/dev/nangua/miniagent/trace/TraceLogger.java#L12-L12>) |
| `TraceLogger` | `default List<TraceEvent> tail(String sessionId, int limit)` | [14–16](<../src/main/java/dev/nangua/miniagent/trace/TraceLogger.java#L14-L16>) |
| `TraceLogger` | `default void close()` | [18–19](<../src/main/java/dev/nangua/miniagent/trace/TraceLogger.java#L18-L19>) |

<a id="file-trace-tracetype"></a>

### [TraceType.java](<../src/main/java/dev/nangua/miniagent/trace/TraceType.java>)

- 类型：[TraceType · enum :9](<../src/main/java/dev/nangua/miniagent/trace/TraceType.java#L9>)。

无显式方法/构造器；按类型声明查看数据字段或枚举常量。

## util

<a id="file-util-json"></a>

### [Json.java](<../src/main/java/dev/nangua/miniagent/util/Json.java>)

- 类型：[Json · class :18](<../src/main/java/dev/nangua/miniagent/util/Json.java#L18>)。

| Owner | 声明（保留签名） | 源码行 |
|---|---|---|
| `Json` | `private Json()` | [26–27](<../src/main/java/dev/nangua/miniagent/util/Json.java#L26-L27>) |
| `Json` | `public static ObjectMapper mapper()` | [29–31](<../src/main/java/dev/nangua/miniagent/util/Json.java#L29-L31>) |
| `Json` | `public static JsonNode readTree(String text) throws JsonProcessingException` | [33–35](<../src/main/java/dev/nangua/miniagent/util/Json.java#L33-L35>) |
| `Json` | `public static JsonNode readTreeUnchecked(String text)` | [38–44](<../src/main/java/dev/nangua/miniagent/util/Json.java#L38-L44>) |
| `Json` | `public static String write(Object value)` | [46–52](<../src/main/java/dev/nangua/miniagent/util/Json.java#L46-L52>) |
| `Json` | `public static String pretty(Object value)` | [54–60](<../src/main/java/dev/nangua/miniagent/util/Json.java#L54-L60>) |
| `Json` | `public static String preview(String text, int maxChars)` | [63–72](<../src/main/java/dev/nangua/miniagent/util/Json.java#L63-L72>) |
