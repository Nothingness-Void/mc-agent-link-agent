package world.agentlink.addon.agent.claude;

import java.io.File;

public final class ExecutableLocator {
    private static final String[] CANDIDATES = {"claude.cmd", "claude.exe", "claude.bat", "claude"};

    private ExecutableLocator() {}

    public static String findClaude() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir == null || dir.isBlank()) continue;
            for (String name : CANDIDATES) {
                File f = new File(dir, name);
                if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
            }
        }
        return null;
    }
}
