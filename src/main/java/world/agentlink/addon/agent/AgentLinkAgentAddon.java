package world.agentlink.addon.agent;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AgentLinkAgentAddon.MOD_ID)
public final class AgentLinkAgentAddon {
    public static final String MOD_ID = "agentlinkagent";
    public static final Logger LOG = LogUtils.getLogger();

    public AgentLinkAgentAddon() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AgentCommand.register(event);
    }
}
