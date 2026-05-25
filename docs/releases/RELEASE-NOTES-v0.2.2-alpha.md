# mc-agent-link-agent v0.2.2-alpha

> Patch release for Claude session handling and easier error reporting.

## Fixes

- Fixed first-run Claude session handling.
- The addon no longer invents a random `session_id` and then passes it to `claude --resume`.
- When `session_id` is blank, the addon starts a new Claude conversation and writes the real returned `session_id` back to `config/agent-link-agent.toml`.
- If an old or invalid `session_id` produces `No conversation found with session ID`, the addon clears it and retries once with a fresh conversation.

## Usability

- On Claude failures, the player now receives an extra clickable chat message:

```text
[Agent] Click to copy error details
```

Clicking it copies request id, player, session id, error, stderr, and stdout so it can be pasted to an agent for debugging.

## Compatibility

- Requires Minecraft Forge `1.20.1`.
- Requires Java `17`.
- Requires `mc-agent-link` base mod `0.1.6-alpha` or newer.

## Artifact

| File | Goes into |
|---|---|
| `agent-link-agent-forge-1.20.1-0.2.2-alpha.jar` | `<server>/mods/` |
| `SHA256SUMS.txt` | Verify the jar |
