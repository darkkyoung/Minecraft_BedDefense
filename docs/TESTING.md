# Testing

Java 25 / Gradle 9.7.1: `gradle build`.

- `src/test`: Fabric Loader JUnit tests for roster progression/cap, exact first wave, Speed III thresholds/exclusions, target distances, token replay/ownership, HP permanence/repair, old schema migration, persisted final respawn/elimination, day 101 boundary/frozen timer, and mixed-dimension/unloaded/queued sunrise ledger.
- `src/gametest`: Minecraft 26.3 server tests in generated worlds, real player connection in an embedded channel, setup/countdown, saved-data reload, bed interaction/respawn/sleep mixins, pickup/reinstall/replay, no-bed first **Easy** raid (five mobs), actual Speed III, per-mob target switch, dawn discard/penalty, Nether persistence, real death and respawn events, final respawn/defeat, dragon death victory, actual timed dirt mining and material durations.
- `gradle runClientGameTest`: actual client/server networking and HUD smoke under Xvfb on CI, with screenshots for unregistered/placed/damaged/carried/destroyed/victory states and vanilla boss-bar spacing. This is not human audio/combat acceptance.
- Test mod/source sets are excluded from the release JAR. No test reads an existing Minecraft installation or user world.
- Existing Windows launcher safety/recovery, existing-profile mod/world/config preservation, bundle validation, WPF executable smoke and public release checks remain in `.github/workflows/launcher.yml`.
- Gameplay tests gate both build CI and the release's `mod-package` job. Failed gameplay tests prevent publishing.
- Local sandbox can compile the official 26.3 API directly; its Unix-domain-socket restriction prevents unmodified Loom initialization. Full Gradle/game-server execution is performed on GitHub Actions. This environmental limit is not treated as a successful test.

Manual acceptance: [R1_ACCEPTANCE.md](R1_ACCEPTANCE.md). Test success is not a claim of human physical acceptance.

## Alpha.7 hard mode playtest

- `ChallengeRulesTest` validates the 100-day hard roster, 150 cap, real allocated totals, day-10 baby/SPEED III eligibility, deterministic status rolls, profile persistence/migration and hard-mode sunrise non-spawn penalty exclusion.
- `PlayableChallengeGameTest.hardRaidStage10BabySpeedAndMining` checks actual spawned baby zombie hitbox, ordinary zombie Speed III and different first-band mining durations for easy/hard.
- Existing challenge GameTest uses the legacy normal profile; hard runs must not retroactively change earlier persistent challenges.
- [Hard mode PC acceptance](HARD_MODE_ACCEPTANCE.md) describes the new-run and launcher-update acceptance procedure. Physical 100-day acceptance remains pending.

## Alpha.8 variable hard-raid balance
- JUnit checks every day 1–100: random range bounds, representative user-requested 7–12 / 10–17 / 31–58 / 49–90 / 88–125 limits, deterministic composition at min/middle/max, exactly one tenth-day boss, correctly scaled baby zombies, and no accidental retroactive alpha.8 migration of alpha.7 hard saves.
- Saved-data roundtrip checks one random wave count shared across multiplayer participant batches, non-reroll after duplicate launch and preservation of pending/index counts during spawn retries.
- Minecraft/Fabric GameTest compares new easy, old alpha.7 hard and new alpha.8 hard block-mining times; existing tests preserve no-bed raid, spawn-bed HP, owner tokens, death/respawn and sunrise ledger.
- Actual nighttime randomization and difficulty feel still require disposable-world PC play. See [hard mode acceptance](HARD_MODE_ACCEPTANCE.md).

## Alpha.9 clean-start regression
- Embedded-player Minecraft server GameTest verifies `[설정 완료 · 시작]` immediately enters Adventure, remains Adventure through countdown while inventory/offhand/cursor retain pre-start items, refuses reentrant setup, and finally enters Survival with all those items empty.
- No game-mode or inventory-clear commands are issued: transitions use `ServerPlayer.setGameMode`, `Inventory.clearContent` and direct container cursor clearing, which do not emit vanilla command success feedback into chat.
- Interrupted-countdown restoration, completed-run spectator restart and multiplayer conversions still require explicit human acceptance on a disposable save. CI is not a claim of full PC/gameplay acceptance.

## Alpha.10 full-height first welcome
- JUnit: first welcome packet's explicit `welcome` flag, fallback constructors, viewport chat-height math and vanilla-height restoration without persistent option changes.
- Fabric client GameTest: send first welcome, assert it temporarily exposes >=18 lines at GUI scale 2 and take `r1-welcome-chat-full-height` screenshot; then send `/beddefense setup` and confirm client presentation flag restores normal before continuing existing HUD acceptance. Mixin target `ChatComponent.getHeight()I` must pass client initialization.
- Physical testing: confirm all Korean introduction lines and clickable **[게임 시작]** on desktop and smaller GUI scale/monitor configurations. CI does not prove all visual layout variants.

## Alpha.11 raid water-conversion identity regression
- Actual Minecraft server GameTest constructs three closed water cells, tracks a real Zombie and Husk in `RaidStateData`, leaves another Zombie untracked, and advances the vanilla underwater counters. **Both raiders retain original UUID/entity and do not enter a conversion countdown**; a reflection call verifies that the wild control's vanilla `convertsInWater()` predicate remains enabled. The control's full 45-second physical conversion is a separate manual test.
- Confirm no lost raid ledger records, no false `레이드 클리어!`, no change to actual Drowned mobs in the hard roster, and no special chat output. Vanilla unrelated zombies must remain convertible.
- On PC, leave the tagged raid Zombie/Husk underwater at least 45 seconds, or until sunrise, and verify the remaining count tracks them; stop the raid only on real kills or sunrise settlement. Keep test worlds disposable. Code CI is not full water AI/pathfinding acceptance.

## Alpha.12 Normal V1 independent profile tests
- JUnit exhaustively checks day 1–100 minimum/maximum/intermediate rolls, the 11 agreed example bands, caps, a one-slot 10th-day captain, day-20 baby zombie limit, eligible day-25 Speed III, full eligible Speed III from day 51, and no Speed III stacked on babies/creepers.
- Saved-data codec roundtrip verifies Normal V1 profile is selected only for newly started runs; existing `PROFILE_LEGACY` Normal remains unchanged. A nightly random total is shared across participant queues, is retained after partial spawning/save reload, and cannot be rerolled on duplicate day launch.
- Minecraft 26.3 actual GameTest instantiates a medium day-20 baby, a day-25 Speed III zombie, and measures first-band mining times of 1.5/3.4/8.5/34s with old-Normal 96s obsidian preserved.
- In a disposable PC world, verify Normal V1 first 3 nights, day 10/20/25/51, the midgame block-placement pursuit and sunrise settlement; actual 100-day human acceptance is pending.
