package world.agentlink.addon.agent.claude;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import world.agentlink.addon.agent.AgentAddonConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ClaudeProcessRunner {

    public record Result(boolean success, String stdout, String stderr, String errorMessage, String sessionId) {}

    private ClaudeProcessRunner() {}

    public static Result run(AgentAddonConfig.Claude cfg, String message) {
        Result first = runOnce(cfg, message, cfg.sessionId());
        if (!first.success() && isMissingConversation(first) && !cfg.sessionId().isBlank()) {
            AgentAddonConfig.updateClaudeSessionId("");
            return runOnce(new AgentAddonConfig.Claude(cfg.enable(), cfg.claudeExecutable(), "",
                    cfg.timeoutSeconds(), cfg.workingDirectory(), cfg.pollIntervalMs()), message, "");
        }
        return first;
    }

    private static Result runOnce(AgentAddonConfig.Claude cfg, String message, String sessionId) {
        String exe = cfg.claudeExecutable();
        if (exe.isBlank()) exe = ExecutableLocator.findClaude();
        if (exe == null) {
            return new Result(false, "", "", "Claude not found. Set claude_executable in config/agent-link-agent.toml", "");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(exe);
        cmd.add("-p");
        cmd.add("--output-format");
        cmd.add("json");
        if (!sessionId.isBlank()) {
            cmd.add("--resume");
            cmd.add(sessionId);
        }
        cmd.add(message);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (!cfg.workingDirectory().isBlank()) pb.directory(new File(cfg.workingDirectory()));
        pb.redirectErrorStream(false);

        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            return new Result(false, "", "", "failed to start: " + ex.getMessage(), sessionId);
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
            return new Result(false, "", "", "interrupted", sessionId);
        }
        if (!finished) {
            p.destroyForcibly();
            return new Result(false, "", "", "Claude timed out after " + cfg.timeoutSeconds() + "s", sessionId);
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
            if (tail.length() > 400) tail = tail.substring(tail.length() - 400);
            return new Result(false, out.toString(), err.toString(), "exit " + code + (tail.isEmpty() ? "" : ": " + tail), sessionId);
        }
        String stdout = out.toString().trim();
        if (stdout.isEmpty()) return new Result(false, "", err.toString(), "Claude returned no output", sessionId);
        return parseJsonResult(stdout, err.toString(), sessionId);
    }

    private static Result parseJsonResult(String stdout, String stderr, String fallbackSessionId) {
        try {
            JsonObject json = JsonParser.parseString(stdout).getAsJsonObject();
            String result = json.has("result") && !json.get("result").isJsonNull()
                    ? json.get("result").getAsString().trim()
                    : "";
            String sessionId = json.has("session_id") && !json.get("session_id").isJsonNull()
                    ? json.get("session_id").getAsString().trim()
                    : fallbackSessionId;
            if (!sessionId.isBlank()) {
                AgentAddonConfig.updateClaudeSessionId(sessionId);
            }
            if (result.isEmpty()) {
                return new Result(false, stdout, stderr, "Claude returned no result", sessionId);
            }
            return new Result(true, result, stderr, null, sessionId);
        } catch (RuntimeException ex) {
            return new Result(true, stdout, stderr, null, fallbackSessionId);
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
                synchronized (buf) {
                    buf.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
        }
    }
}
