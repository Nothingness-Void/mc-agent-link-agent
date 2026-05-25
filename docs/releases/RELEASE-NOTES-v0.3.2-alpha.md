# mc-agent-link-agent v0.3.2-alpha

> Patch release for the `/agent` Claude bridge permission model.

## Highlights

- Removed the old guest/chat-only path from the Claude bridge.
- `/agent` still requires permission level 2, so every player who can run it is treated as a trusted OP.
- The addon now has only two Claude bridge modes:
  - admin
  - ordinary OP

## Permission model

- Configured admins keep full MCP tool access from the addon side.
- Ordinary OPs now receive MCP config too, so they can use normal low-risk tools such as player/status/log/diagnostic queries.
- Ordinary OPs are still blocked from admin-only tools in the addon layer:
  - `run_console_command`
  - `write_config_file`
  - `broadcast`
  - `spark_profiler_*`
  - `read_server_file`
  - `list_dir`
- Final execution is still enforced by the base mod's approval and sandbox rules.

## Why this matters

Before this change, a non-admin OP could run `/agent` but still end up in a Claude guest mode where all MCP tools were disabled. That was too strict and did not match the command's own OP gate.

Now the behavior matches the product intent:

- OP can use `/agent`
- safe tools work by default
- only explicitly restricted tools require admin privileges

## Compatibility

- Requires Minecraft Forge `1.20.1`.
- Requires Java `17`.
- Requires `mc-agent-link` base mod `0.2.1-alpha` or newer.

## Artifact

| File | Goes into |
|---|---|
| `agent-link-agent-forge-1.20.1-0.3.2-alpha.jar` | `<server>/mods/` |
| `SHA256SUMS.txt` | Verify the jar |
