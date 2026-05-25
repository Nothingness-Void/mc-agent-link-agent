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
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
mcp_endpoint = "http://127.0.0.1:25581/mcp"
mcp_token = ""
mcp_server_name = "agentlink"

[claude.sessions]
# 每玩家独立 Claude 会话 ID, 首次成功调用后会自动写回。
```

When `claude_executable` is empty, the addon searches `PATH` for `claude.cmd`, `claude.exe`, `claude.bat`, then `claude`. Each player keeps their own Claude conversation under `[claude.sessions]`, keyed by player UUID.

**Admin whitelist now lives in the base mod.** Edit `config/agent-link.toml` and add your trusted player UUIDs under `[roles].admin_uuids`:

```toml
[roles]
admin_uuids = ["aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"]
```

This addon now treats every player who can run `/agent` as trusted enough to use ordinary tools:

- configured admins: full MCP tool access, with high-impact actions still gated by the base mod's in-game approval flow
- ordinary OPs: safe/diagnostic MCP tools are allowed by default, but admin-only tools remain blocked in the addon layer

There is no separate guest/chat-only mode for `/agent`, because the command itself already requires OP permission level 2. The same `[roles].admin_uuids` list is shared by every addon mod that depends on mc-agent-link 0.2.0+.

`mcp_token` 留空则启动时通过基础 mod 的公共 API (`AgentLinkApi.config().mcpToken()`) 自动同步。token 改了或基础 mod 重置 token 后，重启服务器或 `/agent reload` 即可同步。

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
agent-link-forge-1.20.1-0.2.3-alpha.jar
agent-link-agent-forge-1.20.1-0.3.3-alpha.jar
```

Without this addon, the base mod still works, but it does not register `/agent` in-game commands.
