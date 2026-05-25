# Claude Code quick setup for mc-agent-link-agent

This repository builds the optional Forge addon `agentlinkagent` for `mc-agent-link`.

The addon provides `/agent <message>` and can optionally call Claude Code automatically from inside the Minecraft server process.

## Quick configuration goal

If the user wants `/agent` to call Claude Code directly, configure the server file:

```text
<server>/config/agent-link-agent.toml
```

Set:

```toml
[claude]
enable = true
claude_executable = ""
session_id = ""
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
```

## What each value means

- `enable = true`: starts the background Claude bridge worker.
- `claude_executable = ""`: search `PATH` for `claude.cmd`, `claude.exe`, `claude.bat`, then `claude`.
- `session_id = ""`: safe to leave blank before first server start; the mod generates a UUID and writes it back.
- `timeout_seconds = 120`: max time for one Claude request.
- `working_directory = ""`: use the Minecraft server root as Claude Code cwd. Set an absolute path if the user wants Claude to resume a specific project context.
- `poll_interval_ms = 1500`: queue polling interval.

## Most common Windows setup

If Claude Code is installed globally and `claude` works in PowerShell, only set:

```toml
[claude]
enable = true
claude_executable = ""
session_id = ""
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
```

If the Minecraft server cannot see the same `PATH`, set the full wrapper path instead, for example:

```toml
[claude]
enable = true
claude_executable = "C:\\Users\\<USER>\\AppData\\Roaming\\npm\\claude.cmd"
session_id = ""
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
```

Use escaped backslashes in TOML strings.

## Server behavior

- Default `enable = false`: `/agent` only queues requests for external MCP agents or other addons.
- `enable = true` and Claude found: OP runs `/agent hello`, then receives Claude's reply in chat.
- `enable = true` and PATH lookup cannot find Claude: the server still loads; the addon logs a warning and does not start the worker.
- Explicit but wrong `claude_executable`: the worker starts and replies to player requests with a clear `[error] failed to start: ...` message.

## Required jars

Put both jars in `<server>/mods/`:

```text
agent-link-forge-1.20.1-0.1.6-alpha.jar
agent-link-agent-forge-1.20.1-0.2.0-alpha.jar
```

## Smoke test

1. Start the server once so `config/agent-link-agent.toml` is generated.
2. Stop the server.
3. Edit `[claude].enable = true`.
4. Start the server.
5. As an OP player, run:

```text
/agent 你好，简单介绍一下你是谁
```

Expected result: chat first shows `Claude is thinking...`, then the Claude reply.
