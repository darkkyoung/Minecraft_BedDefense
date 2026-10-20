# Changelog

## 0.2.0-alpha.4+26.3 — Welcome introduction and pale-lime setup confirm
- First join and `/beddefense` before starting now present the **AllBlocks-inspired greeting → introduction/goal/essential rules → [게임 시작]** order. Start action remains a clickable command to open the existing setup.
- **[설정 완료 · 시작]** is now a bold pale-lime primary chat action instead of the generic gray unselected control.
- No changes to setup-section spacing, Spawn Bed block highlighting, raid difficulty balance, mod launcher UI, or challenge rules; those are pending user discussion.
- Added focused unit tests for welcome ordering and primary-action style.


## 0.2.0-alpha.3+26.3 — Bed feedback and same-world replay
- The large **살아남으세요** title/sound now triggers only when a direct raid mob attack reduces the unique bed to zero HP; manual pickup, missing bed, sunrise and dimensional penalties do not use it.
- Clicking an ineligible ordinary bed emits subtle black smoke and rejects sleep/respawn.
- Iron-ingot repairs removed; emerald or diamond **1 item = 1 HP** via the existing bed right-click repair action.
- Vanilla boss-bar layout moves directly under the Spawn Bed status line when no raid counter or countdown is active; spacing dynamically reserves room during raid warnings.
- The completed challenge's /beddefense menu now offers [다시 시작] and the familiar pre-start settings/3-second countdown. Previous special ownership/HP and raid-day ledgers reset on acceptance; existing builds/ordinary inventory remain.
- Test on a backup/new throwaway world before restarting an existing 100-day run.


## 0.2.0-alpha.2+26.3 — Bed title hierarchy and AllBlocks chat transitions
- Top-center **스폰 침대** text is 150% size and bold, with green/amber/red status colors
  (neutral gray before registration). The secondary line remains small.
- Moves the boss bar down slightly to preserve spacing; raid count and warning move with it.
- Reuses AllBlocks master's clear-old-menu behavior on entering and changing setup options.
- On **설정 완료 · 시작**, clears and closes the client chat before the 3/2/1 title countdown.
- No rules/balance, profiles, launcher or installation engine changes.


## 0.1.0-alpha.5+26.3 — Clickable first-join chat start menu
- On world entry, show a Korean clickable chat menu with [게임 시작] and [규칙 보기] while waiting.
- /beddefense (menu), /beddefense start, /beddefense rules, /beddefense status are ordinary, non-operator commands.
- Persist a world-level first-start transition: no duplicate start or stopwatch reset on rejoin.
- Start HUD world-relative day at 1 and stopwatch at 00:00:00 only when clicked; hide prototype raid warning before start.
- Require challenge start before the first bed right-click can register the Spawn Bed visual marker.
- This is a start-flow prototype only: raids, HP and player failure/victory are not yet enforced.


## 0.1.0-alpha.4+26.3 — Compact vanilla bar, first bed registration
- Used the vanilla boss-bar sprites for the right-hand Bed bar at about one-quarter of previous width.
- Added a Korean `스폰 침대` label.
- Registered first right-clicked Overworld bed in per-world saved data; emits periodic green particles while loaded.
- Synchronizes basic bed-present status; red destruction and HP values remain mock-only.
- No full Spawn Bed item, HP, respawn/failure logic or raids implemented.

## 0.1.0-alpha.3+26.3 — Korean HUD v2
- Day/XYZ/world elapsed stopwatch/biome, provisional raid warning, previous custom bar.

## 0.1.0-alpha.2+26.3 — Initial HUD
- English HUD and time sync.

## 0.1.0-alpha.1+26.3 — Initial tech prototype
- Fabric CI and cobblestone crack animation.
