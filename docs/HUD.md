# HUD v2 — Korean, frameless (Minecraft 26.3)

## Layout agreement
- All HUD labels in Korean; white text except the **large pale orange** day.
- Upper left, in order: `n일차`, `XYZ : x / y / z`, `플레이 시간 : HH:MM:SS`, `생물 군계 : <한글 이름>`.
- Dimension line removed.
- Stopwatch counts **world elapsed game-time ticks**, sourced from the server Overworld and synchronized across dimensions. It is **not a timer to next night** and is not yet anchored to a formal challenge-start event.
- Upper center: `레이드까지 M:SS초...`, appearing only in the final 60 seconds before the **provisional sleep-available tick** 12542. This is currently a **visual scheduling prototype**, not a working mob event.
- Upper right: **one custom horizontal HP bar**, NO numeric labels or status text. Bright green proportional fill when installed; **yellow full** for absent/unplaced bed; **red full** for destroyed bed. As there is not yet a persistent Spawn Bed implementation, normal mode displays yellow full by default.
- No rectangular text backdrops/panels anywhere. The thin bar track is permitted as the HP indicator.

## Test commands
- `/bdhud` toggles the whole HUD.
- `/bdhud preview` cycles demonstration states: green 86% installed -> yellow full unplaced -> red full destroyed -> off (normal yellow full).
- While preview is enabled the center shows mock `[미리보기] 레이드 몬스터 12 / 16` and, for installed preview, `스폰 침대 공격 중! [미리보기]`. No state mutation or real mob spawning.
- The original `/beddefense debug cracks` cobblestone test remains intact.

## Testing notes
- Screen layout can be inspected immediately after installing the newest Action artifact.
- Run `/time set 11500` with cheats in a throwaway world to inspect countdown near the last minute; night trigger remains an approximation.
- Check Korean glyph rendering, GUI scaling, and the cross-dimension stopwatch.
- Spawning raids, actual HP, HP warnings tied to real damage, final dragon timer freeze, and challenge start/reset are **not implemented**.
