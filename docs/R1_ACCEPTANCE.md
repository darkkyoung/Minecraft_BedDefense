# Phase R1 — PC acceptance

Target: Minecraft Java 26.3 / Fabric / Java 25. Run in a **new disposable world**. Automated tests generate separate worlds and never access the user's saves/mods/config.

## Install and start

1. Close Minecraft. In your existing Bed Defense Launcher select the existing Fabric 26.3 profile, check updates, then install the mod update. The R1 change does not require downloading another EXE.
2. Open the same profile and create a new survival world. Enable cheats only for the debug checks below.
3. On entry, `[게임 시작]` is above plain rules. Select normal, reset morning, ×2, confirm. Observe 3/2/1 and sound, then day 1 and running stopwatch. Close/reopen before confirming once to check retained choices.
4. Exit/rejoin after start: day, stopwatch, settings, HP and ownership must persist. `/beddefense` reopens the menu. No `/bdhud` command should exist.

## Bed and survival

1. Place two beds. Right-click the first during daylight: green particles, green bar fills, real respawn point. The second must refuse sleep/respawn.
2. `/beddefense debug hp 63`. Break your registered bed: yellow bar, `살아남으세요`, warning sound and hardcore heart style. Reinstall the issued “스폰 침대” item in the Overworld: HP remains 63 and normal hearts return.
3. Carry a normal bed too: it must not replace the carried Spawn Bed. In a creative test, copying the portable stack and placing one must invalidate the duplicate. Nether/End placement must fail.
4. Hold iron ingots and right-click the installed bed: +10 HP per ingot up to 100; no consumption at full HP.
5. `/beddefense debug hp 0`: white flash, red full bar, physical bed removed, no replacement. In a separate run test an explosion/removal of either half and immediate ordinary-bed replacement: permanent destruction must remain.
6. Separate disposable runs: die unregistered, carrying, or destroyed → defeat/spectator; die with placed bed in Overworld → normal respawn.
7. Set HP 20, enter Nether/End, die: one final Overworld respawn, destroyed bed. Next death → defeat. Rejoin between death and respawn to check allowance does not increase.

## Raids and siege

- `/beddefense debug state` shows bed state, HP, player death state and remaining raid count.
- `/beddefense debug raid` sets night and launches the current day's raid; it does not duplicate an already-accounted day. With no bed, first day must still have five zombies.
- With ×2, use `debug day 2` then `debug raid`; roster must be 3. Day 3 uses roster 5. Use new days after each dawn rather than asking for a duplicate wave.
- On multiplier ×1, test roster 41/51. From 51 all eligible zombies/skeletons/spiders/pillagers/vindicators/wither skeletons have Speed III. Creepers/witches/blazes/ravagers do not.
- Stand alternately nearer/farther from raiders than the installed bed. Different mobs can choose different targets and switch. Put dirt/planks/cobble/obsidian in their path: first-band durations 4/9.6/24/96 s, native cracks 0–9, cancellation clears cracks. Test gaps and a raised target for bridge/tower placement with 1–2 s cooldown.
- Bed hits reduce server HP, animate the bar, and show `스폰 침대 공격 받는 중!` above hotbar. No overlap with top-center remaining count.
- `/time of minecraft:overworld set 17000`: remaining raiders glow. `/time of minecraft:overworld set 23000`: Overworld survivors disappear immediately and HP settlement is fixed. Repeat with an unloaded survivor and a cave survivor. Nether/End survivors remain into next night's wave; queued troops must stay bounded and resume when present.

## End and visual acceptance

- Kill the Ender Dragon during a run: success/result, frozen timer. `/beddefense debug day 101`: deadline defeat, frozen timer. Day 100 itself must not fail.
- Check 1080p/1440p and Minecraft GUI scales 2/3/4, Korean labels, normal/damaged/carried/destroyed bar, hardcore hearts, particles, countdown/warning audio, left HUD and actionbar with Sodium/Iris/AllBlocks only where those mods actually declare 26.3 compatibility.

## Evidence boundaries

CI executes compiler, unit tests and real server GameTests; the release pipeline additionally executes Windows launcher file-preservation/recovery, real bundle validation, WPF single-EXE smoke, public updater download/checksum checks.

**Physical Acceptance Pending:** human client graphics/audio, live combat/pathfinding feel, multiplayer latency, modpack interactions and a natural full 100-day playthrough. Headless server tests cannot certify these. The exact external 100-day v2 type sheet was not present; existing PR counts/type distribution are preserved with explicitly documented captain/buff additions.
