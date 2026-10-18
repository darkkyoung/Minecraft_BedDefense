# Testing and acceptance

## HUD prototype smoke test (26.3)
1. Use separate Minecraft 26.3 Fabric profile, with matching 26.3 Fabric API and Bed Defense JAR only.
2. Confirm vanilla-font, **frameless** DAY /100 upper-left, X/Y/Z, NIGHT IN, biome, dimension and upper-right BED -- / -- with PROTOTYPE label. Nothing should use panel/box backdrops.
3. Confirm position updates with walking and biome changes; validate day/time while Nether/End is active and when returning to Overworld.
4. `/bdhud preview` enables labeled sample RAIDERS 12 / 16, SPAWN BED UNDER ATTACK, BED 86/100 and PLACED PREVIEW; repeat to disable.
5. Preview causes **no actual enemies to spawn, no player/beds changed**.
6. `/bdhud` turns HUD on/off; F1 hides with vanilla HUD.
7. Check GUI scale 2/3/4 and a small window, and watch for overlapping vanilla elements.
8. Check no HUD remains stuck after returning to main menu and joining another world.
9. After local multiplayer/dedicated join, confirm time sync; do not claim multi acceptance without real test.

## Cobblestone crack debug smoke test
1. In a disposable world with commands enabled, place a cobblestone block.
2. Look directly at it within 6 blocks; run `/beddefense debug cracks`.
3. Confirm cracks increase from stages 0 to 9 over 4 seconds.
4. Confirm the cobblestone block breaks and drops.
5. Run on dirt and verify rejection, and repeated commands on the same target should not create overlapping tasks.

## Not yet executable
- Spawn Bed registration, HP tracking, state changes
- Real raids and accurate RAID IN countdown, physical monsters breaking/placing blocks
- Actual warning and result UI tied to game state
- Death/dimension penalty, Ender Dragon completion, spectator revive

CI compilation success is necessary, not sufficient. Report live visual results separately.
