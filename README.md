# Bed Defense

**Status: Minecraft Java 26.3 Korean HUD v2 technical prototype, NOT a playable 100-day challenge yet.**

## Current build v0.1.0-alpha.3+26.3

- Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3.
- Korean box-free HUD: pale-orange n일차; white XYZ, elapsed 플레이 시간, localized 생물 군계.
- Upper-center 레이드까지 M:SS초... is visible only in the last minute before approximate vanilla bedtime. Raid spawning is not implemented.
- Upper-right: ONLY a custom bar. Default unavailable bed is full yellow; mock installed green (86%), mock unplaced full yellow and destroyed full red.
- World elapsed game ticks are synchronized separately from the Overworld day clock. Stopwatch is world elapsed time until formal challenge-start and completion mechanics exist.
- /bdhud toggles the display.
- /bdhud preview cycles installed -> unplaced -> destroyed -> off, without modifying any game state.
- /beddefense debug cracks remains a four-second cobblestone crack test.

**Not implemented:** real Spawn Bed, HP, raids, monsters' siege AI, Ender Dragon victory, challenge failure, persistent personalized options.

## Download and test

1. Open https://github.com/darkkyoung/Minecraft_BedDefense/actions and choose the newest SUCCESSFUL Bed Defense 26.3 Build.
2. Download its bed-defense-26.3-test-jar artifact, unzip, and move the new beddefense JAR into the 26.3 Fabric mods folder.
3. Remove older Bed Defense alpha JARs; install only the Minecraft 26.3 Fabric API for this profile. Keep 26.2 mods in a separate game directory.
4. Launch in a disposable world. Check text, fonts, white labels, orange date, and yellow full bar.
5. Execute /bdhud preview 4 times to inspect three demo bar states and exit.
6. To test the time warning, use /time set 11500 with cheats and wait for the 60-second window before sleep becomes possible.
7. Optional: run /beddefense debug cracks on cobblestone in a disposable world.

GitHub Actions compile success is not proof of in-game visual acceptance; please report a screenshot or crash log.

## Documentation

- docs/HUD.md
- docs/TESTING.md
- docs/GAME_DESIGN.md
- docs/VERSIONING.md

Java 25 + Gradle 9.7.1: gradle build.
