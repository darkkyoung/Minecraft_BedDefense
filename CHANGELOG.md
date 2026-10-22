# Changelog

## 0.2.0-alpha.12+26.3 — Normal V1 intermediate raid difficulty
- **Newly started 보통 challenges** receive a separately persisted `PROFILE_NORMAL_V2=4` profile. Previous ongoing `PROFILE_LEGACY=0` Normal challenges retain their historical fixed enemy composition, count, speed and slower mining. **No difficulty changes during an active run**; restarting after a finished challenge enables the chosen new mode.
- A **single saved random wave size** is drawn each night per participant at the effective roster day, using agreed 100-day interpolated bounds: day 1 **6–8**, 2 **9–11**, 3 **11–13**, 5 **14–18**, 10 **25–31**, 20 **34–42**, 30 **41–51**, 50 **53–65**, 75 **62–76**, 90 **75–91**, 100 **84–102**. The same draw is shared across participating players and persists through reload/retry. One captain is included every tenth effective roster day.
- New staged composition gradually adds husks/spiders/skeletons, drowned/pillagers at day 10, **up to three baby zombies from day 20**, cave spiders/strays/creepers, then witches/blazes and higher-tier raiders. Normal Speed III starts at day 25 for a subset; it becomes guaranteed on all eligible mobs by day 51 so it does not regress below Easy's guaranteed day-51 rule. Babies are never double-buffed with Speed III.
- Normal Fire Resistance starts at 10% (day 1), ~35% day 10, 75% by day 50 and 95% late; Normal-specific weapons/armor, water movement and limited strength/resistance advance below Hard stages. Actual water pathfinding remains a separate physical acceptance item.
- Intermediate first-band siege: **dirt 1.5s, planks 3.4s, cobble 8.5s, obsidian 34s**, movement stall threshold about 1.5s and placement cooldown **18–30 ticks (0.9–1.5s)**. Existing Easy/Historical Normal/Hard speeds are unchanged.
- **Unspawned queued** Overworld Normal V1 raiders do not inflict sunrise bed HP penalties; spawned survivors still cost 1 HP each. Existing caps of 128 per dimension, 12 simultaneous miners and 16 blocks placed/sec remain.
- Bed rules, HP 100, gem repair HP 1, dimension death -20, 100-day deadline, difficulty's in-run persistence and Launcher updater/rollback are unaffected.
- 100-day profile range/composition/saved-roll JUnit coverage and real Minecraft Normal baby, day-25 Speed III and siege-time GameTests are included; complete real 100-day PC acceptance is still pending.

## 0.2.0-alpha.11+26.3 — Fix raid zombies disappearing on water conversion
- **Tracked raid zombies no longer transform into Drowned** after prolonged submersion. The entity retains its UUID, so the authoritative raid ledger, remaining-mob count, raid target AI and morning penalty remain correct; the raid cannot be cleared by water conversion.
- Raid **husks** are protected too. Husk overrides vanilla water conversion and would otherwise transform first into a new Zombie (and eventually into a Drowned), also dropping its raid tracking.
- Only mobs whose UUID is present in Bed Defense's server-side `RaidStateData` are affected. **Ordinary wild zombies and husks still convert normally**. Raids that spawn Drowned directly are unchanged.
- Already-registered zombies loaded while a conversion timer is active have their timer reset on server entity load. No new mob tags or saved-data schema changes are needed.
- Added actual Minecraft server GameTest that submerges tracked zombie/husk plus an untracked control zombie, advances underwater timers, confirms raiders retain original entities/UUIDs without converting, and directly verifies the wild control retains vanilla conversion eligibility. The full 45-second wild conversion remains manual acceptance.
- No changes to raid difficulty, spawn counts, buff/equipment balance, Spawn Bed HP, installer, or world-save compatibility. Full physical underwater/pathfinding acceptance still pending.

## 0.2.0-alpha.10+26.3 — Full first-join introduction chat
- The first **베드 디펜스** greeting/introduction/goal/rules/**[게임 시작]** chat temporarily expands its visible height so the earliest lines are not cut off by the vanilla unfocused chat limit.
- The larger chat height is **client-only and temporary**. The setting's vanilla unfocused/focused height is never changed or saved to `options.txt`; selecting **[게임 시작]** opens the normal-height setup menu immediately.
- Reopening `/beddefense` before starting shows the complete introduction again; setup, cancellation, countdown, other chat and post-start gameplay use the player's own native chat-height settings.
- The adaptive expanded height respects the GUI-scaled viewport, keeping a margin for the rest of the HUD even on smaller windows.
- Added a client screenshot assertion that the first welcome exposes at least 18 rows with GUI scale 2, plus unit tests for normal/menu and viewport behavior. Manual screen-scale/shader/modpack verification remains pending.
- No changes to bed HP, raids/difficulty, inventory reset sequence, Windows installer or profiles.

## 0.2.0-alpha.9+26.3 — Silent Adventure → clean Survival start
- Clicking **[설정 완료 · 시작]** switches every eligible online participant to Adventure during the existing 60-tick/3-second countdown. Current spectators who previously participated in a completed run can be included in the restart, but unrelated spectator observers stay spectators.
- **Do not clear items until the challenge start succeeds.** On success, the server clears the participant's main inventory, hotbar, armor, offhand and carried cursor, then switches them directly to Survival before the start title/sound. Ender Chest, XP, world builds and dropped items are untouched.
- Uses server-side game mode and inventory APIs **without** `/gamemode` or `/clear` commands, so no vanilla game-mode change or clear-count system-chat feedback. The intentional challenge-start greeting remains.
- Starting/cancelling the setup without confirming does not wipe items. A failed time reset, an owner disconnect, or an interrupted countdown restores the previous game modes and retains player items. A repeated setup command cannot reset an in-progress countdown.
- **Confirmed same-world restarts now clear participant inventories** at the new start (unlike alpha.8), but preserve builds/world and reinitialize the challenge bed/raid records. Launcher install/update/backup behavior is unchanged.
- Added actual Minecraft/Fabric GameTest assertions for Adventure during countdown, inventory/cursor retention before completion, and cleared Survival at successful start.

## 0.2.0-alpha.8+26.3 — Hard raids: nightly variable count and siege moderation
- New **어려움** runs draw their mob count **once per nightly raid** uniformly between `ceil(0.7 × recommended reduced count)` and the **original alpha.7 maximum** (inclusive). The reduced baseline is initially 75% of the original, then interpolates through the previously agreed representative 10/20/30/50/75/90-day values; by day 90 its maximum is again the original hard roster.
- Example ranges: day 1 **7–12**, day 2 **10–17**, day 10 **31–58**, day 30 **49–90**, day 50 **66–107**, day 75 **78–116**, day 90 **85–121**, day 100 **88–125**.
- The selected count is **stored in the server RaidStateData per wave** and shared across participants; world reload, spawn retries and /beddefense debug raid never roll again for the same day. Count-based role distribution scales, including the single tenth-day captain and baby zombies.
- New hard-profile obstruction times are **1 s dirt / 2 s wood / 5 s cobble / 20 s obsidian** for the first band; bridge/tower cooldown is **15–20 ticks (0.75–1.0 s)** and the stuck-detection threshold is about **1 s**. Existing safety budgets are unchanged.
- Easy, normal, and pre-update active hard (`PROFILE_HARD`) runs retain their previous roster/siege behavior. A new per-run `PROFILE_HARD_V2` activates this balance **only for newly started challenges**. Existing worlds, bed HP/owner, raids, saves and launcher install engine are not rewritten.
- Speed III from roster day 10, baby zombie slots, fire resistance, gear progression, and hard-mode unspawned-queue sunrise exclusion are preserved unchanged.
- This is a player-balance alpha, not completion of human Minecraft game acceptance.

## 0.2.0-alpha.7+26.3 — Experimental hard-mode raid playtest
- New challenges started with **어려움** receive a separate 100-day raid roster, climbing 12 (day 1) -> 58 (10) -> 107 (50) -> 125 (100) per participating player; 150-roster safety ceiling.
- Baby zombies enter at roster day 10 as a fast, small-target elite type without an additional Speed III potion. Eligible regular raiders begin receiving Speed III at roster day 10.
- Fire resistance, weapons, armor, strength/resistance, underwater movement efficiency and attempted Dolphin's Grace are stage-gated. Enemy gear drops are suppressed; underwater navigation still needs physical verification.
- **쉬움** retains the existing mob composition but its obstruction-breaking time is halved. **어려움** receives much faster mining and 5-10 tick block placement; **보통** retains provisional previous behavior.
- Hard-mode dawn settlement no longer penalizes unspawned queued Overworld raiders. Pre-update running worlds retain their legacy raid profile; profile choice is persisted at new-run start.
- This is an alpha playtest, not a physical 100-day acceptance. The launcher installation engine and existing-profile preservation logic are unchanged.

## 0.2.0-alpha.6+26.3 — Glowing unique bed marker
- The sole Spawn Bed receives a green vanilla Glowing outline, in addition to the existing green particles.
- A tiny, non-colliding two-part BlockDisplay overlay exactly tracks the real bed orientation. It never acts as a bed item or changes the bed's block state.
- Outline entities are removed on pickup, block destruction, HP-zero or challenge restart; stale chunk-load entities are validated at the next server tick.
- The earlier **one dropped portable Spawn Bed, no ordinary duplicate** fix is preserved unchanged.
- Other challenge rules, difficulty and Windows launcher are unchanged.


## 0.2.0-alpha.5+26.3 — Unique Spawn Bed world-drop fix
- A player breaking either half of their registered Spawn Bed now drops exactly **one** unique Spawn Bed as a world ItemEntity, following ordinary Minecraft pickup behavior. It is no longer inserted directly into inventory.
- Both BedBlock halves are removed before neighbors are notified, preventing a vanilla **ordinary bed duplicate** when removing the special bed.
- Keeps single-use bed owner token, permanent HP, and existing no-raid-warning-on-manual-pickup behavior.
- Real server GameTest now mines both foot and head through Fabric PlayerBlockBreakEvents, checks exactly one tagged ground drop, zero direct inventory insertion, and successful reinstallation with HP preserved.


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
