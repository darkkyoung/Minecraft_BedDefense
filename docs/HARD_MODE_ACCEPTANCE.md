# Hard raid playtest — 0.2.0-alpha.8+26.3

**Not physical acceptance.** Alpha.8 adds a new versioned hard-mode profile for **newly started** challenges only. Already active alpha.7 hard-mode runs retain their prior behavior and do not become alpha.8 hard mode automatically.
Target Minecraft Java 26.3, Fabric Loader >=0.19.5, Fabric API 0.163.0+26.3, Java 25.

## Installation (existing launcher)
1. Close Minecraft and the official launcher, then open your existing Bed Defense Launcher (Windows 11 x64).
2. Confirm the existing Fabric 26.3 profile and actual game directory. Click **업데이트 조회 → 업데이트 설치**. It should replace **only** the Bed Defense mod JAR, retaining the official launcher profile, world, other mods, Fabric API and settings.
3. Test in a **new disposable survival world**; preserve/backup all valuable saves. In the clickable setup choose **어려움**, first morning, roster stride **×1**, confirm and allow the 3-second countdown. The hard profile persists for this run.
4. Pre-update worlds already running keep legacy raiding regardless of previously selected difficulty; starting a fresh run after a completed challenge enables the selected new profile.

## Alpha.8 daily hard-roster randomization

For a **new** hard run only, the nightly roster is picked uniformly from the following inclusive ranges. Its baseline starts at 75% of alpha.7 hard, interpolates via explicit agreed example anchors, and reaches 100% of the old hard maximum by roster day 90. The **minimum is 70% of that baseline rounded up**, not 70% of the original maximum. The existing alpha.7 hard total stays the upper bound.

| Effective roster day | Baseline recommendation | Random total (min–max) |
| --- | ---: | ---: |
| 1 | 9 | 7–12 |
| 2 | 13 | 10–17 |
| 5 | 24 | 17–32 |
| 10 | 44 | 31–58 |
| 20 | 59 | 42–75 |
| 30 | 70 | 49–90 |
| 50 | 94 | 66–107 |
| 75 | 111 | 78–116 |
| 90 | 121 | 85–121 |
| 100 | 125 | 88–125 |

- **One roll per night**: all participating players receive the same rolled roster count. Each player still has their own mob batch, per existing multiplayer behavior.
- The server stores the rolled wave day and number in the saved raid ledger. Exit/rejoin and pending-queue retries must NOT change it. `/beddefense debug raid` must not bypass per-day deduplication.
- The 10th-day boss remains **exactly one** per participating player and is included in the randomly selected total; baby-zombie and other mob slots are scaled to fit the draw.
- A higher daily roster multiplier advances the *effective roster day* for BOTH the range and monster composition; it does not roll more than one wave per calendar day.
- Active per-dimension raider cap and sunrise penalties are unchanged; hard mode does not penalize raiders left queued and never spawned.

## Hard raid timing, count and elites
- The original hard total (12/58/107/125) is now the **maximum**, not a guaranteed wave size, **per participant**. Configured roster safety cap 150. Active dimension cap remains 128; the rest are queued and should only spawn when room opens.
- At day 1: zombies and husks with some fire resistance and Speed I. By day 5 spiders, drowned and skeletons appear. Day 10 gains 2+ baby zombies, eligible Speed III normal raiders, and one vindicator captain.
- At roster day 30: evoker captain, witches/strays and upgraded gear. Day 60/100: ravager captain, wither skeletons and increasing armor/damage modifiers.
- Day 10 ordinary zombies can have Speed III; baby zombies have natural baby speed and **do not** additionally get a Speed III potion. Special mobs like creepers, witches, blazes and ravagers are excluded from general Speed III.
- Fire-resistance rises 35% day 1 → 70% day 10 → 100% by roster 50; equipment tiers include leather/chainmail/iron and limited diamond.
- Underwater attribute plus Dolphin's Grace is only an experimental assist. A potion/attribute alone does **not** prove hostile mobs path well through water: verify water navigation separately.

## Obstacle siege
- Easy (new runs) has legacy roster, half the legacy mining time: dirt **2s**, planks **4.8s**, cobble **12s**, obsidian **48s** on first band.
- Legacy normal and pre-update saves retain original times: 4s, 9.6s, 24s, 96s.
- **New hard alpha.8** first band ~1s dirt, 2s planks, 5s cobble, 20s obsidian; bridge/tower cooldown 15–20 ticks (0.75–1s), obstacle stall begins after ~1s, hard siege job advances every 5 ticks. **Active alpha.7 hard runs** retain their old 0.5/1/2.5/10s and 5–10 tick behavior. Respect the global 16 placements/second and 12 simultaneous miners.
- Try towering up three blocks, using lava buckets, a water trench, wood/cobblestone/obsidian walls, alternating bed/player proximity; verify that pathfinding decisions and block mining are active. Complex vertical or water navigation is not yet human-accepted.

## Sunrise / integrity
- Unkilled **spawned** Overworld raiders receive dawn HP1 penalty per surviving mob. In hard mode **unspawned queued** Overworld mobs are discarded at dawn without a penalty. Nether/End mob tracking and queues stay bounded and persistent.
- A Spawn Bed retains its single-owner identity, 100 HP, one dropped tagged item on manual pickup and no duplicate; a raid kill at HP0 is permanent. Successful Ender Dragon death and day-101 failure remain unchanged.
- Check day 10, 50 and 100 in independent expendable runs. Avoid jumping the world clock while a wave is unresolved; use /beddefense debug state, day and raid in a cheats-enabled test world.
- Record the exact nightly random roll, spawn count, bed HP loss, vertical/water navigation behavior, enemy equipment, drops, frame rate/server TPS. Exit/rejoin after the draw on a disposable world and confirm the count does not change. Speed III is not guaranteed to exactly match player sprint speed (sprint changes terrain/AI dynamics).

## Evidence boundary
CI unit tests and Fabric GameTests validate data invariants, saves, potion attachment and representative actual mob entities. The Windows publishing pipeline validates the installer. A real player should still assess combat feel, lava/water/tower tactics, graphic/audio details, performance at 125 mobs, and an uninterrupted 100-day playthrough.
