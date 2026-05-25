package world.agentlink.addon.agent.claude;

import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import world.agentlink.addon.agent.AgentAddonConfig;
import world.agentlink.addon.agent.AgentLang;
import world.agentlink.addon.agent.AgentLinkAgentAddon;
import world.agentlink.agent.AgentRequestBuffer;

import java.nio.file.Path;

public final class ClaudeBridgeWorker implements Runnable {
    private final MinecraftServer mcServer;
    private final AgentAddonConfig.Claude cfg;
    private final Path mcpConfigPath;
    private volatile boolean running = true;
    private long lastSeq;

    public ClaudeBridgeWorker(MinecraftServer s, AgentAddonConfig.Claude c, Path mcpConfigPath) {
        this.mcServer = s;
        this.cfg = c;
        this.mcpConfigPath = mcpConfigPath;
        this.lastSeq = AgentRequestBuffer.get().head();
    }

    @Override
    public void run() {
        AgentRequestBuffer buf = AgentRequestBuffer.get();
        while (running) {
            try {
                var entries = buf.since(lastSeq, 10, false);
                for (var e : entries) {
                    lastSeq = e.seq();
                    if (e.status() != AgentRequestBuffer.Status.PENDING) continue;
                    handle(e);
                }
                Thread.sleep(cfg.pollIntervalMs());
            } catch (InterruptedException ie) {
                return;
            } catch (Exception ex) {
                AgentLinkAgentAddon.LOG.error("claude worker loop error", ex);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void handle(AgentRequestBuffer.Entry e) {
        AgentRequestBuffer buf = AgentRequestBuffer.get();
        AgentRequestBuffer.Entry current = buf.findById(e.id());
        if (current == null || current.status() != AgentRequestBuffer.Status.PENDING) return;

        AgentRequestBuffer.Entry working = buf.updateStatus(e.id(), AgentRequestBuffer.Status.WORKING,
                AgentLang.tr("agentlinkagent.bridge.working"));
        if (working == null) return;
        buf.markAgentSeen("i18n:agentlinkagent.activity.claude_working");
        AgentRequestBuffer.sendStatusToPlayer(mcServer, working);

        boolean admin = cfg.isAdmin(e.playerUuid());
        String roleTag = admin ? "(admin)" : "(op)";
        String prompt = "[Player " + e.playerName() + " " + roleTag + "]: " + e.message();
        ClaudeProcessRunner.Result r = ClaudeProcessRunner.run(cfg, e.playerUuid(), prompt, mcpConfigPath);

        AgentRequestBuffer.Entry done = r.success()
                ? buf.reply(e.id(), r.stdout(), true)
                : buf.reply(e.id(), AgentLang.tr("agentlinkagent.bridge.error_prefix", r.errorMessage()), true);
        if (done == null) return;
        buf.markAgentSeen(r.success() ? "i18n:agentlinkagent.activity.claude_replied" : "i18n:agentlinkagent.activity.claude_failed");
        AgentRequestBuffer.sendReplyToPlayer(mcServer, done);
        if (!r.success()) {
            sendCopyableError(e, r);
        }
    }

    public void stop() {
        running = false;
    }

    private void sendCopyableError(AgentRequestBuffer.Entry e, ClaudeProcessRunner.Result r) {
        if (e.playerUuid() == null) return;
        ServerPlayer player = mcServer.getPlayerList().getPlayer(e.playerUuid());
        if (player == null) return;
        String details = errorDetails(e, r);
        Component msg = Component.literal(AgentLang.tr(player, "agentlinkagent.bridge.copy_error_details"))
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, details))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(AgentLang.tr(player, "agentlinkagent.bridge.copy_error_hover")))));
        player.sendSystemMessage(msg);
    }

    private static String errorDetails(AgentRequestBuffer.Entry e, ClaudeProcessRunner.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append(AgentLang.tr("agentlinkagent.bridge.error_report_title")).append('\n');
        sb.append("request_id=").append(e.id()).append('\n');
        sb.append("player=").append(e.playerName()).append('\n');
        sb.append("session_id=").append(r.sessionId() == null ? "" : r.sessionId()).append('\n');
        sb.append("error=").append(r.errorMessage() == null ? "" : r.errorMessage()).append('\n');
        String stderr = r.stderr() == null ? "" : r.stderr().trim();
        if (!stderr.isEmpty()) {
            sb.append("stderr=\n").append(stderr).append('\n');
        }
        String stdout = r.stdout() == null ? "" : r.stdout().trim();
        if (!stdout.isEmpty()) {
            sb.append("stdout=\n").append(stdout).append('\n');
        }
        return sb.toString();
    }
}
