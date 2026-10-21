# Hard raid playtest — 0.2.0-alpha.7+26.3

**Not physical acceptance.** Alpha playtest changes the difficultly-defined raid profile for **newly started** challenges only.
Target Minecraft Java 26.3, Fabric Loader >=0.19.5, Fabric API 0.163.0+26.3, Java 25.

## Installation (existing launcher)
1. Close Minecraft and the official launcher, then open your existing Bed Defense Launcher (Windows 11 x64).
2. Confirm the existing Fabric 26.3 profile and actual game directory. Click **업데이트 조회 → 업데이트 설치**. It should replace **only** the Bed Defense mod JAR, retaining the official launcher profile, world, other mods, Fabric API and settings.
3. Test in a **new disposable survival world**; preserve/backup all valuable saves. In the clickable setup choose **어려움**, first morning, roster stride **×1**, confirm and allow the 3-second countdown. The hard profile persists for this run.
4. Pre-update worlds already running keep legacy raiding regardless of previously selected difficulty; starting a fresh run after a completed challenge enables the selected new profile.

## Hard raid timing, count and elites
- Preview targets: roster 1: 12 mobs; 10: 58; 50: 107; 100: 125 **per participant**. Configured roster safety cap 150. Active dimension cap remains 128; the rest are queued and should only spawn when room opens.
- At day 1: zombies and husks with some fire resistance and Speed I. By day 5 spiders, drowned and skeletons appear. Day 10 gains 2+ baby zombies, eligible Speed III normal raiders, and one vindicator captain.
- At roster day 30: evoker captain, witches/strays and upgraded gear. Day 60/100: ravager captain, wither skeletons and increasing armor/damage modifiers.
- Day 10 ordinary zombies can have Speed III; baby zombies have natural baby speed and **do not** additionally get a Speed III potion. Special mobs like creepers, witches, blazes and ravagers are excluded from general Speed III.
- Fire-resistance rises 35% day 1 → 70% day 10 → 100% by roster 50; equipment tiers include leather/chainmail/iron and limited diamond.
- Underwater attribute plus Dolphin's Grace is only an experimental assist. A potion/attribute alone does **not** prove hostile mobs path well through water: verify water navigation separately.

## Obstacle siege
- Easy (new runs) has legacy roster, half the legacy mining time: dirt **2s**, planks **4.8s**, cobble **12s**, obsidian **48s** on first band.
- Legacy normal and pre-update saves retain original times: 4s, 9.6s, 24s, 96s.
- Hard first band ~0.5s dirt, 1s planks, 2.5s cobble, 10s obsidian; hard bridge/tower cooldown 5–10 ticks (0.25–0.5s), hard siege job advances every 5 ticks. Respect the global 16 placements/second and 12 simultaneous miners.
- Try towering up three blocks, using lava buckets, a water trench, wood/cobblestone/obsidian walls, alternating bed/player proximity; verify that pathfinding decisions and block mining are active. Complex vertical or water navigation is not yet human-accepted.

## Sunrise / integrity
- Unkilled **spawned** Overworld raiders receive dawn HP1 penalty per surviving mob. In hard mode **unspawned queued** Overworld mobs are discarded at dawn without a penalty. Nether/End mob tracking and queues stay bounded and persistent.
- A Spawn Bed retains its single-owner identity, 100 HP, one dropped tagged item on manual pickup and no duplicate; a raid kill at HP0 is permanent. Successful Ender Dragon death and day-101 failure remain unchanged.
- Check day 10, 50 and 100 in independent expendable runs. Avoid jumping the world clock while a wave is unresolved; use /beddefense debug state, day and raid in a cheats-enabled test world.
- Record spawn count, bed HP loss, vertical/water navigation behavior, enemy equipment, drops, frame rate/server TPS. Speed III is not guaranteed to exactly match player sprint speed (sprint changes terrain/AI dynamics).

## Evidence boundary
CI unit tests and Fabric GameTests validate data invariants, saves, potion attachment and representative actual mob entities. The Windows publishing pipeline validates the installer. A real player should still assess combat feel, lava/water/tower tactics, graphic/audio details, performance at 125 mobs, and an uninterrupted 100-day playthrough.
