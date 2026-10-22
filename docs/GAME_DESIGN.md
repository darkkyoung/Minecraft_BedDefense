# Bed Defense — Phase R1 gameplay contract

Target: Minecraft Java 26.3, Fabric Loader >=0.19.5, Fabric API 0.163.0+26.3, Java 25.
This contract supersedes the alpha.5 prototype and October 19 proposal where they conflict.
Physical Acceptance Pending means human play/visual/audio acceptance, not permission to omit the core loop.

## Start, time and ending

- `/beddefense` is the only top-level command. Joining displays `[게임 시작]` first and plain-text rules underneath.
- Setup offers easy/normal/hard (normal default; Normal V1 applies only to newly started challenges as of alpha.12), keep world clock/reset to first morning, roster multipliers 1/2/3/5 and manual +/- (1–100). Challenge difficulty remains frozen throughout an active run.
- Every choice persists immediately. Reopening/rejoining retains it. Only the setup owner can confirm; 60 server ticks of countdown with 3/2/1 sounds precede the start. Disconnect cancels a pending countdown.
- One shared run per world, per-player bed ownership and elimination. Current nonspectators participate at start; later nonspectators join the active run. Each receives a full roster, with no speculative multiplayer 50% reduction.
- Challenge day 1 is the world-clock day containing the start (possibly partial with “keep”). Day 100 remains playable through tick 23999. The first server tick in day 101 ends the run in defeat. Natural clock rewinds cannot extend the persisted maximum observed day; privileged debug may intentionally change it.
- Stopwatch uses server game ticks, including time while a dedicated server is running without players. Victory/defeat persists a frozen elapsed value.
- Ender Dragon death wins an active challenge. Without an installed bed, death eliminates that player; all participants eliminated loses the run. Deadline loses the whole run. Eliminated players respawn/rejoin as spectators.

## Unique Spawn Bed

- First Overworld bed right-click after start registers that player's unique bed even during daytime and sets a real respawn configuration.
- Other ordinary beds cannot replace it, set respawn or permit sleep. Server sleep and respawn hooks enforce the restriction as well as the interaction callback. No bed use in Nether/End during a challenge.
- HP: 100. Right-click the installed bed holding iron ingots repairs up to 10 HP per ingot; full HP consumes nothing. This is provisional balance.
- Owner breaking either half intercepts vanilla drops, retains HP in server data and issues one portable bed with owner UUID and a random token. Other players cannot pick it up. Pickup is allowed during raids; the player takes the missing-bed death risk.
- Placement requires the matching owner/token, state `carried`, Overworld, two replaceable positions inside the border and solid supports. The server invalidates the token after placing both halves; copied/replayed items and ordinary substitutions fail. Tokens are never a client-side HP authority. Recoloring/crafting away the token loses the ability to reinstall that item.
- Pickup → yellow/risk; reinstallation → green/normal. Loss of the portable item leaves the player at risk; ordinary beds are not a recovery shortcut.
- Chunk block changes detect explosions, piston/world edits and replacement, not just player break events. Destruction is permanent, invalidates any token and removes the bed without another portable drop. Bed HP attacks are independent of obstacle mining.
- Per-player state, cause, position, HP and token persist. Alpha.4 positions and PR1 HP/state records migrate additively. A legacy PR1 carried bed did not have a token, so it cannot be silently replaced by an ordinary bed; use a fresh world for R1 acceptance.
- Overworld death with installed bed respawns normally. Nether/End death deducts 20 HP. If that death reaches zero, its persisted `FINAL_RESPAWN` state permits exactly one Overworld respawn; consumption returns the participant to alive with a permanently destroyed bed. Another death then eliminates. Duplicate callbacks/reconnect do not mint an allowance.

## Daily raids and AI

- Every night at Overworld clock 12542, once per challenge day, regardless of bed state.
- `rosterDay = min(100, 1 + (challengeDay - 1) * multiplier)`. This affects composition, buffs, bed attack strength/cooldown and siege speed, not the nightly interval.
- All 100 daily totals from PR1/PR2 remain unchanged, starting with five zombies and ending at 80 per player. Their existing draft type distributions are reused. Every tenth roster replaces its last slot with a captain: vindicator (10–20), evoker (30–50), ravager (60–100), Strength II and Resistance I. Counts do not increase for captains.
- Speed III appears selectively from roster 41 and on every eligible ground mob from roster 51. Creepers, witches, flying casters and ravagers are excluded. The exact original external “v2” type-by-type sheet was not in these branches; the preserved draft distributions plus captains are the explicit provisional roster, not a claim to have reproduced an unavailable sheet.
- Each raider compares nearby living participant distance to installed bed distance independently; ties choose player. No bed attack concurrency cap. Vanilla follow-range attribute is multiplied by 2 in Overworld, 5 in Nether/End through a nonstacking modifier. A priority movement goal and target hook keep vanilla goals from overriding a selected bed.
- Adjacent bed attacks require an unobstructed ray; walls are mined instead. Obstacle mining shows vanilla stages 0–9 and clears on target change, movement, block replacement, death/unload or interruption.
- Mining tables from PR2 receive ×0.8: first band dirt 4 s, planks/logs 9.6 s, cobblestone 24 s, obsidian 96 s. Block entities, beds, unbreakable blocks, portals and reinforced deepslate are excluded.
- Short gaps and vertical obstructions can be bridged/towered with cobblestone: 20–40 ticks between placements, 24 placements per mob, 16 placements per second globally, 12 simultaneous miners, 32 path requests per second.
- At clock 17000 (five minutes before morning 23000), all tracked raiders glow, including later-loaded entities.
- At morning the server first settles the persisted Overworld UUID/queued ledger by owner, charging a provisional 1 HP per unhandled raider. Then loaded survivors are discarded immediately; unloaded survivors are tombstoned for removal on load. No drops/XP or post-dawn kill can change the fixed penalty.
- Nether/End survivors and queues persist into later nights. Active ledger cap 128 per dimension; at most eight spawn attempts per second. Unspawnable troops stay queued rather than disappearing. One compact batch per player/night, bounded by the 100-day run. Queues resume when their owner is present in their original dimension.

## HUD and debug

- Top center: vanilla-style HP bar. Dark gray empty/unregistered, green HP/placed, yellow full/carried, white flash then red full/permanently destroyed. Smooth server-driven fill/damage, brief damage flash/jitter. State is authoritative on the server.
- Remaining count below: `남은 몬스터 수 : remaining / total`; no duplicate raid title. Countdown warning below that. Bed attack warning is an actionbar packet.
- Left: peach challenge day, white XYZ/elapsed/Korean biome. No text panels.
- Missing/carried/destroyed bed in an active run changes normal hearts to hardcore appearance. Risk warnings use title `살아남으세요` and a sound at the affected player. Reinstallation restores vanilla heart choice. The world itself is not converted to vanilla hardcore.
- `/beddefense debug` requires gamemaster permission (level 2): `state`, `day 1..101`, `raid`, `hp 0..100`, `cracks`, `preview`. `day` changes the challenge-day offset; `raid` sets world clock to night and respects once-per-day accounting. No release reset command.

## Integration provenance

Fresh main: `9f3c24a1d901c0acd6a11a3d98783ad70e22cd62`.
PR1: `823226ab146dbfd508fe9de26b1b117f9d1e95b3`; PR2 (stacked on PR1): `b45f61b8f4d6cb47c13b4bfa10efe3efd00c46ca`.
AllBlocks `Minecraft_AllBlockMod_26.2` **master**: `18b0fc6e96a651c8be96485ffb22b61f31066741`.
Reused setup flow/countdown pattern, PR1 counts/save identities and PR2 daily progression/HUD/siege tables. The experimental `mc/26.3-core-gameplay` branch stored duplicate settings inside `ChallengeData` and removed the raid/vitality work, so it was compared but not blindly merged. The already-established `ChallengeOptionsData` remains authoritative.

## Alpha.8 hard-balance addendum (supersedes alpha.7 hard values)

- The single authoritative selected difficulty is saved at **new challenge start** as `raid_balance_profile`: `PROFILE_HARD_V2=3` for this revision, `PROFILE_HARD=2` for existing alpha.7 hard runs. Existing in-progress worlds are never silently changed by a mod update.
- New hard raids draw **one uniformly distributed integer per nightly wave**, persisted in the saved `RaidStateData` as `last_random_wave` and `last_random_roster`, shared across player batches. Within each player batch `next + count` preserves the selected total across spawn retries and save reload. The same day cannot be relaunched to reroll.
- The upper bound remains alpha.7 hard's fixed `HardRaidBalance.totalForDay(effectiveRosterDay)`. A reduced baseline begins at 75% for roster days 1–10 and interpolates through agreed reference anchors (day 20=59, 30=70, 50=94, 75=111, 90=121); the lower bound is **ceil(70% × reduced baseline)**. By roster day 90 the baseline again equals the old maximum, though the random lower bound remains 70%.
- Hard composition (including boss and baby zombies) scales to the selected total. Each tenth roster retains exactly one boss per player's wave. Speed III from roster day 10 and existing hard buffs, fire-resistance and weapons are otherwise unchanged.
- New hard alpha.8 obstacle mining first-band durations: dirt 1s, wood 2s, cobble 5s, obsidian 20s; placement 15–20 ticks, stuck threshold 20 ticks; progress evaluated every 5 ticks. Old alpha.7 hard profile retains previous durations (0.5 / 1 / 2.5 / 10 s) and 5–10 tick placement. Easy and normal profiles unchanged.
- Always respect dimension active-entity cap 128, 12 simultaneous miners and 16 placed blocks/second. Difficult-mode unspawned queued Overworld raiders remain exempt from dawn HP penalties.
- This specification does not claim real-world pathfinding/balance or 100-day physical acceptance. 

## Alpha.9 start/countdown reset contract (supersedes earlier inventory-retention notes)

- After clicking **[설정 완료 · 시작]**, selected online participants move to **Adventure mode for the 3-second (60-tick) countdown**. All conversions use server-side Java APIs, NOT `/gamemode` or `/clear`, and must not generate system-chat feedback for switching modes or clearing items.
- Participants include non-spectators, the setup owner, and eliminated spectators who participated in the completed previous run. Unrelated intentional spectator observers are unaffected. Late eligible joiners are caught by the active countdown.
- Inventory and cursor must **remain intact while the countdown runs**. Only after the world state successfully commits a new challenge does the server empty main inventory, hotbar, armor, offhand, carried cursor and convert participants to **Survival mode**, before `시작!` title/sound. The normal challenge-start announcement may still appear in chat.
- Disconnect of countdown owner, failure to reset world time, or any aborted start restores each online participant's prior gamemode; NO item clearing occurs on failure. Re-entering settings during an active countdown cannot bypass the lock.
- Confirmed **same-world restart** clears regular player inventories, despite preserving world constructions and Ender Chests. XP and dropped world entities are not cleared. Older documents saying ordinary inventory is preserved apply only until the new start is confirmed.

## Alpha.10 first-welcome chat viewport

- Before starting a challenge, `ChallengeMenu.show` sends `MenuUiPayload(closeScreen=false,welcome=true)` immediately before the greeting/goal/rules/clickable start messages. The extra flag is decoded by the client and controls only an in-memory welcome presentation bit.
- The client chat component increases its **visible height only during this introductory state** to fit wrapped text. It is dynamically capped by GUI screen height and chat scale and leaves a fixed top margin. No global/focused/unfocused chat settings or `options.txt` are written.
- The normal `MenuUiPayload(false)` sent by the setup/options screens disables expanded chat before rendering settings, as does the countdown's `MenuUiPayload(true)`. Client disconnect also disables expansion. The initial introduction can be reopened by `/beddefense` before challenge start.
- Chat formatting, `[게임 시작]` clickable command, clear-previous-menu behavior and Bed Defense HUD remain unchanged. On exceptionally small GUI viewports, the chat remains constrained by screen space; game chat is still scrollable when focused.
- Server and launcher acceptability are separate from screenshots. CI client game test captures `r1-welcome-chat-full-height` and verifies a larger visible-line count; actual Korean font/shader/GUI scales require user validation.

## Alpha.11 server raid-identity protection against water conversion

- A Bed Defense raider is a **server-saved entity UUID** in `RaidStateData`. Vanilla water conversion replaces a zombie with a **new entity/UUID**, which loses the old raider's goal and raid accounting. It may falsely clear a wave if the old entity triggers death/unload cleanup.
- Prevent **only registered raid zombies** from starting the vanilla Zombie → Drowned conversion. Also prevent registered **Husks** from performing Husk → Zombie conversion (Husk independently overrides `convertsInWater()`). Both mixins check the authoritative `RaidStateData` UUID membership on the server. Normal untracked wild zombies/husks remain vanilla. Native Drowned raids still spawn as Drowned.
- A previously tracked zombie/husk reloaded while already converting has its underwater/conversion timers cleared on entity load, preserving the original UUID after upgrade. The spawn/roster totals and server ledger stay unchanged; legitimate death, sunrise, or challenge reset still removes raiders normally.
- No changes to water physics, swimming/pathfinding, armor, effects, bed HP, or permanent save schema. Validate long submersion and actual morning penalty in a disposable world; CI GameTest covers conversion-vs-tracking at advanced water counters plus the untracked conversion predicate; natural 45-second wild conversion is a manual acceptance test.

## Alpha.12 Normal V1 intermediate raid profile

- Normal V1 is a **separately saved per-run profile**: `PROFILE_NORMAL_V2=4`. Only a newly started "보통" run after alpha.12 uses it. Previously running "보통" challenges remain `PROFILE_LEGACY=0` without retroactive balancing. "쉬움" (`1`) and both historical/new "어려움" profiles (`2/3`) are unchanged. No difficulty switching mid-run, including via `/beddefense` setup.
- Nightly per-participant mob total is drawn uniformly **once** from [min,max] anchored and linearly interpolated over all 100 effective roster days: day 1 6–8; 2 9–11; 3 11–13; 5 14–18; 10 25–31; 20 34–42; 30 41–51; 50 53–65; 75 62–76; 90 75–91; 100 84–102. The selected count is saved through existing `RaidStateData.launch` as a single `last_random_wave/last_random_roster` number and encoded in each `Pending` batch as `next + count`; reconnects/retries cannot reroll the day's wave.
- The Normal-specific mob-composition percentages follow a staged table: initial ordinary zombies with gradually added husks/spiders/skeletons, drowned/pillagers from day 10, **baby zombies (max 3) from day 20**, more varieties and elites later. A single designated captain occupies one already counted slot on each 10th effective roster day. Starter counts and cap apply per participating player; not multiplied by team size silently.
- Normal combat modifiers are weaker than Hard's stage progression: fire resistance 10% day1, 35% day10, 75% day50 and 95% days90–100; weapon/armor and strength/resistance/water movement bonuses stage up over time. Eligible ordinary mob Speed III starts selectively on day25. **From day51 it is guaranteed** just as in Easy's original profile (Hard V2's chance schedule remains unchanged). Baby zombies are intrinsically fast and never receive an additional Speed III potion. Drowned raid spawns and the no-underwater-conversion tracked-zombie fix remain intact.
- First-band mining is intermediate between current Easy/Hard V2, computed as their mean with a tenth-second rounding: dirt **1.5s**, planks **3.4s**, cobble **8.5s**, obsidian **34s**. Normal V1 starts a stuck job after ~30 ticks and evaluates its job every 5 ticks; bridge/tower placement cooldown randomized over **18–30 ticks (0.9–1.5s)**, preserving global miner/block budgets. Existing `PROFILE_LEGACY` Normal uses original much slower mining and 20–40 tick placement.
- Unspawned queued Normal V1 Overworld monsters are discarded without dawn HP charge, like Hard; spawned survivors still cost HP1 each. The active cap and path-scheduling safety limits are unchanged.
- This is a design/CI implementation, not a verified 100-day human survival acceptance; game difficulty labels should be judged by user playtest as separate player experiences.

## Alpha.13 single recovery beacon and immutable two-stage bed revival

- During an active challenge, when the owner-linked Spawn Bed HP reaches 0 (or its registered blocks are externally destroyed), it becomes **DESTROYED** and spawns **one custom block** `beddefense:recovery_beacon` at the old FOOT, if and only if that owner's used revival count is under two. It is not a vanilla BeaconBlock: it has no gameplay effects, normal payment GUI, pyramid requirement, or loot. A dedicated `BeaconBeamOwner` block entity feeds a constant green section to the Minecraft 26.3 `BeaconRenderer`, creating a beam regardless of a beacon base.
- One block only; negative mining hardness, high blast resistance, piston-block reaction, no loot/item and a server-side before-break guard (including Creative) protect it. Commands from operators and external mods can still change blocks; a periodic server reconciliation recreates a recoverable beacon only when the old FOOT is empty. It never overwrites a user-built block.
- Server SavedData `beddefense:bed_recoveries` stores owner UUID → successful revivals (0/1/2) plus the original bed orientation. No client item components, cloned items or world crafting recipes can mint allowances.
- Right click accepts only its **living non-eliminated owner** and opens a vanilla 3×3 dispenser inventory. Ghosted recipe icons are **pure client rendering**, visibly faded; they are never actual items in the container. Deposit one ordinary bed in the CENTER (keypad 5) and one emerald in each other slot for first restore, or one emerald block in each for the second. Each successful restore consumes exactly 9 resources and builds a red Spawn Bed at the original foot/head in the saved orientation; restoring grants **25 HP first, 50 HP second** and re-establishes that owner's real Overworld respawn. The signal disappears. On third destruction no signal appears.
- The server refuses payment if the head space is blocked, world border/support validation fails, state changes, the player is not the owner, or the challenge isn't running. Deposits are preserved on failed placement; unspent stacks are returned on menu close. Overflow can drop near the owner. Server performs the transaction on tick; the client GUI cannot authorize revival.
- While destroyed, the player is still at risk of elimination if they die before restoration. A periodic owner-only actionbar shows old bed position and remaining revival count. Other players may transport/donate resources, but never interact with or spend another owner's allowance.
- Confirmed new challenge resets both revival history and stale recovery signal blocks before clearing old Spawn Bed ownership; opening/cancelling setup alone cannot reset recovery records. Older saves without `bed_recoveries` initialize their owner revive count at zero. No ordinary vanilla beacon behavior is modified.
- Emerald-only bed repair (HP 1 each) supersedes earlier diamond/iron repair text.
