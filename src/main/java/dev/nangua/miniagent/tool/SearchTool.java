package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic mock search. The interface is deliberately the same shape as a real
 * search API (query in, ranked title/snippet/url out), so replacing it is local.
 *
 * <p>确定式的 mock 搜索：内置一个小型语料库，打分规则是标题命中权重高于摘要
 * 命中（12/6，分词后 3/1），得分并列时按标题排序保证结果稳定。入参/出参的
 * 形状刻意与真实搜索 API 一致，将来换成真实现只需改这个类。
 */
public final class SearchTool implements Tool {
    private static final List<SearchDoc> CORPUS = List.of(
            new SearchDoc("杭州天气与出行建议", "杭州未来两天多云，气温 18-26°C，早晚有凉意；西湖景区周末客流较大。",
                    "mock://weather/hangzhou"),
            new SearchDoc("北京天气周报", "北京本周晴到多云，昼夜温差大，最低 9°C，最高 22°C，空气干燥。",
                    "mock://weather/beijing"),
            new SearchDoc("上海天气与通勤", "上海本周有短时阵雨，建议随身带伞，气温 20-27°C。",
                    "mock://weather/shanghai"),
            new SearchDoc("Agent 的记忆与上下文管理", "Agent 通常把系统提示、会话摘要、最近消息和工具结果放入上下文；"
                    + "过长时压缩早期内容，并保留工具状态等结构化记忆。", "mock://docs/agent-memory"),
            new SearchDoc("如何写一份清晰的工作周报", "周报建议包含本周完成、关键数据、风险与阻塞、下周计划四部分，"
                    + "用结果和数字代替流水账。", "mock://docs/weekly-report"),
            new SearchDoc("Java 21 HttpClient 调用大模型", "JDK HttpClient 可以直接调用 OpenAI 兼容的 chat/completions 接口，"
                    + "通过 Bearer Token、JSON 请求体和超时重试实现最小客户端。", "mock://docs/java-http-client"),
            new SearchDoc("远程办公与番茄钟", "远程办公时用 25 分钟专注加 5 分钟休息的番茄钟，"
                    + "可以减少上下文切换，适合写代码和写周报。", "mock://docs/focus")
    );

    @Override
    public String name() {
        return "search";
    }

    @Override
    public String description() {
        return "Search a small mock knowledge/web index. Use it for factual lookups or when the user "
                + "asks to search. Results are simulated and marked with mock:// URLs.";
    }

    @Override
    public ObjectNode parameterSchema() {
        return Schemas.object(
                "Search query and optional result count.",
                List.of("query"),
                Schemas.properties(
                        "query", Schemas.string("Keywords, e.g. 杭州天气 or Agent memory"),
                        "limit", Schemas.integer("Number of results, 1-5 (default 3)", 1, 5)
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, ObjectNode arguments) {
        JsonNode queryNode = arguments.get("query");
        if (queryNode == null || !queryNode.isTextual() || queryNode.asText().isBlank()) {
            throw new ToolException("'query' must be a non-blank string");
        }
        String query = queryNode.asText().trim();
        int limit = arguments.path("limit").asInt(3);
        limit = Math.max(1, Math.min(5, limit));

        List<ScoredDoc> matches = new ArrayList<>();
        for (SearchDoc doc : CORPUS) {
            int score = score(query, doc);
            if (score > 0) {
                matches.add(new ScoredDoc(doc, score));
            }
        }
        matches.sort(Comparator.comparingInt(ScoredDoc::score).reversed()
                .thenComparing(scored -> scored.doc().title()));

        ObjectNode payload = Json.mapper().createObjectNode();
        payload.put("query", query);
        payload.put("source", "mock-search");
        ArrayNode results = payload.putArray("results");
        matches.stream().limit(limit).forEach(match -> {
            ObjectNode result = results.addObject();
            result.put("title", match.doc().title());
            result.put("snippet", match.doc().snippet());
            result.put("url", match.doc().url());
        });
        if (results.isEmpty()) {
            payload.put("note", "No mock index result. Try broader keywords.");
        }
        return ToolResult.success(Json.write(payload));
    }

    private static int score(String query, SearchDoc doc) {
        String q = query.toLowerCase(Locale.ROOT);
        String title = doc.title().toLowerCase(Locale.ROOT);
        String snippet = doc.snippet().toLowerCase(Locale.ROOT);
        int score = 0;
        if (title.contains(q)) {
            score += 12;
        }
        if (snippet.contains(q)) {
            score += 6;
        }
        for (String term : q.split("[\\s,，。?？!！;；:：/]+")) {
            if (term.length() < 2) {
                continue;
            }
            if (title.contains(term)) {
                score += 3;
            }
            if (snippet.contains(term)) {
                score += 1;
            }
        }
        return score;
    }

    private record SearchDoc(String title, String snippet, String url) {
    }

    private record ScoredDoc(SearchDoc doc, int score) {
    }
}
