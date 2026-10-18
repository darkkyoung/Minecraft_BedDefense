# Bed Defense — Game Design (canonical draft)

This document distinguishes **agreed design** from **not yet implemented** behavior.

## Goal and deadline
- Challenge succeeds immediately on defeating the Ender Dragon.
- Day 100 is the proposed deadline; the exact fail timing is to be finalized.
- Night raids are a daily pressure on exploring/crafting/defending.

## Spawn Bed
- The first vanilla bed used to set a player's respawn becomes that player's unique Spawn Bed, irrespective of whether sleep succeeds.
- The Spawn Bed can only be placed in the Overworld.
- Ordinary beds can still be used for sleep but cannot replace Spawn Bed respawn.
- It can be picked up, relocated and reinstalled. Its HP is retained in the item across the move.
- If the Spawn Bed is **unplaced**, a death ends the single-player challenge.
- If Spawn Bed HP reaches 0 by mob attacks, it is permanently destroyed; **later death** is a challenge loss, not immediate loss.
- For an interdimensional death that reduces HP to 0, grant **one final respawn for that death**, then destroy the Spawn Bed, so future death loses.
- Healing requires a crafted resource/item (recipe TBD).
- Prevent picking up the Spawn Bed during a raid to avoid exploit; this was proposed as a design safeguard, and must still be approved if gameplay differs.

## Time and raids
- Always use the Overworld's clock as the global day/time reference, even while the player is in Nether/End.
- Raid triggers when vanilla sleep first becomes permitted in the Overworld, not when the player actually sleeps.
- Sleeping is blocked during an active raid.
- Overworld raid: spawn bed has priority over player. Without a placed Spawn Bed, monsters hunt the player.
- Nether/End raid: monsters hunt player because the Spawn Bed cannot exist there.
- Overworld sunrise: remaining raid mobs are eliminated with burning/death presentation, with remaining-enemy Spawn Bed HP penalty (formula TBD).
- Nether/End sunrise: surviving raid monsters persist into subsequent days; newly generated waves accumulate. Active-mob limit and queued reinforcements recommended for performance; value TBD.
- Nether/End death: Spawn Bed HP penalty proposed at 20 of 100 (numbers not locked) and normal inventory loss applies.

## Siege AI
- Reach Spawn Bed if possible; if obstructed, break or place limited blocks to create a route.
- Each destroy/place action has a cooldown; prevent unlimited per-tick pathfinding and griefing.
- Hostile mobs visibly swing their arm and show vanilla **block crack stages 0–9** during obstacle breaking. Clear the cracks if interrupted.
- Spawn Bed attacks are **HP damage events**, *not* a cracking/mining progress bar.
- Proposed 10-day scaling table (dirt / planks / cobblestone / obsidian in seconds per basic zombie): 1–10: 5/12/30/120; 11–20: 4.5/11/27/108; 21–30: 4/10/24/96; 31–40: 3.5/8.5/21/84; 41–50: 3/7/18/72; 51–60: 2.5/6/15/60; 61–70: 2/5/12/48; 71–80: 1.8/4.2/10.5/42; 81–90: 1.5/3.6/9/36; 91–100: 1.3/3/7.5/30.
- Proposed bed attack schedule by 10-day brackets (damage / interval): 1–10: 1/4s; 11–20: 1/3.5s; 21–30: 1/3s; 31–40: 1/2.5s; 41–50: 1/2.3s; 51–60: 2/3s; 61–70: 2/2.5s; 71–80: 2/2.2s; 81–90: 3/2.8s; 91–100: 3/2.5s. Two concurrent attackers maximum was a proposed safety cap.
- Existing raid proposal includes base zombie day 1 count 5, day-dependent variety and selected Speed I/II/III buffs from day 11 onward; later-day final balance is editable rather than hardcoded.

## Interface
- Spawn Bed HP bar appears when under attack. Flash/warning sound at low HP.
- Vanilla block cracking overlay for ordinary obstacle blocks; separate damage warning for the Spawn Bed.

## Multiplayer extension
- Single-player first; later support individual player Spawn Beds/statuses and raid scale by active player count.
- A permanently dead spectator could be revived if allies craft the named player's special Spawn Bed (cost TBD).

## Implementation status
- All mechanics above are **design only** until listed as shipped/tested in README and CHANGELOG.
- The first code prototype adds **only** a debug command to demonstrate the native block cracking visual. No actual raid mob currently uses it.
