package dev.nangua.miniagent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nangua.miniagent.util.Json;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One JSON file per session under data/sessions/<user>/<sessionId>.json.
 * Writes are atomic (temp file + move) so a crash cannot leave half a session.
 *
 * <p>教学优先的实现：每次 save 都全量重写整个 JSON 文件，不做增量优化。
 * userId/sessionId 必须先通过白名单正则（ID_PATTERN）校验再拼路径；进程内按
 * 目标文件加 ReentrantLock 串行化并发读写，但锁不跨进程生效。list 遇到单个
 * 损坏文件只告警跳过，不让一个坏文件拖垮整个列表。
 */
public final class JsonSessionRepository implements SessionRepository {
    // 白名单字符集：userId/sessionId 的合法性校验与路径穿越防线二合一（不含 / \ .. 等字符）。
    private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,64}";
    private final Path root;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public JsonSessionRepository(Path dataRoot) {
        this(dataRoot.resolve("sessions"), Json.mapper());
    }

    public JsonSessionRepository(Path root, ObjectMapper mapper) {
        this.root = root.toAbsolutePath().normalize();
        this.mapper = mapper;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new SessionStoreException("Cannot create session directory " + this.root, e);
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public Session create(String userId, String title) {
        String safeUser = validate(userId, "userId");
        String id;
        do {
            id = "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (Files.exists(pathFor(safeUser, id)));
        Session session = new Session(id, safeUser, title);
        save(session);
        return session;
    }

    @Override
    public void save(Session session) {
        String userId = validate(session.getUserId(), "userId");
        String sessionId = validate(session.getId(), "sessionId");
        Path file = pathFor(userId, sessionId);
        ReentrantLock lock = lockFor(file);
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), sessionId + "-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), session);
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new SessionStoreException("Cannot save session " + sessionId, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取指定用户的会话；文件不存在时返回 Optional.empty()。
     * 读出后会再校验文件内的 userId/sessionId 与请求一致（文件被手工挪动或
     * 改名时会不一致），不一致抛 SessionStoreException；IO 故障同样包装为
     * SessionStoreException，而不是假装返回空。
     */
    @Override
    public Optional<Session> find(String userId, String sessionId) {
        String safeUser = validate(userId, "userId");
        String safeSession = validate(sessionId, "sessionId");
        Path file = pathFor(safeUser, safeSession);
        ReentrantLock lock = lockFor(file);
        lock.lock();
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            Session session = mapper.readValue(file.toFile(), Session.class);
            if (!safeUser.equals(session.getUserId()) || !safeSession.equals(session.getId())) {
                throw new SessionStoreException("Session file identity mismatch: " + file);
            }
            return Optional.of(session);
        } catch (IOException e) {
            throw new SessionStoreException("Cannot read session " + safeSession, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Session> list(String userId) {
        String safeUser = validate(userId, "userId");
        Path dir = root.resolve(safeUser);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Session> sessions = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    Session session = mapper.readValue(file.toFile(), Session.class);
                    if (safeUser.equals(session.getUserId())) {
                        sessions.add(session);
                    }
                } catch (IOException e) {
                    // A corrupt window must not make every other window unusable.
                    System.err.println("[warn] skip unreadable session file: " + file.getFileName());
                }
            }
        } catch (IOException e) {
            throw new SessionStoreException("Cannot list sessions for user " + safeUser, e);
        }
        sessions.sort(Comparator.comparing(Session::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return sessions;
    }

    @Override
    public void delete(String userId, String sessionId) {
        String safeUser = validate(userId, "userId");
        String safeSession = validate(sessionId, "sessionId");
        Path file = pathFor(safeUser, safeSession);
        ReentrantLock lock = lockFor(file);
        lock.lock();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new SessionStoreException("Cannot delete session " + safeSession, e);
        } finally {
            lock.unlock();
        }
    }

    private Path pathFor(String userId, String sessionId) {
        return root.resolve(userId).resolve(sessionId + ".json");
    }

    private ReentrantLock lockFor(Path file) {
        // 键统一转小写：Windows 路径大小写不敏感，不同大小写拼出的同一文件必须命中同一把锁。
        return locks.computeIfAbsent(file.toString().toLowerCase(Locale.ROOT), ignored -> new ReentrantLock());
    }

    private static String validate(String value, String field) {
        if (value == null || !value.matches(ID_PATTERN)) {
            throw new IllegalArgumentException(field + " must match " + ID_PATTERN + ": " + value);
        }
        return value;
    }
}
