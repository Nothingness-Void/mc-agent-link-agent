package world.agentlink.addon.agent;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import world.agentlink.agent.AgentRequestBuffer;

import java.util.List;
import java.util.Locale;

final class AgentCommand {

    private AgentCommand() {}

    static void register(RegisterCommandsEvent event, AgentLinkAgentAddon addon) {
        event.getDispatcher().register(Commands.literal("agent")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> help(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("help")
                        .executes(ctx -> help(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource().getPlayerOrException(), addon)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> cancel(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> submit(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "message")))));
    }

    private static int help(ServerPlayer player) {
        send(player, AgentMessages.Key.HELP_ASK, ChatFormatting.AQUA);
        send(player, AgentMessages.Key.HELP_STATUS, ChatFormatting.GRAY);
        send(player, AgentMessages.Key.HELP_RELOAD, ChatFormatting.GRAY);
        send(player, AgentMessages.Key.HELP_CANCEL, ChatFormatting.GRAY);
        return 1;
    }

    private static int status(ServerPlayer player) {
        AgentRequestBuffer buf = AgentRequestBuffer.get();
        long lastSeen = buf.lastAgentSeenAt();
        if (lastSeen <= 0) {
            send(player, AgentMessages.Key.NO_AGENT_ACTIVITY, ChatFormatting.YELLOW);
        } else {
            long ageSeconds = Math.max(0, (System.currentTimeMillis() - lastSeen) / 1000L);
            String action = buf.lastAgentAction();
            String suffix = action == null || action.isBlank() ? "" : " (" + action + ")";
            send(player, AgentMessages.Key.LAST_AGENT_ACTIVITY, ChatFormatting.AQUA, ageSeconds, suffix);
        }

        List<AgentRequestBuffer.Entry> recent = buf.recent(5, true);
        if (recent.isEmpty()) {
            send(player, AgentMessages.Key.NO_REQUESTS, ChatFormatting.GRAY);
            return 1;
        }
        send(player, AgentMessages.Key.RECENT_REQUESTS, ChatFormatting.AQUA);
        for (AgentRequestBuffer.Entry e : recent) {
            String summary = e.message();
            if (summary.length() > 60) summary = summary.substring(0, 60) + "...";
            String status = e.status().name().toLowerCase(Locale.ROOT);
            String statusMessage = e.statusMessage() == null || e.statusMessage().isBlank() ? "" : " - " + e.statusMessage();
            send(player, AgentMessages.Key.RECENT_ITEM, ChatFormatting.GRAY, e.id(), status, statusMessage, summary);
        }
        return 1;
    }

    private static int reload(ServerPlayer player, AgentLinkAgentAddon addon) {
        AgentLinkAgentAddon.ReloadResult result = addon.reloadClaudeConfig();
        switch (result.status()) {
            case "started" -> send(player, AgentMessages.Key.RELOAD_STARTED, ChatFormatting.AQUA, result.sessionId());
            case "disabled" -> send(player, AgentMessages.Key.RELOAD_DISABLED, ChatFormatting.YELLOW);
            case "not_found" -> send(player, AgentMessages.Key.RELOAD_NOT_FOUND, ChatFormatting.RED);
            case "server_not_started" -> send(player, AgentMessages.Key.RELOAD_SERVER_NOT_STARTED, ChatFormatting.RED);
            default -> send(player, AgentMessages.Key.RELOAD_FAILED, ChatFormatting.RED, result.status());
        }
        return result.ok() ? 1 : 0;
    }

    private static int cancel(ServerPlayer player, String id) {
        AgentRequestBuffer.Entry entry = AgentRequestBuffer.get().cancel(id, "canceled by " + player.getGameProfile().getName());
        if (entry == null) {
            send(player, AgentMessages.Key.UNKNOWN_ID, ChatFormatting.RED, id);
            return 0;
        }
        if (entry.status() != AgentRequestBuffer.Status.CANCELED) {
            send(player, AgentMessages.Key.ALREADY_STATUS, ChatFormatting.YELLOW, id, entry.status().name().toLowerCase(Locale.ROOT));
            return 0;
        }
        send(player, AgentMessages.Key.CANCELED, ChatFormatting.AQUA, id);
        return 1;
    }

    private static int submit(ServerPlayer player, String message) {
        if (message == null || message.isBlank()) {
            send(player, AgentMessages.Key.USAGE, ChatFormatting.YELLOW);
            return 0;
        }
        AgentRequestBuffer.Entry entry = AgentRequestBuffer.get().createFromPlayer(player, message);
        send(player, AgentMessages.Key.QUEUED, ChatFormatting.AQUA, entry.id());
        return 1;
    }

    private static void send(ServerPlayer player, AgentMessages.Key key, ChatFormatting style, Object... args) {
        player.sendSystemMessage(Component.literal(AgentMessages.tr(player, key, args)).withStyle(style));
    }
}
