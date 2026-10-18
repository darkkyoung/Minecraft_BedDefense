# Testing and acceptance

## Current executable smoke test (only)
1. Build succeeds on Java 25 / Minecraft 26.3 Fabric.
2. Copy artifact JAR and 26.3 Fabric API into Minecraft mods.
3. Open a disposable creative or survival world with cheats on.
4. Place cobblestone (not a valuable block).
5. Look directly at it within 6 blocks; run `/beddefense debug cracks`.
6. Visually check progressive stages of vanilla cracking (0–9) for 4 seconds.
7. Confirm a single cobblestone block breaks and its normal drop appears.
8. Repeat on dirt: command must reject (restrict debug destruction scope).
9. Run twice on the same cobblestone: it must not create overlapping tasks.
10. Unload/change block during demo: cracking must clear and no unrelated block breaks.

## Not yet executable
- First-bed registration, persistence through logout/restart, pick-up/move HP transfer
- Death behavior when the bed is missing/destroyed, or Nether/End penalty to 0
- Full 100-day wave schedule and 1–3 Speed buff allocations
- Siege pathfinding, obstacle breaking by real mobs, block placement cooldown
- Sunrise clearance/HP penalties, Nether/End carry-over, queued backlog
- Win/loss state, multiplayer spectator revival

Do not promote a GitHub Actions **build** to full acceptance. Compile success is not runtime proof.
