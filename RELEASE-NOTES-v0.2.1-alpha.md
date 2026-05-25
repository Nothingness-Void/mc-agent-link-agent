# mc-agent-link-agent v0.2.1-alpha

> Patch release for easier Claude bridge setup.

## Highlights

- Added `/agent reload` for OPs.
- Reloads `config/agent-link-agent.toml` while the server is running.
- Stops the old Claude worker before applying the new config.
- Starts the Claude worker when `[claude].enable = true` and Claude is available.
- Stops/leaves disabled when `[claude].enable = false`.
- Reports clear in-game status messages for started, disabled, not found, or failed reload states.

## Why this matters

Agents or server operators can now do the full simple setup without restarting the Minecraft server:

```text
1. Install both jars.
2. Start the server once to generate config/agent-link-agent.toml.
3. Edit [claude].enable = true and optionally claude_executable.
4. Run /agent reload in game as an OP.
5. Run /agent <request>.
```

## Compatibility

- Requires Minecraft Forge `1.20.1`.
- Requires Java `17`.
- Requires `mc-agent-link` base mod `0.1.6-alpha` or newer.
- Keeps the default `enable = false` behavior compatible with `0.1.x` and `0.2.0-alpha`.

## Artifact

| File | Goes into |
|---|---|
| `agent-link-agent-forge-1.20.1-0.2.1-alpha.jar` | `<server>/mods/` |
| `SHA256SUMS.txt` | Verify the jar |
