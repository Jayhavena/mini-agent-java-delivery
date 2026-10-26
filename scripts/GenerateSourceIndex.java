import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Run from the repository root with JDK 21. Parses source only; no Maven dependencies or API calls. */
public class GenerateSourceIndex {
    record Entry(String owner, String declaration, long start, long end) {}
    record Source(Path path, String relative, String text, List<Entry> types, List<Entry> methods) {
        String link() { return "../" + relative; }
        String anchor() { return "file-" + relative.substring(relative.indexOf("miniagent/") + 10)
                .replace(".java", "").replace('/', '-').toLowerCase(java.util.Locale.ROOT); }
    }

    private static final Map<String, String> ROLES = Map.ofEntries(
            Map.entry("Main", "B｜启动参数与会话选择"),
            Map.entry("AgentFactory", "A｜显式装配与工具注册"),
            Map.entry("AgentRuntime", "A｜请求主循环与终止分支"),
            Map.entry("AgentConfig", "A｜上限、预算与环境配置"),
            Map.entry("AgentReply", "A｜turn 返回值与工具调用记录"),
            Map.entry("AgentStopReason", "A｜终止原因"),
            Map.entry("AgentException", "B｜运行时异常边界"),
            Map.entry("AgentCli", "B｜REPL、会话命令与终端展示"),
            Map.entry("ContextManager", "A｜消息构建、裁剪与压缩游标"),
            Map.entry("ContextWindow", "A｜单次请求上下文及统计"),
            Map.entry("SessionSummarizer", "A｜确定性摘要与合并"),
            Map.entry("SystemPromptBuilder", "A｜工具说明、记忆与输出协议"),
            Map.entry("TokenEstimator", "A｜启发式 token 估算"),
            Map.entry("LiveDemo", "C｜真实 API 双窗口演示（可能付费）"),
            Map.entry("ChatMessage", "A｜发给模型的角色消息"),
            Map.entry("LlmClient", "A｜模型调用端口"),
            Map.entry("LlmConfig", "B｜provider 参数与环境配置"),
            Map.entry("LlmException", "B｜模型错误及 HTTP 状态"),
            Map.entry("LlmRequest", "A｜模型请求数据"),
            Map.entry("LlmResponse", "A｜模型响应与用量数据"),
            Map.entry("OpenAiCompatibleClient", "B｜HTTP、重试与响应解析"),
            Map.entry("AgentOutput", "A｜ToolCall / FinalAnswer 协议类型"),
            Map.entry("AgentOutputParser", "A｜文本协议提取与字段校验"),
            Map.entry("ParseException", "B｜协议解析错误"),
            Map.entry("Session", "A｜会话消息、摘要与私有工具状态"),
            Map.entry("SessionMessage", "A｜持久化消息及工厂方法"),
            Map.entry("MessageKind", "A｜消息类型"),
            Map.entry("SessionRepository", "A｜会话持久化端口"),
            Map.entry("JsonSessionRepository", "B｜按用户/窗口存储、替换与加载"),
            Map.entry("SessionStoreException", "B｜会话存储错误"),
            Map.entry("Tool", "A｜工具契约与默认 spec"),
            Map.entry("ToolSpec", "A｜模型可见的工具说明"),
            Map.entry("ToolContext", "A｜当前会话的工具状态引用"),
            Map.entry("ToolResult", "A｜工具成功/失败结果"),
            Map.entry("ToolException", "B｜工具可报告错误"),
            Map.entry("ToolRegistry", "A｜名称注册、查询与分发"),
            Map.entry("CalculatorTool", "A｜完整工具调用的最小样例"),
            Map.entry("TodoTool", "B｜会话级有状态工具"),
            Map.entry("ReadDocsTool", "B｜受限 Markdown 文件读取"),
            Map.entry("SearchTool", "C｜本地 mock 搜索"),
            Map.entry("WeatherTool", "C｜本地 mock 天气"),
            Map.entry("ExpressionEvaluator", "C｜独立的表达式解析算法"),
            Map.entry("Schemas", "C｜工具参数 Schema 构建辅助"),
            Map.entry("TraceType", "B｜事件类型"),
            Map.entry("TraceEvent", "B｜事件数据与构造辅助"),
            Map.entry("TraceLogger", "B｜观测端口"),
            Map.entry("JsonlTraceLogger", "B｜JSONL 写入与尾部查询"),
            Map.entry("NoopTraceLogger", "C｜空观测实现"),
            Map.entry("Json", "B｜统一 JSON 序列化与预览")
    );

    public static void main(String[] args) throws Exception {
        boolean check = args.length == 1 && args[0].equals("--check");
        if (args.length > 0 && !check) {
            throw new IllegalArgumentException("Usage: java scripts/GenerateSourceIndex.java [--check]");
        }
        Path root = Path.of("").toAbsolutePath().normalize();
        Path sourceRoot = root.resolve("src/main/java");
        List<Path> paths;
        try (var stream = Files.walk(sourceRoot)) {
            paths = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        if (paths.isEmpty()) throw new IllegalStateException("Run from the repository root: no Java sources found");
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK 21 is required (not a JRE)");
        List<Source> sources = new ArrayList<>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "21"), null,
                    manager.getJavaFileObjectsFromPaths(paths));
            var trees = Trees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                Path path = Path.of(unit.getSourceFile().toUri());
                String text = Files.readString(path, StandardCharsets.UTF_8);
                var types = new ArrayList<Entry>();
                var methods = new ArrayList<Entry>();
                new TreePathScanner<Void, Void>() {
                    final Deque<String> owners = new ArrayDeque<>();
                    Entry entry(Tree tree, String declaration) {
                        long start = trees.getSourcePositions().getStartPosition(unit, tree);
                        long end = trees.getSourcePositions().getEndPosition(unit, tree);
                        if (start < 0 || end <= start) throw new IllegalStateException("Missing source range: " + tree);
                        return new Entry(String.join(".", owners), declaration,
                                unit.getLineMap().getLineNumber(start), unit.getLineMap().getLineNumber(end - 1));
                    }
                    @Override public Void visitClass(ClassTree tree, Void unused) {
                        owners.addLast(tree.getSimpleName().toString());
                        types.add(entry(tree, tree.getKind().name().toLowerCase(java.util.Locale.ROOT)));
                        super.visitClass(tree, unused);
                        owners.removeLast();
                        return null;
                    }
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        long start = trees.getSourcePositions().getStartPosition(unit, tree);
                        long end = tree.getBody() == null
                                ? trees.getSourcePositions().getEndPosition(unit, tree)
                                : trees.getSourcePositions().getStartPosition(unit, tree.getBody());
                        String header = text.substring((int) start, (int) end).strip()
                                .replaceAll("\\s+", " ").replaceFirst(";$", "");
                        if (tree.getReturnType() == null && !header.contains("(")) {
                            header += " [compact constructor]";
                        }
                        methods.add(entry(tree, header));
                        return super.visitMethod(tree, unused);
                    }
                }.scan(unit, null);
                sources.add(new Source(path, root.relativize(path).toString().replace('\\', '/'), text, types, methods));
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalStateException("Cannot index invalid source: " + diagnostic);
            }
        }
        sources.sort(Comparator.comparing(Source::relative));
        String markdown = render(sources);
        Path target = root.resolve("docs/INDEX.md");
        if (check) {
            if (!Files.exists(target) || !Files.readString(target, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").equals(markdown)) {
                System.err.println("Source index is stale. Run: java scripts/GenerateSourceIndex.java");
                System.exit(1);
            }
            System.out.println("Source index OK: " + sources.size() + " files, "
                    + sources.stream().mapToInt(s -> s.methods().size()).sum() + " explicit method/constructor declarations");
        } else {
            Files.writeString(target, markdown, StandardCharsets.UTF_8);
            System.out.println("Generated docs/INDEX.md: " + sources.size() + " files, "
                    + sources.stream().mapToInt(s -> s.methods().size()).sum() + " explicit method/constructor declarations");
        }
    }

    private static String render(List<Source> sources) throws Exception {
        int methods = sources.stream().mapToInt(s -> s.methods().size()).sum();
        int types = sources.stream().mapToInt(s -> s.types().size()).sum();
        var digest = MessageDigest.getInstance("SHA-256");
        for (Source source : sources) {
            digest.update(source.relative().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(source.text().replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        StringBuilder out = new StringBuilder("""
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

                """);
        out.append("- 覆盖：**").append(sources.size()).append(" 个主源码文件 / ").append(types)
                .append(" 个显式类型 / ").append(methods).append(" 个显式方法与构造器声明**。\n");
        out.append("- 源码指纹（按路径与 LF 规范化的 UTF-8 内容计算 SHA-256）：`")
                .append(HexFormat.of().formatHex(digest.digest())).append("`。\n");
        out.append("""
                - 生成器使用 JDK 编译器的语法树，不靠正则猜方法，不编译业务代码，也不访问模型/网络。
                - 维护脚本：[GenerateSourceIndex.java](<../scripts/GenerateSourceIndex.java>)。修改源码后重新生成并检查；此检查针对本索引，正文中的源码链接仍需随对应修改复核。

                ```powershell
                java scripts/GenerateSourceIndex.java
                java scripts/GenerateSourceIndex.java --check
                ```

                ## 文件导航

                | 文件 | 阅读层级与职责 | 显式方法/构造器 |
                |---|---|---:|
                """);
        for (Source source : sources) {
            String name = source.path().getFileName().toString();
            String role = ROLES.get(name.replace(".java", ""));
            if (role == null) throw new IllegalStateException("Add reading role for " + name);
            out.append("| [").append(name).append("](#").append(source.anchor()).append(") | ")
                    .append(role).append(" | ").append(source.methods().size()).append(" |\n");
        }
        String group = null;
        for (Source source : sources) {
            String pkg = source.relative().substring(0, source.relative().lastIndexOf('/'));
            String nextGroup = pkg.substring(pkg.indexOf("miniagent") + 9).replaceFirst("^/", "");
            if (nextGroup.isEmpty()) nextGroup = "入口";
            if (!nextGroup.equals(group)) {
                out.append("\n## ").append(nextGroup).append("\n");
                group = nextGroup;
            }
            out.append("\n<a id=\"").append(source.anchor()).append("\"></a>\n\n### [")
                    .append(source.path().getFileName()).append("](<").append(source.link()).append(">)\n\n");
            for (Entry type : source.types()) {
                out.append("- 类型：[").append(type.owner()).append(" · ").append(type.declaration())
                        .append(" :").append(type.start()).append("](<").append(source.link())
                        .append("#L").append(type.start()).append(">)。\n");
            }
            if (source.methods().isEmpty()) {
                out.append("\n无显式方法/构造器；按类型声明查看数据字段或枚举常量。\n");
            } else {
                out.append("\n| Owner | 声明（保留签名） | 源码行 |\n|---|---|---|\n");
                for (Entry method : source.methods()) {
                    out.append("| `").append(method.owner()).append("` | `")
                            .append(method.declaration().replace("|", "\\|")).append("` | [")
                            .append(method.start()).append('–').append(method.end()).append("](<")
                            .append(source.link()).append("#L").append(method.start()).append("-L")
                            .append(method.end()).append(">) |\n");
                }
            }
        }
        return out.toString();
    }
}
