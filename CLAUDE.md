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
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
mcp_endpoint = "http://127.0.0.1:25581/mcp"
mcp_token = ""
mcp_server_name = "agentlink"

[claude.sessions]
# 每玩家独立 Claude 会话, key 为玩家 UUID。留空表即可, 首次成功调用后会自动写回。
```

**白名单（admin）UUID 不再写在这里**。从 v0.3.0-alpha 起，admin 列表统一在基础 mod 的 `config/agent-link.toml` `[roles].admin_uuids` 里维护：

```toml
[roles]
admin_uuids = ["aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"]
```

agent 通过 `AgentLinkApi.isAdmin(uuid)` 直接读这一份名单，所有附属 mod 共用。

`/agent` 本身只允许 OP 使用，所以这里没有单独的 guest 模式：

- 普通 OP：默认可用安全/诊断类 MCP 工具
- admin：额外可用 admin-only 工具（最终仍受基础 mod 审批约束）

## What each value means

- `enable = true`: starts the background Claude bridge worker.
- `claude_executable = ""`: search `PATH` for `claude.cmd`, `claude.exe`, `claude.bat`, then `claude`.
- `timeout_seconds = 120`: max time for one Claude request.
- `working_directory = ""`: use the Minecraft server root as Claude Code cwd. Set an absolute path if the user wants Claude to resume a specific project context.
- `poll_interval_ms = 1500`: queue polling interval.
- `mcp_endpoint`: 基础 mod 的 MCP HTTP 端点。默认 `127.0.0.1:25581/mcp`。
- `mcp_token = ""`: 留空则启动时通过 `AgentLinkApi.config().mcpToken()` 自动读基础 mod token，并跟随。
- `mcp_server_name = "agentlink"`: Claude 看到的工具名前缀为 `mcp__<这里>__<tool>`。
- `[claude.sessions]`: 每玩家一份 session_id, 自动写回。

## Most common Windows setup

If Claude Code is installed globally and `claude` works in PowerShell, only set the agent's toml as above + the base mod's `[roles].admin_uuids`. agent 端不需要再写 admin。

If the Minecraft server cannot see the same `PATH`, set `claude_executable` to the full wrapper path:

```toml
[claude]
enable = true
claude_executable = "C:\\Users\\<USER>\\AppData\\Roaming\\npm\\claude.cmd"
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500

[claude.sessions]
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
agent-link-forge-1.20.1-0.2.1-alpha.jar
agent-link-agent-forge-1.20.1-0.3.2-alpha.jar
```

## Smoke test

1. Start the server once so `config/agent-link-agent.toml` is generated.
2. Edit `[claude].enable = true`.
3. As an OP player, run:

```text
/agent reload
```

4. Then run:

```text
/agent 你好，简单介绍一下你是谁
```

Expected result: chat first shows `Claude is thinking...`, then the Claude reply.
