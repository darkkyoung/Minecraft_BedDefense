# Bed Defense

**Status: 26.3 technical prototype (NOT a playable 100-day challenge yet).**

Bed Defense is a planned single-player-first Minecraft challenge: defend a unique Spawn Bed through nightly raids and defeat the Ender Dragon before the Day 100 limit.

## Current technical prototype

- Minecraft Java **26.3**, **Fabric** Loader and Fabric API.
- A **debug-only** in-game crack overlay demonstration built with Minecraft's native 0–9 block-breaking stages.
- `/beddefense` — see the prototype status.
- `/beddefense debug cracks` — look at a **cobblestone block** within 6 blocks and run a 4-second native crack animation. The cobblestone is then broken with normal drops.
- GitHub Actions builds a test `.jar` when the Gradle build succeeds.

**Not implemented yet:** Spawn Bed registration/HP/respawning, day scheduler, AI pathfinding, mob block placement/breaking, raids, dimension penalties, permanent failure, Ender Dragon win condition, multiplayer revival. This crack test is a visual proof of concept, **not proof that mobs can currently break obstacles**.

## Test the prototype

1. Open [Actions](https://github.com/darkkyoung/Minecraft_BedDefense/actions) and select the latest **successful** `Bed Defense 26.3 Build` run.
2. Under **Artifacts**, download `bed-defense-26.3-test-jar`; unzip it and find `beddefense-...jar`.
3. Install Fabric Loader for Minecraft Java 26.3 and the matching Fabric API, then put both the Fabric API JAR and the Bed Defense JAR in your Minecraft `mods` folder.
4. Start **a disposable test world**. Place a cobblestone block, look directly at it from within 6 blocks and run `/beddefense debug cracks` (enable commands/cheats).
5. Confirm progressive vanilla crack textures appear over 4 seconds, then that the block breaks and drops an item. This test deliberately changes the world.

Actions only lists downloadable artifacts **after a successful workflow run**. A green check indicates the code built, not that the full game has passed hands-on testing.

## Design and next steps

- Canonical rules: [docs/GAME_DESIGN.md](docs/GAME_DESIGN.md)
- Test scope: [docs/TESTING.md](docs/TESTING.md)
- Version policy: [docs/VERSIONING.md](docs/VERSIONING.md)

Version-specific builds use separate branches when required. The initial `main` branch targets Minecraft 26.3.

## Build locally

Install Java 25 and Gradle 9.7.1, then run `gradle build`. The shortest JAR in `build/libs` (not the sources JAR) is the installable mod. Fabric's official 26.3 template is the build configuration reference.
