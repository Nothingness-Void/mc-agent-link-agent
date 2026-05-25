package world.agentlink.addon.agent;

import net.minecraft.server.level.ServerPlayer;

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
        return AgentLang.tr(player, key(key), args);
    }

    private static String key(Key key) {
        return switch (key) {
            case HELP_ASK -> "agentlinkagent.help.ask";
            case HELP_STATUS -> "agentlinkagent.help.status";
            case HELP_RELOAD -> "agentlinkagent.help.reload";
            case HELP_CANCEL -> "agentlinkagent.help.cancel";
            case NO_AGENT_ACTIVITY -> "agentlinkagent.status.no_agent_activity";
            case LAST_AGENT_ACTIVITY -> "agentlinkagent.status.last_agent_activity";
            case NO_REQUESTS -> "agentlinkagent.status.no_requests";
            case RECENT_REQUESTS -> "agentlinkagent.status.recent_requests";
            case RECENT_ITEM -> "agentlinkagent.status.recent_item";
            case UNKNOWN_ID -> "agentlinkagent.request.unknown_id";
            case ALREADY_STATUS -> "agentlinkagent.request.already_status";
            case CANCELED -> "agentlinkagent.request.canceled";
            case RELOAD_STARTED -> "agentlinkagent.reload.started";
            case RELOAD_DISABLED -> "agentlinkagent.reload.disabled";
            case RELOAD_NOT_FOUND -> "agentlinkagent.reload.not_found";
            case RELOAD_SERVER_NOT_STARTED -> "agentlinkagent.reload.server_not_started";
            case RELOAD_FAILED -> "agentlinkagent.reload.failed";
            case USAGE -> "agentlinkagent.usage";
            case QUEUED -> "agentlinkagent.request.queued";
        };
    }
}
