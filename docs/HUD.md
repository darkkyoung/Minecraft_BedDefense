# HUD v3 — Compact vanilla bar and Spawn Bed visual registration (Minecraft 26.3)

- Korean day/XYZ/elapsed stopwatch/biome on the left unchanged; no box/panel backgrounds.
- Top right label `스폰 침대` over native Minecraft boss-bar sprite drawn with 25% horizontal scale.
- Yellow (full): not registered or known missing. Green (full): first registered bed still placed, **not an HP reading**. Red (full): visual preview for future destroyed state only.
- First right-click on Overworld bed (daytime allowed) registers the canonical bed foot location, saved with per-player UUID in the Overworld persistent data.
- Periodic vanilla HAPPY_VILLAGER green particles at the registered bed, while its chunk is loaded.
- Unloaded bed chunk is treated as a still-registered bed, until its block is loaded and can be checked.
- This is a visual & data-registration milestone only: no portable Spawn Bed item, HP, real bed destruction, survival enforcement, or raids yet.
- `/bdhud preview` cycles green 86%, yellow full, red full, then normal live status.

## Test
1. Install new 26.3 Fabric JAR and compatible API; remove old Bed Defense JAR.
2. Confirm `스폰 침대` label and small vanilla bar, yellow until registration.
3. Put down a vanilla bed and right-click in daytime; expect green particles + bar.
4. Re-click, verify no additional registration. Reconnect and verify persistent marker.
5. Break the registered bed in a **throwaway world**; when area is loaded the indicator returns to yellow.
6. Repeat `/bdhud preview` to inspect all mock states; no enemy spawn or HP effects.
7. Check UI scales and narrow windows.
