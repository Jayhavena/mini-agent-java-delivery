package dev.nangua.miniagent.session;

import java.util.List;
import java.util.Optional;

/**
 * session 仓储接口：定义会话窗口的创建、保存、查找、列举与删除。
 * 所有方法都以 userId 为第一维度，实现必须保证不同用户的数据相互隔离；
 * find 未命中返回 Optional.empty()，存储层故障统一以 SessionStoreException 抛出。
 */
public interface SessionRepository {
    Session create(String userId, String title);

    void save(Session session);

    Optional<Session> find(String userId, String sessionId);

    List<Session> list(String userId);

    void delete(String userId, String sessionId);
}
