# Frameless HUD — Minecraft 26.3 prototype

## Agreed in-game layout

**No boxes/panels/opaque backgrounds.** Vanilla pixel font and light shadow only.

- Upper-left, descending: **large DAY n / 100**, real X/Y/Z block coordinates, global Overworld countdown until bedtime, biome, current dimension.
- Upper-right: **large Spawn Bed HP**, small installation state below.
- Raid active (future): centered raid indicator / remaining opponents; warning when Spawn Bed attacked.
- True bed registration, HP, active raid, player loss/success states have **not yet been implemented**.

## Executable prototype

- HUD renders automatically on Minecraft Java 26.3 Fabric.
- Live coordinates & biome and dimension from the client.
- Day and countdown are synced from the server's *Overworld* clock (not the local Nether/End sky); 0-based Minecraft day 0 is displayed as DAY 1.
- Countdown is labeled `NIGHT IN`, not `RAID IN`, because real raid scheduling is not yet implemented. Uses a provisional vanilla bedtime tick 12542 and normal 20 TPS.
- Bed shows `BED -- / --` and `NOT REGISTERED (PROTOTYPE)`, **not dummy HP**.
- `/bdhud` toggles HUD locally.
- `/bdhud preview` toggles a clearly marked illustrative raid overlay and dummy bed HP (86/100) for layout acceptance. Preview never causes raid spawning, changes to world, HP or player state.
- F1 hides the entire HUD.
- Dates and timer reflect actual world time in singleplayer and modded dedicated-server sessions when time is synced; until first server time message, show CLOCK SYNC....

## Visual acceptance

1. Join Minecraft 26.3 test world, with Fabric API and Bed Defense installed.
2. Confirm upper-left day, coordinates, NIGHT IN, biome, and dimension respond appropriately; upper-right bed remains placeholder.
3. Run `/bdhud preview` to preview raid center and bed state text, without panels.
4. Run `/bdhud preview` again to revert, `/bdhud` to toggle, F1 to hide.
5. Travel across dimensions (if accessible) and verify day/time continuity.
6. Test narrow window and GUI Scale settings, and confirm no collision with vanilla bossbars.
7. The existing `/beddefense debug cracks` remains independently usable.

## Open integration tasks
- Connect HUD to server-side Spawn Bed state and real raid schedule, warning and raid results.
- Set Bed damage text color based on live HP only when actual bed state exists.
- Account for sleep/time skips, tick rate, and multiplayer correctness; client interpolation is cosmetic.
- Decide configurable layout and user-specific persisted preferences.
