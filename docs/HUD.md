# R1 HUD

Authoritative server packets sync world clock/outcome, bed state/HP and raid remaining/total every four ticks.

- Top center: 182×5 vanilla boss-bar texture, title at y=8, bar at y=21. Unregistered empty dark gray; placed green HP; carried full yellow; permanently destroyed full red following a white flash.
- State text at y=32, raid count at y=47, one-minute warning at y=61. Vanilla boss bars (including the dragon) start below this area while the Bed Defense HUD is visible.
- Left: peach day, white XYZ / elapsed / Korean biome, no rectangular text panels.
- Bed attack uses the vanilla actionbar directly above the hotbar.
- Hardcore heart sprites follow the server's active-run/missing-bed state. They do not mutate the world hardcore flag. Valid reinstallation restores vanilla heart selection.
- HP easing uses world ticks rather than render-frame count; outcome freezes the elapsed timer. Animations never determine server destruction/respawn eligibility.
- `/beddefense hud` toggles display. Operator-only `/beddefense debug preview` cycles clearly labeled previews; it does not change real HP or gameplay. No `/bdhud` alias remains.

Client CI captures all major HUD states; physical display scaling, audio and real combat acceptance follow [R1_ACCEPTANCE.md](R1_ACCEPTANCE.md).
