# Testing

Java 25 / Gradle 9.7.1: `gradle build`.

- `src/test`: Fabric Loader JUnit tests for roster progression/cap, exact first wave, Speed III thresholds/exclusions, target distances, token replay/ownership, HP permanence/repair, old schema migration, persisted final respawn/elimination, day 101 boundary/frozen timer, and mixed-dimension/unloaded/queued sunrise ledger.
- `src/gametest`: Minecraft 26.3 server tests in generated worlds, real player connection in an embedded channel, setup/countdown, saved-data reload, bed interaction/respawn/sleep mixins, pickup/reinstall/replay, no-bed first raid, actual Speed III, per-mob target switch, dawn discard/penalty, Nether persistence, real death and respawn events, final respawn/defeat, dragon death victory, actual timed dirt mining and material durations.
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
