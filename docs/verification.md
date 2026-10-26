# 交付验证记录

## 验证范围与环境

- 日期：2026-10-26（Asia/Shanghai）。
- 验证对象：经审查的独立交付快照。当前仓库从本次交付重新初始化 Git，不导入开发工作区的旧提交、私人学习资料或运行数据；开发中的 AI 协作过程仍见 [PROMPTS](<../PROMPTS.md>)。
- 本地环境：Windows 11、Azul OpenJDK 21.0.11、Apache Maven 3.9.15；Bash 启动脚本另在 Git Bash 下执行。
- 交付快照从候选公开文件生成，共 87 个文件，复制时逐文件核对 SHA-256；不带旧 Git 元数据、旧构建产物、会话数据或私人资料。导出后仅调整交付说明与历史来源标注，源码与配置保持一致；提交前在新仓库再次验证构建与入口。
- 默认测试不调用外部模型；真实调用显式开启并可能计费。Maven 首次构建仍可能下载依赖，HTTP 客户端测试使用本地测试服务。

## 实际命令与结果

| 检查 | 执行方式 | 结果 |
|---|---|---|
| 默认测试与打包 | `mvn -B -ntp clean verify`；同时设置假 Key 和 `http://127.0.0.1:1` 模型地址 | 53 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS |
| 干净候选副本复验 | 在不含旧产物的副本中重复上述命令 | 同样 53 项通过，打包成功 |
| 独立公开仓库复验 | 在不继承旧 Git 历史的交付仓库中再次运行 clean verify、索引检查和 CLI help | 53 项通过，打包、49 文件／256 方法索引和入口检查通过 |
| 无 Key 显式 live | 清空进程中的两个 Key 后执行 `mvn -B -ntp -Plive test` | 预期失败：1 项因缺少 Key 断言失败，0 跳过；不是绿色假通过 |
| 有 Key 显式 live | `mvn -B -ntp -Plive test`，真实 DeepSeek API、`deepseek-chat` | 1 项通过，0 失败、0 错误、0 跳过 |
| 真实双窗口演示 | 下方 LiveDemo 命令 | 5 轮均为 FINAL，11 次模型请求，两个窗口的 todo 分离 |
| 源码索引 | `java scripts/GenerateSourceIndex.java --check` | 49 个主源码文件、256 个显式方法／构造器声明，检查通过 |
| 打包后入口 | `java -jar target/mini-agent.jar --help` | 成功；Windows 本地检查额外设置 JVM UTF-8 输出参数 |
| 启动脚本 | Windows 启动入口、Git Bash 脚本，以假 Key 和不可达模型地址启动后输入 `/quit` | 两者成功启动并退出，不发送聊天请求 |
| 公开文件与文档 | 检查候选文件、Markdown 本地目标及行号范围 | 通过；私人目录和运行产物未进入候选文件集 |

真实测试不是模型替身：它要求真实模型调用 calculator，断言工具成功返回 `3293`、最终停止原因为 FINAL、回答包含结果，并确认会话已保存。测试入口见 [DeepSeekLiveTest](<../src/test/java/dev/nangua/miniagent/agent/DeepSeekLiveTest.java>)。

本轮演示命令（先按 [README](<../README.md>) 将 Key 放入进程环境，不在命令里写明文 Key）：

```bash
java -cp target/mini-agent.jar dev.nangua.miniagent.demo.LiveDemo \
  --data-dir data/live-demo-20261026 \
  --out data/live-demo-output-20261026.md
```

## 新增完整流程验证

[AgentConversationFlowTest](<../src/test/java/dev/nangua/miniagent/agent/AgentConversationFlowTest.java>) 使用确定性 LLM 和真实的本地工具／文件仓库，覆盖：

1. 纯对话追问：先记录项目代号，后续请求仍包含之前的消息。
2. 带工具的指代追问：把上一轮天气结果用于下一轮 todo 参数。
3. 同一用户两个窗口交替：消息、发给模型的 Context、落盘 todo 不串线。
4. 新仓库、新 Runtime 恢复：加载已有消息、摘要、压缩游标和工具状态后继续对话；摘要用 fixture 预置，单独验证恢复语义。
5. 自动压缩后追问：旧事实已退出未压缩消息区，仍通过系统 SESSION_MEMORY 进入下一次模型请求；该项独立验证自动摘要生成。

断言包括实际发给 LLM 的上下文，以及重新从磁盘读取的完整消息快照和工具状态，不只依赖模型替身预设的最终文本。消息快照比较完整 JSON 树，以兼容 Java null 与 Jackson NullNode 的表示差异。

## 公开证据的来源与边界

- [演示输出](<live-demo-output.md>) 已更新为本轮 2026-10-26 的真实模型运行，不再把旧记录当成重新验证。
- 演示中 `alice`、待办及员工手册是硬编码示例。`weather`、`search` 是 mock 工具；模型调用是真实 API。本轮手动演示调用了 weather、todo、read_docs、calculator，未覆盖 search 的模型自主调用。
- [Trace 样例](<sample-trace.jsonl>) 是本轮窗口一的 36 行原始事件；两个窗口合计 66 个事件。选取后未改写事件数据，也没有拼装模型响应。
- Trace 含事件元数据、工具结果预览与简短决策摘要，不是完整 HTTP 捕获或完整内部思维链；单凭模型名也不能独立证明 API 来源。演示属于手动检查材料，不冒充断言式验收套件。
- 发布前检查候选公开文件中的当前实际 Key、常见凭据模式、私钥头与开发机绝对路径，并检查新仓库提交对象与身份信息。扫描具有启发式边界，不能等同于对任意密钥或外部缓存的穷尽保证；私人目录和旧 Git 对象均不导入交付仓库。

## 复现边界与交付说明

- [CI 配置](<../.github/workflows/ci.yml>) 执行默认测试、打包、索引检查与 CLI help。最新 Ubuntu/Temurin 运行状态以[本仓库 Actions 记录](<https://github.com/Jayhavena/mini-agent-java-delivery/actions/workflows/ci.yml>)为准；本地通过不等同于远端通过。
- Shade 打包仍有依赖模块描述符、MANIFEST/LICENSE/NOTICE 重叠等警告；当前 classpath 可运行 JAR 的构建及入口检查通过，不声称零警告或模块化打包兼容。
- 本仓库只发布独立审查后的交付文件，默认分支为 `main`；提交身份使用 GitHub noreply 邮箱。没有通过 force push 覆盖其他仓库，也不把私有开发记录伪装成本仓库已公开的逐步提交历史。
- 私人开发资料和原始备份由所有者在公开仓库之外保留。公开交付中的 Prompt、已知限制和真实模型证据继续保留，隐私整理不改变对 AI 使用方式的说明。
