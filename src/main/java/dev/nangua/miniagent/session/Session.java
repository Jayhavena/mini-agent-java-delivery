package dev.nangua.miniagent.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nangua.miniagent.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A persistent chat window: messages, compacted summary and per-session tool state.
 *
 * <p>一个持久化的会话窗口。三块核心状态：messages 是全量消息，压缩也不会删除
 * 原文，只推进 compactedMessages 游标；summary 与 compactedMessages 一起表示
 * 「前 N 条已被摘要替代」，ContextManager 据此决定发回模型的内容；toolState
 * 是按窗口隔离的工具状态，窗口之间互不可见。本类只是数据载体，落盘由
 * SessionRepository 负责。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Session {
    private String id;
    private String userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private List<SessionMessage> messages = new ArrayList<>();
    private String summary = "";
    // 已被摘要替代的前缀长度：messages[0..compactedMessages) 不再发回模型，原文仍保留在列表里。
    private int compactedMessages;
    // 按窗口隔离的工具状态（如各自的 todo），由工具读写，其他窗口不可见。
    private ObjectNode toolState = JsonNodeFactory.instance.objectNode();

    /** Required by Jackson. */
    public Session() {
    }

    public Session(String id, String userId, String title) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.title = title == null || title.isBlank() ? "新会话" : title.trim();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SessionMessage> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }

    public void setMessages(List<SessionMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public String getSummary() {
        return summary == null ? "" : summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary;
    }

    public int getCompactedMessages() {
        return compactedMessages;
    }

    public void setCompactedMessages(int compactedMessages) {
        this.compactedMessages = Math.max(0, compactedMessages);
    }

    public ObjectNode getToolState() {
        if (toolState == null) {
            toolState = JsonNodeFactory.instance.objectNode();
        }
        return toolState;
    }

    public void setToolState(ObjectNode toolState) {
        this.toolState = toolState == null ? JsonNodeFactory.instance.objectNode() : toolState;
    }

    public Session append(SessionMessage message) {
        getMessages().add(message);
        updatedAt = Instant.now();
        return this;
    }

    public void clearMessages() {
        getMessages().clear();
        compactedMessages = 0;
        summary = "";
        updatedAt = Instant.now();
    }

    public int messageCount() {
        return getMessages().size();
    }

    public String statePreview() {
        return Json.preview(Json.write(getToolState()), 500);
    }
}
