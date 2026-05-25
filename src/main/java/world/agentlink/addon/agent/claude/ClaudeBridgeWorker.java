package world.agentlink.addon.agent.claude;

import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import world.agentlink.addon.agent.AgentAddonConfig;
import world.agentlink.addon.agent.AgentLinkAgentAddon;
import world.agentlink.agent.AgentRequestBuffer;

public final class ClaudeBridgeWorker implements Runnable {
    private final MinecraftServer mcServer;
    private final AgentAddonConfig.Claude cfg;
    private volatile boolean running = true;
    private long lastSeq;

    public ClaudeBridgeWorker(MinecraftServer s, AgentAddonConfig.Claude c) {
        this.mcServer = s;
        this.cfg = c;
        this.lastSeq = AgentRequestBuffer.get().head();
    }

    @Override
    public void run() {
        AgentRequestBuffer buf = AgentRequestBuffer.get();
        while (running) {
            try {
                var entries = buf.since(lastSeq, 10, false);
                for (var e : entries) {
                    lastSeq = Math.max(lastSeq, e.seq());
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

        AgentRequestBuffer.Entry working = buf.updateStatus(e.id(), AgentRequestBuffer.Status.WORKING, "Claude is thinking...");
        if (working == null) return;
        buf.markAgentSeen("claude working");
        AgentRequestBuffer.sendStatusToPlayer(mcServer, working);

        String prompt = "[Player " + e.playerName() + " (OP)]: " + e.message();
        ClaudeProcessRunner.Result r = ClaudeProcessRunner.run(cfg, prompt);

        AgentRequestBuffer.Entry done = r.success()
                ? buf.reply(e.id(), r.stdout(), true)
                : buf.reply(e.id(), "[error] " + r.errorMessage(), true);
        if (done == null) return;
        buf.markAgentSeen(r.success() ? "claude replied" : "claude failed");
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
        Component msg = Component.literal("[Agent] Click to copy error details")
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, details))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Copy error details for sending to an agent"))));
        player.sendSystemMessage(msg);
    }

    private static String errorDetails(AgentRequestBuffer.Entry e, ClaudeProcessRunner.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("mc-agent-link-agent Claude bridge error\n");
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
