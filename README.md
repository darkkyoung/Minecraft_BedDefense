# Bed Defense

**Status: Minecraft Java 26.3 UI + crack-effect technical prototype. NOT yet a playable 100-day challenge.**

Bed Defense is a planned single-player-first Minecraft challenge: defend a unique Spawn Bed during nightly raids and defeat the Ender Dragon before the Day 100 limit.

## What's in this build

- Java 26.3 / Fabric Loader 0.19.5 / Fabric API 0.163.0+26.3, Java 25.
- **Frameless Minecraft HUD** (no backgrounds, panels, or boxes):
  - **Upper left:** DAY /100, X/Y/Z coordinates, countdown to vanilla nighttime, biome and dimension.
  - **Upper right:** prominent Spawn Bed HP placeholder and small installation status.
  - Live global Overworld clock synchronized from the server across dimensions.
- Client commands:
  - `/bdhud` — show/hide HUD.
  - `/bdhud preview` — toggle a **clearly labeled visual preview** of raid indicators + a demo `BED 86/100` number. **No actual raid, HP or sleep handling exists yet.**
- Existing debug command: `/beddefense debug cracks` — look at a cobblestone block within 6 blocks to show 4 seconds of native Minecraft block cracking stages 0–9; the cobblestone then breaks and drops.
- GitHub Actions produces a test mod JAR on a successful build.

**Still unimplemented:** real Spawn Bed registration/HP/respawning, raids, monster siege AI, true raid timer, sunrise penalties, dimension-death mechanics, permanent defeat, Ender Dragon victory detection, multiplayer revival, personalized settings.

## Install and test

1. Open [Actions](https://github.com/darkkyoung/Minecraft_BedDefense/actions) and select the latest **successful** `Bed Defense 26.3 Build`.
2. Under **Artifacts**, download `bed-defense-26.3-test-jar`, unzip, and take the `beddefense-...jar` (not a sources JAR).
3. In a **separate Minecraft Java 26.3 Fabric game directory/profile**, install the 26.3 Fabric API and Bed Defense JAR in `mods/`. **Do not mix with 26.2 mods** such as Sodium, Iris, Fabric API or AllBlocks for 26.2.
4. Start a disposable test world. The live HUD should appear in the upper corners. The bed line is **BED -- / --** because the underlying game feature isn't implemented.
5. Run `/bdhud preview` to inspect the raid display and sample Spawn Bed HP. Run it again to remove preview. `/bdhud` toggles all Bed Defense HUD text. F1 hides vanilla and mod HUD.
6. Walk/teleport around to test coordinates/biome; optionally compare day/countdown after Nether/End portal travel.
7. Optionally test `/beddefense debug cracks` on cobblestone in a throwaway world with commands enabled.

Build success is not proof of in-game visual appearance or gameplay correctness; collect an in-game screenshot and logs to validate.

## Documents

- Game design: [docs/GAME_DESIGN.md](docs/GAME_DESIGN.md)
- HUD layout and commands: [docs/HUD.md](docs/HUD.md)
- Test plan: [docs/TESTING.md](docs/TESTING.md)
- Versioning: [docs/VERSIONING.md](docs/VERSIONING.md)

## Build locally

Use Java 25 and Gradle 9.7.1, then run `gradle build`. The regular JAR in `build/libs` (not the sources JAR) is the installable mod.
