# mc-agent-link-agent v0.1.0-alpha

> First alpha release of the optional in-game `/agent` command addon for `mc-agent-link`.

This addon moves the player-facing `/agent` command out of the base mod and into a separate optional Forge mod.

## Features

- `/agent <request>` queues an OP-created request in the base mod's `AgentRequestBuffer`.
- `/agent status` shows recent requests and recent agent activity.
- `/agent cancel <id>` cancels a pending request.
- Player-facing messages are server-side bilingual:
  - Chinese for `zh_*` clients.
  - English for other clients.
  - Defaults to Chinese when the server cannot detect the client language.

## Requirements

- Minecraft Forge `1.20.1`
- Java `17`
- `mc-agent-link` base mod `0.1.6-alpha` or newer

## Install

Put both jars in the server `mods/` directory:

```text
agent-link-forge-1.20.1-0.1.6-alpha.jar
agent-link-agent-forge-1.20.1-0.1.0-alpha.jar
```

Without this addon, the base mod still exposes MCP tools and the request queue API, but it does not register the in-game `/agent` command.

## Artifact

| File | Goes into |
|---|---|
| `agent-link-agent-forge-1.20.1-0.1.0-alpha.jar` | `<server>/mods/` |
| `SHA256SUMS.txt` | Verify the jar |
