# Bed Defense — Phase R1 gameplay contract

Target: Minecraft Java 26.3, Fabric Loader >=0.19.5, Fabric API 0.163.0+26.3, Java 25.
This contract supersedes the alpha.5 prototype and October 19 proposal where they conflict.
Physical Acceptance Pending means human play/visual/audio acceptance, not permission to omit the core loop.

## Start, time and ending

- `/beddefense` is the only top-level command. Joining displays `[게임 시작]` first and plain-text rules underneath.
- Setup offers easy/normal/hard (normal default, identical current balance), keep world clock/reset to first morning, roster multipliers 1/2/3/5 and manual +/- (1–100).
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
