package world.agentlink.addon.agent.claude;

import world.agentlink.addon.agent.AgentAddonConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ClaudeProcessRunner {

    public record Result(boolean success, String stdout, String stderr, String errorMessage) {}

    private ClaudeProcessRunner() {}

    public static Result run(AgentAddonConfig.Claude cfg, String message) {
        String exe = cfg.claudeExecutable();
        if (exe.isBlank()) exe = ExecutableLocator.findClaude();
        if (exe == null) {
            return new Result(false, "", "", "Claude not found. Set claude_executable in config/agent-link-agent.toml");
        }

        List<String> cmd = List.of(exe, "-p", "--resume", cfg.sessionId(), message);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (!cfg.workingDirectory().isBlank()) pb.directory(new File(cfg.workingDirectory()));
        pb.redirectErrorStream(false);

        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            return new Result(false, "", "", "failed to start: " + ex.getMessage());
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
            return new Result(false, "", "", "interrupted");
        }
        if (!finished) {
            p.destroyForcibly();
            return new Result(false, "", "", "Claude timed out after " + cfg.timeoutSeconds() + "s");
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
            return new Result(false, out.toString(), err.toString(), "exit " + code + (tail.isEmpty() ? "" : ": " + tail));
        }
        String stdout = out.toString().trim();
        if (stdout.isEmpty()) return new Result(false, "", err.toString(), "Claude returned no output");
        return new Result(true, stdout, err.toString(), null);
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
