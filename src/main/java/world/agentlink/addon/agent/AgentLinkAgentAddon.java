package world.agentlink.addon.agent;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import world.agentlink.addon.agent.claude.ClaudeBridgeWorker;
import world.agentlink.addon.agent.claude.ExecutableLocator;

@Mod(AgentLinkAgentAddon.MOD_ID)
public final class AgentLinkAgentAddon {
    public static final String MOD_ID = "agentlinkagent";
    public static final Logger LOG = LogUtils.getLogger();

    private ClaudeBridgeWorker claudeWorker;
    private Thread claudeThread;

    public AgentLinkAgentAddon() {
        AgentAddonConfig.load();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AgentCommand.register(event);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        AgentAddonConfig.Claude cfg = AgentAddonConfig.get().claude();
        if (!cfg.enable()) {
            LOG.info("agent-link-agent: claude bridge disabled (set [claude].enable=true to enable)");
            return;
        }
        if (cfg.claudeExecutable().isBlank() && ExecutableLocator.findClaude() == null) {
            LOG.warn("agent-link-agent: claude bridge enabled but Claude executable was not found; worker will not start");
            return;
        }
        claudeWorker = new ClaudeBridgeWorker(event.getServer(), cfg);
        claudeThread = new Thread(claudeWorker, "AgentLink-Claude-Worker");
        claudeThread.setDaemon(true);
        claudeThread.start();
        LOG.info("agent-link-agent: claude bridge started, session={}", cfg.sessionId());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (claudeWorker != null) claudeWorker.stop();
        if (claudeThread != null) claudeThread.interrupt();
        claudeWorker = null;
        claudeThread = null;
    }

}
