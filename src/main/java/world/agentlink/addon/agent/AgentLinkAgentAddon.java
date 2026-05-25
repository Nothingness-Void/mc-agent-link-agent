package world.agentlink.addon.agent;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import world.agentlink.addon.agent.claude.ClaudeBridgeWorker;
import world.agentlink.addon.agent.claude.ExecutableLocator;
import world.agentlink.addon.agent.claude.McpConfigWriter;
import world.agentlink.api.AgentLinkApi;

import java.nio.file.Path;

@Mod(AgentLinkAgentAddon.MOD_ID)
public final class AgentLinkAgentAddon {
    public static final String MOD_ID = "agentlinkagent";
    public static final Logger LOG = LogUtils.getLogger();

    private ClaudeBridgeWorker claudeWorker;
    private Thread claudeThread;
    private MinecraftServer server;

    public AgentLinkAgentAddon() {
        AgentAddonConfig.load();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AgentCommand.register(event, this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        applyClaudeConfig(AgentAddonConfig.get().claude());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stopClaudeWorker();
        server = null;
    }

    synchronized ReloadResult reloadClaudeConfig() {
        if (server == null) {
            return new ReloadResult(false, "server_not_started");
        }
        stopClaudeWorker();
        AgentAddonConfig.load();
        return applyClaudeConfig(AgentAddonConfig.get().claude());
    }

    private synchronized ReloadResult applyClaudeConfig(AgentAddonConfig.Claude cfg) {
        if (!cfg.enable()) {
            LOG.info("agent-link-agent: claude bridge disabled (set [claude].enable=true to enable)");
            return new ReloadResult(true, "disabled");
        }
        if (cfg.claudeExecutable().isBlank() && ExecutableLocator.findClaude() == null) {
            LOG.warn("agent-link-agent: claude bridge enabled but Claude executable was not found; worker will not start");
            return new ReloadResult(false, "not_found");
        }

        String resolvedToken = resolveMcpToken(cfg);
        AgentAddonConfig.setResolvedMcpToken(resolvedToken);
        AgentAddonConfig.Claude effective = AgentAddonConfig.get().claude();

        Path mcpConfigPath = null;
        if (resolvedToken.isBlank()) {
            LOG.warn("agent-link-agent: no MCP token available (set claude.mcp_token, or ensure base mod's agent-link.toml has a token); MCP tools will not be reachable from Claude");
        } else {
            mcpConfigPath = McpConfigWriter.write(FMLPaths.CONFIGDIR.get(), effective);
            if (mcpConfigPath == null) {
                LOG.warn("agent-link-agent: failed to write MCP config file; MCP tools will not be reachable from Claude");
            } else {
                LOG.info("agent-link-agent: wrote MCP config to {}", mcpConfigPath);
            }
        }

        claudeWorker = new ClaudeBridgeWorker(server, effective, mcpConfigPath);
        claudeThread = new Thread(claudeWorker, "AgentLink-Claude-Worker");
        claudeThread.setDaemon(true);
        claudeThread.start();
        LOG.info("agent-link-agent: claude bridge started, sessions cached={}, admins={}, mcp={}",
                effective.sessions().size(), AgentLinkApi.adminUuids().size(),
                mcpConfigPath == null ? "off" : "on");
        return new ReloadResult(true, "started");
    }

    private static String resolveMcpToken(AgentAddonConfig.Claude cfg) {
        if (cfg.mcpToken() != null && !cfg.mcpToken().isBlank()) return cfg.mcpToken();
        try {
            AgentLinkApi.ConfigView view = AgentLinkApi.config();
            if (view != null && view.mcpToken() != null && !view.mcpToken().isBlank()) {
                return view.mcpToken();
            }
        } catch (Throwable ex) {
            LOG.debug("agent-link-agent: cannot read base mod token: {}", ex.getMessage());
        }
        return "";
    }

    private synchronized void stopClaudeWorker() {
        if (claudeWorker != null) claudeWorker.stop();
        if (claudeThread != null) claudeThread.interrupt();
        if (claudeThread != null) {
            try {
                claudeThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        claudeWorker = null;
        claudeThread = null;
    }

    record ReloadResult(boolean ok, String status) {}

}
