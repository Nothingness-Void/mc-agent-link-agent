# mc-agent-link-agent

Optional Forge addon for [`mc-agent-link`](https://github.com/Nothingness-Void/mc-agent-link).

This addon owns the in-game `/agent` command. The base mod remains a base/lib mod that provides MCP transport, tools, and the shared `AgentRequestBuffer` API.

## Features

- `/agent <request>` queues an OP request for connected MCP agents or companion addons.
- `/agent status` shows recent requests and agent activity.
- `/agent reload` reloads `config/agent-link-agent.toml` and restarts/stops the Claude bridge without restarting the server.
- `/agent cancel <id>` cancels a pending request.
- Optional Claude bridge can process queued requests automatically when enabled.
- Server-side bilingual messages:
  - Chinese for `zh_*` clients.
  - English for other clients.

## Requirements

- Minecraft Forge `1.20.1`
- Java `17`
- `mc-agent-link` base mod installed on the server

## Optional Claude bridge

By default, Claude bridge is disabled and `/agent` behaves like `0.1.x`: requests stay in the base queue for an external MCP agent or companion addon.

The addon writes `config/agent-link-agent.toml` automatically. Enable Claude bridge with:

```toml
[claude]
enable = true
claude_executable = ""
session_id = ""
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
```

When `claude_executable` is empty, the addon searches `PATH` for `claude.cmd`, `claude.exe`, `claude.bat`, then `claude`. All OPs share the same Claude `session_id`.

After editing the config while the server is running, run `/agent reload` as an OP to apply it.

For Claude Code itself, see `CLAUDE.md` for the shortest copy-paste setup guide.

## Build

Build the base mod first:

```powershell
cd ..\mc-agent-link\minecraft\forge-mod
.\gradlew.bat compileJava
```

Then build this addon:

```powershell
cd ..\..\..\mc-agent-link-agent
.\gradlew.bat jar
```

If this repo does not contain a Gradle wrapper yet, run it with an installed Gradle or the wrapper from the base repo.

## Install

Put both jars in the server `mods/` directory:

```text
agent-link-forge-1.20.1-*.jar
agent-link-agent-forge-1.20.1-*.jar
```

Without this addon, the base mod still works, but it does not register `/agent` in-game commands.
