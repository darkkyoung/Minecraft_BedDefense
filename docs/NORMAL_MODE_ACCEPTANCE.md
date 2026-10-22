# Normal V1 difficulty — alpha.12+26.3

The playable **보통** profile is enabled for **newly started** challenges only. Existing ongoing Normal worlds retain old roster/mining. Manual acceptance is separate from successful automated tests.

## Target numbers per player/night

| Effective roster day | Normal V1 mobs | Easy fixed | Hard V2 random |
|---|---:|---:|---:|
| 1 | 6–8 | 5 | 7–12 |
| 2 | 9–11 | 6 | 10–17 |
| 3 | 11–13 | 6 | 12–22 |
| 5 | 14–18 | 8 | 17–32 |
| 10 | 25–31 | 12 | 31–58 |
| 20 | 34–42 | 18 | 42–75 |
| 30 | 41–51 | 22 | 49–90 |
| 50 | 53–65 | 32 | 66–107 |
| 75 | 62–76 | 40 | 78–116 |
| 90 | 75–91 | 62 | 85–121 |
| 100 | 84–102 | 80 | 88–125 |

Intermediate days use integer linear interpolation, inclusive daily uniform draws. Once drawn, the saved total is fixed for that night and all participant batches. Multipliers affect effective roster day, not the number of nights. One captain occupies a slot on each 10th effective roster day.

## Composition and buffs

Normal gradually introduces husks/spiders/skeletons, drowned/pillagers from day10, up to three separate **baby zombies from day20**, cave spiders/creepers/strays, then late witches/blazes/wither skeletons and gear. Eligible Speed III begins selectively day25 and is guaranteed after day51 to match Easy's vanilla roster speed floor; the established Hard V2 buff percentages have not been changed. Normal Fire Resistance: 10% day1, 35% day10, 75% day50, 95% day90–100. Other staged weapons/armor are less aggressive than Hard.

## Initial siege, seconds

| Block | Easy | Normal V1 | Hard V2 | Old Normal |
|---|---:|---:|---:|---:|
| Dirt | 2 | 1.5 | 1 | 4 |
| Planks | 4.8 | 3.4 | 2 | 9.6 |
| Cobblestone | 12 | 8.5 | 5 | 24 |
| Obsidian | 48 | 34 | 20 | 96 |

Normal V1 checks stalled movement after ~1.5 seconds; work ticks every 5 server ticks and bridge/tower cooldown is 18–30 ticks. Pathing can add delay to the observed interaction. Global mine/placement and active mob budgets unchanged. Morning discards unspawned Normal V1 Overworld queues without bed penalty; only spawned surviving raiders charge HP1 each.

## Installation/PC observations

- On Windows 11 x64, close Minecraft and the official launcher, then open existing Bed Defense Launcher → 업데이트 조회 → 업데이트 설치. Confirm Minecraft Java 26.3 Fabric profile; existing Loader/API, mods, saves, configuration and other launcher profiles must remain unchanged.
- Test newly started Normal on a disposable world (first-day morning, multiplier ×1). Use screenshot/capture to note daily total, mix, stage buffs, blocks destroyed, bed HP lost, and any false raid-complete or water-navigation behavior.
- Try `/beddefense` while challenge is active. It must *not* offer new difficulty; changes require finishing the previous challenge and starting a fresh one. An old Normal world opened after update must keep its previous profile, not silently switch.
- Actual player experience, 100-day balance, water AI and complex tower navigation remain **Physical Acceptance Pending**.
