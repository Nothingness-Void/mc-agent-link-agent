package world.agentlink.addon.agent;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import world.agentlink.api.AgentLinkApi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentAddonConfig {

    public record Snapshot(Claude claude) {}

    public record Claude(
            boolean enable,
            String claudeExecutable,
            Map<UUID, String> sessions,
            int timeoutSeconds,
            String workingDirectory,
            int pollIntervalMs,
            String permissionMode,
            String mcpEndpoint,
            String mcpToken,
            String mcpServerName,
            String adminSystemPrompt,
            String opSystemPrompt,
            List<String> opDisallowedTools,
            String resolvedMcpToken
    ) {
        public String sessionFor(UUID uuid) {
            if (uuid == null) return "";
            String s = sessions.get(uuid);
            return s == null ? "" : s;
        }

        /**
         * Returns true only when the player UUID is explicitly listed under the base mod's
         * {@code [roles].admin_uuids}. An empty admin list means "no admins configured" —
         * everyone with /agent access is treated as an ordinary OP, NOT an admin.
         */
        public boolean isAdmin(UUID uuid) {
            if (uuid == null) return false;
            java.util.List<UUID> admins = AgentLinkApi.adminUuids();
            return !admins.isEmpty() && admins.contains(uuid);
        }

        public Claude withResolvedMcpToken(String token) {
            String t = token == null ? "" : token;
            if (t.equals(resolvedMcpToken)) return this;
            return new Claude(enable, claudeExecutable, sessions, timeoutSeconds, workingDirectory,
                    pollIntervalMs, permissionMode, mcpEndpoint, mcpToken, mcpServerName,
                    adminSystemPrompt, opSystemPrompt, opDisallowedTools, t);
        }
    }

    private static final String FILE_NAME = "agent-link-agent.toml";
    private static volatile Snapshot CURRENT;

    private static final String DEFAULT_MCP_ENDPOINT = "http://127.0.0.1:25581/mcp";
    private static final String DEFAULT_MCP_SERVER_NAME = "agentlink";
    private static final String DEFAULT_PERMISSION_MODE = "bypassPermissions";
    private static final String LEGACY_ADMIN_SYSTEM_PROMPT =
            "You are an in-game assistant for a Minecraft server (mc-agent-link). "
                    + "You may call mcp__agentlink__* tools and your own local tools (Bash, Read, Edit, Write, Glob, Grep). "
                    + "Sensitive tool calls (run_console_command, broadcast, write_config_file, spark_*) trigger an in-game approval prompt "
                    + "handled by eligible in-game approvers according to the server's role config. Always explain to the player what you are about to do before invoking tools. "
                    + "Reply concisely; long messages are truncated by the chat layer.";
    private static final String PREVIOUS_ADMIN_SYSTEM_PROMPT =
            "You are an in-game assistant for a Minecraft server (mc-agent-link). "
                    + "You may call mcp__agentlink__* tools and your own local tools (Bash, Read, Edit, Write, Glob, Grep). "
                    + "Sensitive tool calls (run_console_command, broadcast, write_config_file, spark_*) may trigger an in-game approval prompt "
                    + "handled by eligible approvers according to the server's role config. When you need a tool, call it directly. "
                    + "Do NOT reply that permission or approval has been requested, and do NOT ask the player to click allow before the tool call. "
                    + "If approval is required, the mod will show the clickable in-game prompt automatically. Reply concisely after the tool succeeds or fails; long messages are truncated by the chat layer.";
    private static final String DEFAULT_ADMIN_SYSTEM_PROMPT =
            "You are an in-game assistant for a Minecraft server (mc-agent-link). "
                    + "You may call mcp__agentlink__* tools and your own local tools (Bash, Read, Edit, Write, Glob, Grep). "
                    + "Sensitive tool calls (run_console_command, broadcast, write_config_file, spark_*) may trigger an in-game approval prompt "
                    + "handled by eligible approvers according to the server's role config. When you need a tool, call it directly. "
                    + "Do NOT reply that permission or approval has been requested, and do NOT ask the player to click allow before the tool call. "
                    + "If approval is required, the mod will show the clickable in-game prompt automatically. Reply to the player in Simplified Chinese unless they clearly ask for another language. "
                    + "Reply concisely after the tool succeeds or fails; long messages are truncated by the chat layer.";
    private static final String LEGACY_OP_SYSTEM_PROMPT =
            "You are an in-game assistant for a Minecraft server (mc-agent-link). "
                    + "The current player is an OP using /agent, but not a configured admin under the server's role config. "
                    + "You may call ordinary low-risk mcp__agentlink__* tools for live status, logs, players, mods, and diagnostics. "
                    + "You MUST NOT call admin-only MCP tools such as run_console_command, write_config_file, broadcast, spark_profiler_*, read_server_file, or list_dir. "
                    + "Always explain briefly what you are about to check before invoking tools. Reply concisely; long messages are truncated by the chat layer.";
    private static final String PREVIOUS_OP_SYSTEM_PROMPT =
            "You are an in-game assistant for a Minecraft server (mc-agent-link). "
                    + "The current player is an OP using /agent, but not a configured admin under the server's role config. "
                    + "You may call ordinary low-risk mcp__agentlink__* tools for live status, logs, players, mods, and diagnostics. "
                    + "You MUST NOT call admin-only MCP tools such as run_console_command, write_config_file, broadcast, spark_profiler_*, read_server_file, or list_dir. "
                    + "When you need an allowed tool, call it directly. Do NOT reply that permission or approval has been requested, and do NOT ask the player to click allow first. "
                    + "If approval is required anyway, the mod will show the clickable in-game prompt automatically. Reply concisely after the tool succeeds or fails; long messages are truncated by the chat layer.";
    private static final String DEFAULT_OP_SYSTEM_PROMPT =
            "You are an in-game assistant for a Minecraft server (mc-agent-link). "
                    + "The current player is an OP using /agent, but not a configured admin under the server's role config. "
                    + "You may call ordinary low-risk mcp__agentlink__* tools for live status, logs, players, mods, and diagnostics. "
                    + "You MUST NOT call admin-only MCP tools such as run_console_command, write_config_file, broadcast, spark_profiler_*, read_server_file, or list_dir. "
                    + "When you need an allowed tool, call it directly. Do NOT reply that permission or approval has been requested, and do NOT ask the player to click allow first. "
                    + "If approval is required anyway, the mod will show the clickable in-game prompt automatically. Reply to the player in Simplified Chinese unless they clearly ask for another language. "
                    + "Reply concisely after the tool succeeds or fails; long messages are truncated by the chat layer.";
    private static final List<String> DEFAULT_OP_DISALLOWED_TOOLS = List.of(
            "Bash", "Edit", "Write",
            "mcp__agentlink__run_console_command",
            "mcp__agentlink__write_config_file",
            "mcp__agentlink__broadcast",
            "mcp__agentlink__spark_profiler_start",
            "mcp__agentlink__spark_profiler_stop",
            "mcp__agentlink__spark_profiler_cancel",
            "mcp__agentlink__read_server_file",
            "mcp__agentlink__list_dir"
    );

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
            int timeoutSeconds = Math.max(1, getInt(cfg, "claude.timeout_seconds", 600));
            String workingDirectory = getString(cfg, "claude.working_directory", "");
            int pollIntervalMs = Math.max(100, getInt(cfg, "claude.poll_interval_ms", 1500));
            String permissionMode = getString(cfg, "claude.permission_mode", DEFAULT_PERMISSION_MODE);

            String mcpEndpoint = getString(cfg, "claude.mcp_endpoint", DEFAULT_MCP_ENDPOINT);
            String mcpToken = getString(cfg, "claude.mcp_token", "");
            String mcpServerName = getString(cfg, "claude.mcp_server_name", DEFAULT_MCP_SERVER_NAME);
            String adminPrompt = normalizePrompt(getString(cfg, "claude.admin_system_prompt", DEFAULT_ADMIN_SYSTEM_PROMPT),
                    DEFAULT_ADMIN_SYSTEM_PROMPT, LEGACY_ADMIN_SYSTEM_PROMPT, PREVIOUS_ADMIN_SYSTEM_PROMPT);
            String opPrompt = normalizePrompt(getString(cfg, "claude.op_system_prompt", DEFAULT_OP_SYSTEM_PROMPT),
                    DEFAULT_OP_SYSTEM_PROMPT, LEGACY_OP_SYSTEM_PROMPT, PREVIOUS_OP_SYSTEM_PROMPT);
            List<String> opDisallowed = readStringList(cfg, "claude.op_disallowed_tools", DEFAULT_OP_DISALLOWED_TOOLS);

            // Drop legacy claude.admin_uuids — moved to base mod's [roles].admin_uuids in agent-link.toml.
            if (cfg.contains("claude.admin_uuids")) {
                Object legacy = cfg.get("claude.admin_uuids");
                cfg.remove("claude.admin_uuids");
                AgentLinkAgentAddon.LOG.warn("agent-link-agent: claude.admin_uuids is no longer read here; move these UUIDs to [roles].admin_uuids in config/agent-link.toml. Removed value: {}", legacy);
            }

            Map<UUID, String> sessions = new LinkedHashMap<>();
            String legacySessionId = getString(cfg, "claude.session_id", "");

            Object sessionsTable = cfg.get("claude.sessions");
            if (sessionsTable instanceof Config sessionsCfg) {
                for (Map.Entry<String, Object> entry : sessionsCfg.valueMap().entrySet()) {
                    UUID uuid = parseUuid(entry.getKey());
                    if (uuid == null) {
                        AgentLinkAgentAddon.LOG.warn("agent-link-agent: ignoring invalid uuid in claude.sessions: {}", entry.getKey());
                        continue;
                    }
                    if (entry.getValue() == null) continue;
                    String sid = String.valueOf(entry.getValue()).trim();
                    if (!sid.isEmpty()) sessions.put(uuid, sid);
                }
            }

            cfg.set("claude.enable", enable);
            cfg.setComment("claude.enable", " 是否启用 Claude 自动桥接。默认关: 行为同 0.1.x, 请求堆队列等外部 agent。");
            cfg.set("claude.claude_executable", claudeExecutable);
            cfg.setComment("claude.claude_executable", " claude.cmd / claude.exe / claude.bat 的绝对路径。留空则在 PATH 里查找。");
            cfg.set("claude.timeout_seconds", timeoutSeconds);
            cfg.setComment("claude.timeout_seconds", " 单次调用超时(秒)。包含 Claude 启动 + 模型推理 + 所有 MCP 工具调用 + 游戏内审批等待。\n 默认 600 = 10 分钟，应付一次涉及多次审批 / 多次 MCP 工具调用的复杂请求。\n 0.4.1 起，超时不会丢 session id —— 下次 /agent 会 --resume 同一会话。\n 仍需注意：超时会强杀 Claude 子进程，未完成的工具调用不会撤销。");
            cfg.set("claude.working_directory", workingDirectory);
            cfg.setComment("claude.working_directory", " Claude Code 启动 cwd, 留空则用服务器根目录。影响 --resume 从哪个 project 目录读 JSONL。");
            cfg.set("claude.poll_interval_ms", pollIntervalMs);
            cfg.setComment("claude.poll_interval_ms", " 队列轮询间隔(毫秒)。");
            cfg.set("claude.permission_mode", permissionMode);
            cfg.setComment("claude.permission_mode",
                    " Claude Code CLI 的 --permission-mode。默认 bypassPermissions, 让 MCP 请求直接到达 Minecraft 侧审批而不是先卡在 Claude 客户端弹窗。");

            cfg.set("claude.mcp_endpoint", mcpEndpoint);
            cfg.setComment("claude.mcp_endpoint",
                    "\n 基础 mod (mc-agent-link) 的 MCP HTTP 端点。默认 127.0.0.1:25581/mcp, 与 agent-link.toml mcp_listen_port 对齐。");
            cfg.set("claude.mcp_token", mcpToken);
            cfg.setComment("claude.mcp_token",
                    "\n MCP Bearer token。留空则启动时从基础 mod 自动读取并跟随 (优先 AgentLinkApi.config().mcpToken())。");
            cfg.set("claude.mcp_server_name", mcpServerName);
            cfg.setComment("claude.mcp_server_name",
                    "\n MCP 服务器在 mcp.json 里注册的名字。Claude 看到的工具名形如 mcp__<这里>__<tool>。改了要同步改 op_disallowed_tools。");
            cfg.set("claude.admin_system_prompt", adminPrompt);
            cfg.setComment("claude.admin_system_prompt",
                    "\n 白名单玩家请求时附加给 Claude 的 system prompt。提示它有 mcp__agentlink__* 工具且玩家会在游戏内授权。");
            cfg.set("claude.op_system_prompt", opPrompt);
            cfg.setComment("claude.op_system_prompt",
                    "\n 普通 OP（能用 /agent 但不在 [roles].admin_uuids 里）请求时附加给 Claude 的 system prompt。允许默认安全工具, 禁止 admin-only 工具。");
            cfg.set("claude.op_disallowed_tools", opDisallowed);
            cfg.setComment("claude.op_disallowed_tools",
                    "\n 普通 OP 模式下传给 claude --disallowedTools 的工具名列表。默认禁本地高危工具和 admin-only MCP 工具。");

            if (cfg.contains("claude.session_id")) {
                cfg.remove("claude.session_id");
            }
            if (!legacySessionId.isBlank() && sessions.isEmpty()) {
                AgentLinkAgentAddon.LOG.info("agent-link-agent: dropping legacy global session_id={}; sessions are now per-player and will be (re)created on next call",
                        legacySessionId);
            }

            writeSessionsTable(cfg, sessions);

            cfg.save();

            CURRENT = new Snapshot(new Claude(enable, claudeExecutable,
                    Collections.unmodifiableMap(new LinkedHashMap<>(sessions)),
                    timeoutSeconds, workingDirectory, pollIntervalMs, permissionMode,
                    mcpEndpoint, mcpToken, mcpServerName,
                    adminPrompt, opPrompt,
                    Collections.unmodifiableList(new ArrayList<>(opDisallowed)),
                    ""));
            if (fresh) {
                AgentLinkAgentAddon.LOG.info("agent-link-agent wrote default config to {}", path);
            }
        }
    }

    public static synchronized void updateClaudeSessionId(UUID playerUuid, String sessionId) {
        if (playerUuid == null) return;
        String nextSessionId = sessionId == null ? "" : sessionId.trim();
        Snapshot snap = CURRENT;
        if (snap == null || snap.claude() == null) return;

        Map<UUID, String> next = new LinkedHashMap<>(snap.claude().sessions());
        if (nextSessionId.isEmpty()) {
            if (next.remove(playerUuid) == null) return;
        } else {
            String prev = next.put(playerUuid, nextSessionId);
            if (nextSessionId.equals(prev)) return;
        }

        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                .preserveInsertionOrder()
                .build()) {
            cfg.load();
            writeSessionsTable(cfg, next);
            cfg.save();
        }

        Claude old = snap.claude();
        CURRENT = new Snapshot(new Claude(old.enable(), old.claudeExecutable(),
                Collections.unmodifiableMap(next),
                old.timeoutSeconds(), old.workingDirectory(), old.pollIntervalMs(), old.permissionMode(),
                old.mcpEndpoint(), old.mcpToken(), old.mcpServerName(),
                old.adminSystemPrompt(), old.opSystemPrompt(), old.opDisallowedTools(),
                old.resolvedMcpToken()));
    }

    public static synchronized void setResolvedMcpToken(String token) {
        Snapshot snap = CURRENT;
        if (snap == null || snap.claude() == null) return;
        Claude updated = snap.claude().withResolvedMcpToken(token);
        if (updated == snap.claude()) return;
        CURRENT = new Snapshot(updated);
    }

    private static void writeSessionsTable(CommentedFileConfig cfg, Map<UUID, String> sessions) {
        CommentedConfig table = CommentedConfig.inMemory();
        for (Map.Entry<UUID, String> entry : sessions.entrySet()) {
            table.valueMap().put(entry.getKey().toString(), entry.getValue());
        }
        cfg.set("claude.sessions", table);
        cfg.setComment("claude.sessions",
                " 每玩家独立 Claude 会话 ID, key 为玩家 UUID。建议留空表: 首次成功调用后会自动写回真实 session_id。");
    }

    private static String normalizePrompt(String value, String currentDefault, String... oldDefaults) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return currentDefault;
        for (String oldDefault : oldDefaults) {
            if (text.equals(oldDefault)) return currentDefault;
        }
        return value;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<String> readStringList(CommentedFileConfig cfg, String key, List<String> fallback) {
        Object raw = cfg.get(key);
        if (raw == null) return new ArrayList<>(fallback);
        if (!(raw instanceof List<?> list)) {
            AgentLinkAgentAddon.LOG.warn("agent-link-agent: config key '{}' is not a list; using default", key);
            return new ArrayList<>(fallback);
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) continue;
            String s = String.valueOf(item).trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
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
