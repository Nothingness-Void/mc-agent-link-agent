package world.agentlink.addon.agent.claude;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import world.agentlink.addon.agent.AgentAddonConfig;
import world.agentlink.addon.agent.AgentLang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ClaudeProcessRunner {

    public record Result(boolean success, String stdout, String stderr, String errorMessage, String sessionId) {}

    private ClaudeProcessRunner() {}

    public static Result run(AgentAddonConfig.Claude cfg, UUID playerUuid, String message, Path mcpConfigPath) {
        String existing = cfg.sessionFor(playerUuid);
        Result first = runOnce(cfg, playerUuid, message, existing, mcpConfigPath);
        if (!first.success() && isMissingConversation(first) && !existing.isBlank()) {
            AgentAddonConfig.updateClaudeSessionId(playerUuid, "");
            return runOnce(cfg, playerUuid, message, "", mcpConfigPath);
        }
        return first;
    }

    private static Result runOnce(AgentAddonConfig.Claude cfg, UUID playerUuid, String message, String sessionId, Path mcpConfigPath) {
        String exe = cfg.claudeExecutable();
        if (exe.isBlank()) exe = ExecutableLocator.findClaude();
        if (exe == null) {
            return new Result(false, "", "", AgentLang.tr("agentlinkagent.claude.not_found"), "");
        }

        boolean admin = cfg.isAdmin(playerUuid);

        // Pre-allocate a session id so the conversation is recoverable even if the child process
        // is killed mid-flight (timeout, server stop). The CLI accepts --session-id <uuid> for new
        // conversations and ALSO emits the same id back in the JSON `session_id` field, so resume
        // semantics work exactly the same as before. We persist this id eagerly — if the next
        // /agent invocation comes in before the current one completes, --resume will pick up the
        // partial conversation rather than starting over.
        boolean isNewConversation = sessionId.isBlank();
        String preallocatedSessionId = isNewConversation ? UUID.randomUUID().toString() : sessionId;
        if (isNewConversation) {
            AgentAddonConfig.updateClaudeSessionId(playerUuid, preallocatedSessionId);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(exe);
        cmd.add("-p");
        cmd.add("--output-format");
        cmd.add("json");
        if (cfg.permissionMode() != null && !cfg.permissionMode().isBlank()) {
            cmd.add("--permission-mode");
            cmd.add(cfg.permissionMode());
        }
        if (isNewConversation) {
            cmd.add("--session-id");
            cmd.add(preallocatedSessionId);
        } else {
            cmd.add("--resume");
            cmd.add(sessionId);
        }

        if (admin) {
            if (mcpConfigPath != null) {
                cmd.add("--mcp-config");
                cmd.add(mcpConfigPath.toString());
            }
            if (cfg.adminSystemPrompt() != null && !cfg.adminSystemPrompt().isBlank()) {
                cmd.add("--append-system-prompt");
                cmd.add(cfg.adminSystemPrompt());
            }
        } else {
            if (mcpConfigPath != null) {
                cmd.add("--mcp-config");
                cmd.add(mcpConfigPath.toString());
            }
            String disallowed = String.join(",", cfg.opDisallowedTools());
            if (!disallowed.isBlank()) {
                cmd.add("--disallowedTools");
                cmd.add(disallowed);
            }
            if (cfg.opSystemPrompt() != null && !cfg.opSystemPrompt().isBlank()) {
                cmd.add("--append-system-prompt");
                cmd.add(cfg.opSystemPrompt());
            }
        }

        cmd.add(message);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (!cfg.workingDirectory().isBlank()) pb.directory(new File(cfg.workingDirectory()));
        pb.redirectErrorStream(false);

        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            return new Result(false, "", "", AgentLang.tr("agentlinkagent.claude.start_failed", ex.getMessage()), preallocatedSessionId);
        }

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread to = new Thread(() -> drain(p.getInputStream(), out), "AgentLink-Claude-stdout");
        Thread te = new Thread(() -> drain(p.getErrorStream(), err), "AgentLink-Claude-stderr");
        to.setDaemon(true);
        te.setDaemon(true);
        to.start();
        te.start();

        boolean finished;
        try {
            finished = p.waitFor(cfg.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            return new Result(false, out.toString(), err.toString(),
                    AgentLang.tr("agentlinkagent.claude.interrupted"), preallocatedSessionId);
        }
        if (!finished) {
            // Hard kill, but FIRST flush whatever the streams already produced so the operator gets
            // some context. The drain threads are still draining; give them a brief window to catch
            // up before we report.
            p.destroyForcibly();
            try {
                to.join(500);
                te.join(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            String stdoutTail = tailSnippet(out.toString(), 600);
            String stderrTail = tailSnippet(err.toString(), 600);
            String detail = buildTimeoutDetail(cfg.timeoutSeconds(), preallocatedSessionId, isNewConversation,
                    stdoutTail, stderrTail);
            return new Result(false, out.toString(), err.toString(), detail, preallocatedSessionId);
        }
        try {
            to.join(2000);
            te.join(2000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        int code = p.exitValue();
        if (code != 0) {
            String tail = err.toString().trim();
            String snippet;
            if (tail.length() > 400) {
                snippet = tail.substring(tail.length() - 400) + " [truncated]";
            } else {
                snippet = tail;
            }
            String suffix = snippet.isEmpty() ? "" : ": " + snippet;
            return new Result(false, out.toString(), err.toString(),
                    AgentLang.tr("agentlinkagent.claude.exit_code", code, suffix), preallocatedSessionId);
        }
        String stdout = out.toString().trim();
        if (stdout.isEmpty()) return new Result(false, "", err.toString(),
                AgentLang.tr("agentlinkagent.claude.no_output"), preallocatedSessionId);
        return parseJsonResult(stdout, err.toString(), playerUuid, preallocatedSessionId);
    }

    private static String tailSnippet(String s, int max) {
        if (s == null) return "";
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.length() <= max) return trimmed;
        return "[…] " + trimmed.substring(trimmed.length() - max);
    }

    /**
     * Build a multi-line, operator-readable timeout message. Lines:
     *   1. "timed out after N seconds. Session id pre-allocated; next /agent will --resume."
     *   2. "session_id=<uuid>" (so the operator can find the conversation file directly)
     *   3. stdout tail (often a partial JSON or last MCP tool call)
     *   4. stderr tail (often the actual blocker — approval pending, network error, etc.)
     */
    private static String buildTimeoutDetail(int timeoutSeconds, String sessionId, boolean wasNew,
                                             String stdoutTail, String stderrTail) {
        StringBuilder sb = new StringBuilder();
        sb.append(AgentLang.tr("agentlinkagent.claude.timed_out", timeoutSeconds));
        sb.append('\n');
        sb.append(AgentLang.tr("agentlinkagent.claude.timed_out.session_hint", sessionId,
                wasNew ? AgentLang.tr("agentlinkagent.claude.timed_out.new")
                        : AgentLang.tr("agentlinkagent.claude.timed_out.resumed")));
        if (!stdoutTail.isEmpty()) {
            sb.append('\n');
            sb.append(AgentLang.tr("agentlinkagent.claude.timed_out.stdout_tail", stdoutTail));
        }
        if (!stderrTail.isEmpty()) {
            sb.append('\n');
            sb.append(AgentLang.tr("agentlinkagent.claude.timed_out.stderr_tail", stderrTail));
        }
        return sb.toString();
    }

    private static Result parseJsonResult(String stdout, String stderr, UUID playerUuid, String fallbackSessionId) {
        try {
            JsonObject json = JsonParser.parseString(stdout).getAsJsonObject();
            String result = json.has("result") && !json.get("result").isJsonNull()
                    ? json.get("result").getAsString().trim()
                    : "";
            String sessionId = json.has("session_id") && !json.get("session_id").isJsonNull()
                    ? json.get("session_id").getAsString().trim()
                    : fallbackSessionId;
            if (!sessionId.isBlank()) {
                AgentAddonConfig.updateClaudeSessionId(playerUuid, sessionId);
            }
            if (result.isEmpty()) {
                return new Result(false, stdout, stderr, AgentLang.tr("agentlinkagent.claude.no_result"), sessionId);
            }
            return new Result(true, result, stderr, null, sessionId);
        } catch (RuntimeException ex) {
            String preview = stdout.length() > 400 ? stdout.substring(0, 400) + " [truncated]" : stdout;
            return new Result(false, stdout, stderr,
                    AgentLang.tr("agentlinkagent.claude.non_json", preview), fallbackSessionId);
        }
    }

    private static boolean isMissingConversation(Result result) {
        String text = ((result.errorMessage() == null ? "" : result.errorMessage()) + "\n"
                + (result.stderr() == null ? "" : result.stderr()) + "\n"
                + (result.stdout() == null ? "" : result.stdout())).toLowerCase();
        return text.contains("no conversation found with session id");
    }

    private static void drain(InputStream in, StringBuilder buf) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                buf.append(line).append('\n');
            }
        } catch (IOException ignored) {
        }
    }
}
