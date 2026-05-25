package world.agentlink.addon.agent;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AgentAddonConfig {

    public record Snapshot(Claude claude) {}

    public record Claude(
            boolean enable,
            String claudeExecutable,
            String sessionId,
            int timeoutSeconds,
            String workingDirectory,
            int pollIntervalMs
    ) {}

    private static final String FILE_NAME = "agent-link-agent.toml";
    private static Snapshot CURRENT;

    private AgentAddonConfig() {}

    public static Snapshot get() {
        return CURRENT;
    }

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        boolean fresh = !Files.exists(path);
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                .preserveInsertionOrder()
                .build()) {
            cfg.load();

            boolean enable = getBoolean(cfg, "claude.enable", false);
            String claudeExecutable = getString(cfg, "claude.claude_executable", "");
            String sessionId = getString(cfg, "claude.session_id", "");
            int timeoutSeconds = Math.max(1, getInt(cfg, "claude.timeout_seconds", 120));
            String workingDirectory = getString(cfg, "claude.working_directory", "");
            int pollIntervalMs = Math.max(100, getInt(cfg, "claude.poll_interval_ms", 1500));

            cfg.set("claude.enable", enable);
            cfg.setComment("claude.enable", " 是否启用 Claude 自动桥接。默认关: 行为同 0.1.x, 请求堆队列等外部 agent。");
            cfg.set("claude.claude_executable", claudeExecutable);
            cfg.setComment("claude.claude_executable", " claude.cmd / claude.exe / claude.bat 的绝对路径。留空则在 PATH 里查找。");
            cfg.set("claude.session_id", sessionId);
            cfg.setComment("claude.session_id", " 共享 Claude 会话 ID。建议留空: 首次成功调用 Claude 后会写回真实 session_id。不要手动填写随机 UUID。");
            cfg.set("claude.timeout_seconds", timeoutSeconds);
            cfg.setComment("claude.timeout_seconds", " 单次调用超时(秒)。Claude 历史长时冷启可能 5~10s, 留宽松。");
            cfg.set("claude.working_directory", workingDirectory);
            cfg.setComment("claude.working_directory", " Claude Code 启动 cwd, 留空则用服务器根目录。影响 --resume 从哪个 project 目录读 JSONL。");
            cfg.set("claude.poll_interval_ms", pollIntervalMs);
            cfg.setComment("claude.poll_interval_ms", " 队列轮询间隔(毫秒)。");
            cfg.save();

            CURRENT = new Snapshot(new Claude(enable, claudeExecutable, sessionId, timeoutSeconds, workingDirectory, pollIntervalMs));
            if (fresh) {
                AgentLinkAgentAddon.LOG.info("agent-link-agent wrote default config to {}", path);
            }
        }
    }

    public static synchronized void updateClaudeSessionId(String sessionId) {
        String nextSessionId = sessionId == null ? "" : sessionId;
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                .preserveInsertionOrder()
                .build()) {
            cfg.load();
            cfg.set("claude.session_id", nextSessionId);
            cfg.save();
        }
        if (CURRENT != null && CURRENT.claude() != null) {
            Claude old = CURRENT.claude();
            CURRENT = new Snapshot(new Claude(old.enable(), old.claudeExecutable(), nextSessionId,
                    old.timeoutSeconds(), old.workingDirectory(), old.pollIntervalMs()));
        }
    }

    private static String getString(CommentedFileConfig cfg, String key, String fallback) {
        Object raw = cfg.get(key);
        return raw == null ? fallback : String.valueOf(raw);
    }

    private static boolean getBoolean(CommentedFileConfig cfg, String key, boolean fallback) {
        Object raw = cfg.get(key);
        if (raw == null) return fallback;
        if (raw instanceof Boolean b) return b;
        AgentLinkAgentAddon.LOG.warn("agent-link-agent: config key '{}' is not a boolean; using default", key);
        return fallback;
    }

    private static int getInt(CommentedFileConfig cfg, String key, int fallback) {
        Object raw = cfg.get(key);
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        AgentLinkAgentAddon.LOG.warn("agent-link-agent: config key '{}' is not a number; using default", key);
        return fallback;
    }
}
