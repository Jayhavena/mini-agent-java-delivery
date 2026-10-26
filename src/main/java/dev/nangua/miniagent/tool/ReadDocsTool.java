package dev.nangua.miniagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Reads Markdown files from a fixed docs directory, with traversal protection.
 *
 * <p>读取项目 docs 目录下的 Markdown 文档。防护有三层：文件名只允许字母/数字/
 * ._-. 并以 .md 结尾（字符级白名单，不含斜杠和反斜杠）；resolve+normalize 后
 * 必须仍位于 docsRoot 内（防 ../ 目录穿越）；最后还要通过 isRegularFile 校验。
 * 内容超过 12k 字符会截断并打上标记。
 */
public final class ReadDocsTool implements Tool {
    private static final int MAX_CHARS = 12_000;
    private static final List<String> KNOWN_DOCS = List.of(
            "agent-notes.md", "product-faq.md");

    private final Path docsRoot;

    public ReadDocsTool(Path docsRoot) {
        this.docsRoot = Objects.requireNonNull(docsRoot, "docsRoot").toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "read_docs";
    }

    @Override
    public String description() {
        return "Read a local Markdown document from the project docs directory. "
                + "Available files: " + String.join(", ", KNOWN_DOCS) + ".";
    }

    @Override
    public ObjectNode parameterSchema() {
        return Schemas.object(
                "Read a documentation file.",
                List.of("path"),
                Schemas.properties(
                        "path", Schemas.string("File name under docs/, e.g. agent-notes.md")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context, ObjectNode arguments) {
        JsonNode pathNode = arguments.get("path");
        if (pathNode == null || !pathNode.isTextual() || pathNode.asText().isBlank()) {
            throw new ToolException("'path' must be a non-blank file name");
        }
        String requested = pathNode.asText().trim();
        if (!requested.matches("[A-Za-z0-9._-]+\\.md")) {
            throw new ToolException("Only simple .md file names under docs/ are allowed");
        }
        Path resolved = docsRoot.resolve(requested).normalize();
        if (!resolved.startsWith(docsRoot)) {
            throw new ToolException("Path traversal is not allowed");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ToolException("Document not found: " + requested + ". Available: " + KNOWN_DOCS);
        }
        try {
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            boolean truncated = content.length() > MAX_CHARS;
            if (truncated) {
                content = content.substring(0, MAX_CHARS) + "\n\n[truncated]";
            }
            ObjectNode payload = Json.mapper().createObjectNode();
            payload.put("path", requested);
            payload.put("chars", content.length());
            payload.put("truncated", truncated);
            payload.put("content", content);
            return ToolResult.success(Json.write(payload));
        } catch (IOException e) {
            throw new ToolException("Cannot read document: " + requested, e);
        }
    }
}
