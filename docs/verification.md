# 交付验证记录

## 验证范围与环境

- 日期：2026-10-26（Asia/Shanghai）；本页当前结果对应发布后材料精简的复测，不把首次交付的旧结果当成本次结果。
- 验证对象：独立的公开交付仓库。首次初始化不导入开发工作区的旧 Git 历史、私人学习资料或运行数据；AI 协作过程仍见 [PROMPTS](<../PROMPTS.md>)。
- 本地环境：Windows 11、Azul OpenJDK 21.0.11、Apache Maven 3.9.15。
- 首次交付的 87 文件快照经过逐文件 SHA-256 核对。发布后按所有者要求移除可选材料及无用生成器；当前公开文件为 78 个，本次重新构建变更后的源码与配置，而非沿用旧产物。
- 默认测试不调用外部模型；真实调用显式开启并可能计费。Maven 首次构建仍可能下载依赖，HTTP 客户端测试使用本地测试服务。

## 本次实际命令与结果

| 检查 | 执行方式 | 结果 |
|---|---|---|
| 默认测试与打包 | `mvn -B -ntp clean verify`；同时设置假 Key 和 `http://127.0.0.1:1` 模型地址 | 54 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS |
| 剩余示例文档回归 | `TodoAndRegistryTest.readDocsAdvertisesExistingBundledExamples`，包含于上述默认测试 | 工具只提示保留的两个示例；两份文档均能成功读取且内容非空 |
| 有 Key 显式 live | `mvn -B -ntp -Plive test`，真实 DeepSeek API、`deepseek-chat` | 1 项通过，0 失败、0 错误、0 跳过 |
| 真实双窗口演示 | 下方 LiveDemo 命令，使用全新会话目录 | 5 轮均为 FINAL，工具调用成功，两个窗口的 todo 分离；B 窗口读取项目说明 |
| 打包后入口 | `java -jar target/mini-agent.jar --help` | 成功；Windows 本地额外设置 JVM UTF-8 输出参数 |

真实测试不是模型替身：它要求真实模型调用 calculator，断言工具成功返回 `3293`、最终停止原因为 FINAL、回答包含结果，并确认会话已保存。测试入口见 [DeepSeekLiveTest](<../src/test/java/dev/nangua/miniagent/agent/DeepSeekLiveTest.java>)。

本次演示命令（先按 [README](<../README.md>) 将 Key 放入进程环境，不在命令里写明文 Key）：

```bash
java -cp target/mini-agent.jar dev.nangua.miniagent.demo.LiveDemo \
  --data-dir data/live-demo-trimmed-20261026 \
  --out data/live-demo-output-trimmed-20261026.md
```

首次交付阶段还验证过：干净副本默认测试及打包、无 Key 显式 live 明确失败且不跳过、Windows 和 Git Bash 启动后 `/quit`。这些是历史验证，不声称本次再次执行了全部启动脚本及缺 Key 分支。

## 完整流程验证

[AgentConversationFlowTest](<../src/test/java/dev/nangua/miniagent/agent/AgentConversationFlowTest.java>) 使用确定性 LLM 和真实的本地工具／文件仓库，包含于本次默认测试，覆盖：

1. 纯对话追问：先记录项目代号，后续请求仍包含之前的消息。
2. 带工具的指代追问：把上一轮天气结果用于下一轮 todo 参数。
3. 同一用户两个窗口交替：消息、发给模型的 Context、落盘 todo 不串线。
4. 新仓库、新 Runtime 恢复：加载已有消息、摘要、压缩游标和工具状态后继续对话；摘要用 fixture 预置，单独验证恢复语义。
5. 自动压缩后追问：旧事实已退出未压缩消息区，仍通过系统 SESSION_MEMORY 进入下一次模型请求；该项独立验证自动摘要生成。

断言包括实际发给 LLM 的上下文，以及重新从磁盘读取的完整消息快照和工具状态，不只依赖模型替身预设的最终文本。消息快照比较完整 JSON 树，以兼容 Java null 与 Jackson NullNode 的表示差异。

## 公开证据的来源与边界

- [演示输出](<live-demo-output.md>) 来自本次精简后的真实模型运行，替换了依赖已移除示例的旧输出；生成时间以文件元数据为准。
- `alice`、待办与 [项目说明](<agent-notes.md>) 是演示输入；`weather`、`search` 是 mock 工具，模型调用是真实 API。本次手动演示调用了 weather、todo、read_docs、calculator，未覆盖 search 的模型自主调用。
- [Trace 样例](<sample-trace.jsonl>) 选用本次窗口一的原始 JSONL；选取后不改写事件，也不拼装模型响应。本次真实模型有协议纠错后恢复的情况，不能将最终成功等同于每次输出都正确。
- Trace 含事件元数据、工具结果预览与简短决策摘要，不是完整 HTTP 捕获或完整内部思维链；单凭模型名也不能独立证明 API 来源。演示属于人工检查材料，不冒充断言式验收套件。
- 公开证据需检查实际 Key、常见凭据模式、私钥头与开发机绝对路径，并核对删除项及本地文档链接。扫描具有启发式边界，不等同于对任意密钥或外部缓存的穷尽保证。

## 复现边界与交付说明

- [CI 配置](<../.github/workflows/ci.yml>) 仅执行默认测试、打包与 CLI help，不再依赖已退役的生成器。最新 Ubuntu/Temurin 运行状态以[本仓库 Actions 记录](<https://github.com/Jayhavena/mini-agent-java-delivery/actions/workflows/ci.yml>)为准；本地通过不等同于远端通过。
- Shade 打包仍有依赖模块描述符、MANIFEST/LICENSE/NOTICE 重叠等警告；当前 classpath 可运行 JAR 的构建及入口检查通过，不声称零警告或模块化打包兼容。
- 本仓库只发布独立审查后的交付文件，默认分支为 `main`；提交身份使用 GitHub noreply 邮箱。本次为普通删除提交，不强推改写已公开历史。
- 私人开发资料和原始备份由所有者在公开仓库之外保留。公开交付中的 Prompt、已知限制和真实模型证据继续保留，材料精简不改变对 AI 使用方式的说明。
