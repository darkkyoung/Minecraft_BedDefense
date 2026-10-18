# Bed Defense

**Minecraft Java 26.3 Fabric technical prototype. This is not a playable 100-day challenge yet.**

## v0.1.0-alpha.4+26.3

- Existing Korean HUD: day, XYZ, elapsed stopwatch and biome, without rectangular backgrounds.
- Upper right: **스폰 침대** label over a short **vanilla boss-bar texture** (approximately 1/4 former bar width).
- First Overworld bed right-click registers the player's first Spawn Bed coordinates even during daytime.
- A registered bed shows small green particles whenever the bed chunk is loaded, including after a world restart.
- **Yellow full bar:** no bed registered or known absent. **Green full:** a bed registered and presumed placed. **Red full:** preview only (actual destruction is not implemented).
- `/bdhud preview` cycles 86%-green, yellow, red and normal, without changing gameplay.
- `/bdhud` toggles HUD; `/beddefense debug cracks` remains a safe cobblestone visual test.
- Raid-warning is visual-only and does not spawn monsters.

**Not implemented:** real Spawn Bed block/item, HP/damage, pickup-and-relocation, respawn/permadeath rules, real raids, siege AI, dragon victory, multiplayer revival.

## Install / test

1. Visit https://github.com/darkkyoung/Minecraft_BedDefense/actions and choose the latest successful build for alpha.4.
2. Download the `bed-defense-26.3-test-jar` artifact, unzip, move the `beddefense-...jar` into the Minecraft Java 26.3 Fabric `mods` folder; remove older Bed Defense alpha JARs.
3. Keep only compatible Minecraft 26.3 Fabric API and 26.3-compatible optional mods.
4. In a disposable world, observe compact yellow bar at top right and `스폰 침대` label.
5. Place a regular bed and right-click it (even in daytime). Check registration message, green particles and full green bar.
6. Exit/rejoin world: registration should persist. Check preview with `/bdhud preview` four times.

Build success is not in-game acceptance; please send screenshot and errors if issues arise.

See [HUD testing details](docs/HUD.md), [game design](docs/GAME_DESIGN.md), [testing](docs/TESTING.md).
