# mc-agent-link-agent v0.2.0-alpha

> Alpha release with an optional Claude Code bridge for the in-game `/agent` command.

## Highlights

- Added optional Claude automatic bridge in the same `agentlinkagent` mod.
- Default behavior remains unchanged: `[claude].enable = false`, so `/agent` requests stay in the base queue for an external MCP agent or companion addon.
- Enable with `config/agent-link-agent.toml`:

```toml
[claude]
enable = true
claude_executable = ""
session_id = ""
timeout_seconds = 120
working_directory = ""
poll_interval_ms = 1500
```

- When `claude_executable` is empty, the addon searches `PATH` for `claude.cmd`, `claude.exe`, `claude.bat`, then `claude`.
- All OPs share one Claude `session_id`, so the in-game agent can keep shared conversation history.
- Requests are processed serially to avoid concurrent writes to the same Claude `--resume` session.
- If Claude is not found while using PATH lookup, the server still loads and logs a warning instead of crashing.

## Compatibility

- Requires Minecraft Forge `1.20.1`.
- Requires Java `17`.
- Requires `mc-agent-link` base mod `0.1.6-alpha` or newer.
- If Claude bridge is disabled or unavailable, behavior is compatible with `0.1.x`.

## Artifact

| File | Goes into |
|---|---|
| `agent-link-agent-forge-1.20.1-0.2.0-alpha.jar` | `<server>/mods/` |
| `SHA256SUMS.txt` | Verify the jar |
