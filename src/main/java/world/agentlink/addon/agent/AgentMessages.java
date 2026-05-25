package world.agentlink.addon.agent;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Locale;

final class AgentMessages {

    enum Key {
        HELP_ASK,
        HELP_STATUS,
        HELP_RELOAD,
        HELP_CANCEL,
        NO_AGENT_ACTIVITY,
        LAST_AGENT_ACTIVITY,
        NO_REQUESTS,
        RECENT_REQUESTS,
        RECENT_ITEM,
        UNKNOWN_ID,
        ALREADY_STATUS,
        CANCELED,
        RELOAD_STARTED,
        RELOAD_DISABLED,
        RELOAD_NOT_FOUND,
        RELOAD_SERVER_NOT_STARTED,
        RELOAD_FAILED,
        USAGE,
        QUEUED
    }

    private AgentMessages() {}

    static String tr(ServerPlayer player, Key key, Object... args) {
        String pattern = switch (language(player)) {
            case ZH_CN -> zh(key);
            case EN_US -> en(key);
        };
        return String.format(Locale.ROOT, pattern, args);
    }

    private static Lang language(ServerPlayer player) {
        String raw = readLanguage(player);
        if (raw == null || raw.isBlank()) return Lang.ZH_CN;
        if (raw != null && raw.toLowerCase(Locale.ROOT).startsWith("zh")) return Lang.ZH_CN;
        return Lang.EN_US;
    }

    private static String readLanguage(ServerPlayer player) {
        try {
            Method clientInformation = player.getClass().getMethod("clientInformation");
            Object info = clientInformation.invoke(player);
            Method language = info.getClass().getMethod("language");
            Object value = language.invoke(info);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String zh(Key key) {
        return switch (key) {
            case HELP_ASK -> "[Agent] /agent <请求> - 向已连接的 agent 提交游戏内请求";
            case HELP_STATUS -> "[Agent] /agent status - 查看最近请求和 agent 活跃状态";
            case HELP_RELOAD -> "[Agent] /agent reload - 重载 agent-link-agent 配置并重启 Claude 桥接";
            case HELP_CANCEL -> "[Agent] /agent cancel <id> - 取消一个未完成请求";
            case NO_AGENT_ACTIVITY -> "[Agent] 还没有看到 agent 活跃。";
            case LAST_AGENT_ACTIVITY -> "[Agent] 上次 agent 活跃在 %d 秒前%s。";
            case NO_REQUESTS -> "[Agent] 还没有请求。";
            case RECENT_REQUESTS -> "[Agent] 最近请求:";
            case RECENT_ITEM -> "[Agent] %s %s%s :: %s";
            case UNKNOWN_ID -> "[Agent] 未找到请求 id: %s";
            case ALREADY_STATUS -> "[Agent] 请求 %s 已经是 %s。";
            case CANCELED -> "[Agent] 请求 %s 已取消。";
            case RELOAD_STARTED -> "[Agent] 配置已重载，Claude 桥接已启动，会话 %s。";
            case RELOAD_DISABLED -> "[Agent] 配置已重载，Claude 桥接当前为关闭状态。";
            case RELOAD_NOT_FOUND -> "[Agent] 配置已重载，但未找到 Claude 可执行文件。请设置 claude_executable 或 PATH。";
            case RELOAD_SERVER_NOT_STARTED -> "[Agent] 服务器尚未启动，无法重载配置。";
            case RELOAD_FAILED -> "[Agent] 配置重载失败: %s";
            case USAGE -> "用法: /agent <请求>";
            case QUEUED -> "[Agent] 已接到消息，编号 %s。";
        };
    }

    private static String en(Key key) {
        return switch (key) {
            case HELP_ASK -> "[Agent] /agent <request> - submit an in-game request to the connected agent";
            case HELP_STATUS -> "[Agent] /agent status - show recent requests and agent activity";
            case HELP_RELOAD -> "[Agent] /agent reload - reload agent-link-agent config and restart the Claude bridge";
            case HELP_CANCEL -> "[Agent] /agent cancel <id> - cancel a pending request";
            case NO_AGENT_ACTIVITY -> "[Agent] No agent activity seen yet.";
            case LAST_AGENT_ACTIVITY -> "[Agent] Last agent activity %d seconds ago%s.";
            case NO_REQUESTS -> "[Agent] No requests yet.";
            case RECENT_REQUESTS -> "[Agent] Recent requests:";
            case RECENT_ITEM -> "[Agent] %s %s%s :: %s";
            case UNKNOWN_ID -> "[Agent] Unknown request id: %s";
            case ALREADY_STATUS -> "[Agent] Request %s is already %s.";
            case CANCELED -> "[Agent] Request %s canceled.";
            case RELOAD_STARTED -> "[Agent] Config reloaded. Claude bridge started, session %s.";
            case RELOAD_DISABLED -> "[Agent] Config reloaded. Claude bridge is disabled.";
            case RELOAD_NOT_FOUND -> "[Agent] Config reloaded, but Claude executable was not found. Set claude_executable or PATH.";
            case RELOAD_SERVER_NOT_STARTED -> "[Agent] Server is not started yet; cannot reload config.";
            case RELOAD_FAILED -> "[Agent] Config reload failed: %s";
            case USAGE -> "Usage: /agent <request>";
            case QUEUED -> "[Agent] Message received as %s.";
        };
    }

    private enum Lang {
        ZH_CN,
        EN_US
    }
}
