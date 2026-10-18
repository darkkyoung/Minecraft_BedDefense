# Contributor / AI agent guidance

1. Canonical GitHub repository: https://github.com/darkkyoung/Minecraft_BedDefense
2. Check current main and docs/GAME_DESIGN.md before edits. Never assume a design-only mechanic is implemented.
3. Current release target: Minecraft Java 26.3 / Fabric / Java 25. Preserve version-specific branch policy.
4. The unique Spawn Bed and its HP/respawn semantics are the core design contract. No "ordinary bed replaces Spawn Bed" shortcuts.
5. Keep "bed HP damage" separate from "vanilla block crack overlay" for obstacle blocks.
6. Siege AI must respect block filters, cooldowns, and bounded per-tick costs.
7. Persist challenge state server-side; verify atomic transitions for last-respawn edge cases before calling a feature complete.
8. CI compile is necessary but does not replace in-game survival testing. Describe shipped features accurately.
9. Do not add unrestricted debug/world modification commands to release builds.
