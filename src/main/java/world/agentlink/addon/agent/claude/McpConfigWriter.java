package world.agentlink.addon.agent.claude;

import world.agentlink.addon.agent.AgentAddonConfig;
import world.agentlink.addon.agent.AgentLinkAgentAddon;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class McpConfigWriter {

    private McpConfigWriter() {}

    /** Writes config/agent-link-agent.mcp.json next to the addon toml. Returns absolute path or null on failure. */
    public static Path write(Path configDir, AgentAddonConfig.Claude cfg) {
        if (cfg.mcpEndpoint() == null || cfg.mcpEndpoint().isBlank()) return null;
        if (cfg.resolvedMcpToken() == null || cfg.resolvedMcpToken().isBlank()) return null;
        Path target = configDir.resolve("agent-link-agent.mcp.json");
        Path tmp = configDir.resolve("agent-link-agent.mcp.json.tmp");
        String body = "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"" + escape(cfg.mcpServerName()) + "\": {\n"
                + "      \"type\": \"http\",\n"
                + "      \"url\": \"" + escape(cfg.mcpEndpoint()) + "\",\n"
                + "      \"headers\": {\n"
                + "        \"Authorization\": \"Bearer " + escape(cfg.resolvedMcpToken()) + "\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        try {
            Files.createDirectories(configDir);
            try (Writer w = new OutputStreamWriter(
                    Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE),
                    StandardCharsets.UTF_8)) {
                w.write(body);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target.toAbsolutePath();
        } catch (IOException ex) {
            AgentLinkAgentAddon.LOG.warn("agent-link-agent: failed to write mcp config: {}", ex.getMessage());
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
