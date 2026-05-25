package world.agentlink.addon.agent.claude;

import net.minecraft.server.MinecraftServer;
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
    }

    public void stop() {
        running = false;
    }
}
