# 产品 FAQ

## Q1：Agent 支持多用户吗？

支持。session 文件按 `用户 ID / session ID` 隔离。同一个用户可以创建多个窗口，每个窗口有独立的对话和待办。

## Q2：历史会话能恢复吗？

能。每轮结束都会原子写入 `data/sessions/<user>/<sessionId>.json`，再次启动时通过 `--session` 或 `/use` 恢复。

## Q3：天气和搜索是真实的吗？

当前版本为了可复现，使用 deterministic mock 数据，并在结果里标记 `isMock` 或 `mock-search`。替换成真实 HTTP provider 只需要实现同一个 `Tool` 接口。

## Q4：为什么不用原生 function calling？

本作业要求展示 LLM 输出的解析逻辑。项目把工具 Schema 放进系统提示词，要求模型输出统一 JSON envelope，再由 `AgentOutputParser` 提取 thought、tool call 或 final answer。这样协议可审计，也避免依赖某一家 provider 的 tool-call 格式。

## Q5：如何排查一次异常调用？

打开 `data/traces/<sessionId>.jsonl`，或使用 CLI 的 `/trace` 命令。每个事件都有时间戳、step、工具参数、是否成功和耗时。
