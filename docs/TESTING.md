# 26.3 HUD and clickable start-menu acceptance checklist

## Alpha.5 start menu
1. Create a **new disposable world** (preferably test in Creative with commands allowed). On join, see Korean clickable [게임 시작] and [규칙 보기].
2. Before clicking start: HUD says 시작 전, 플레이 시간 stays 00:00:00, raid warning is hidden.
3. Click [규칙 보기] and return to the menu; verify no timer starts.
4. Click [게임 시작] once: server announces the start, HUD soon shows 1일차 and timer counts upward.
5. Try /beddefense start again; it must NOT reset time, change start day or create duplicate start state.
6. Leave and rejoin the world; ongoing status menu appears, timer continues from persisted world time.
7. Type /beddefense or /beddefense menu at any time to reopen it, and /beddefense status to read elapsed time.
8. Start in one dimension, travel to Nether and back; the global stopwatch must stay continuous.
9. Old worlds previously used for alpha.4 may already have a registered Spawn Bed; avoid assuming those states are fresh when testing. No world reset/migration is performed.



1. Minecraft 26.3 Fabric + matching Fabric API. Remove other alpha Bed Defense JARs.
2. Upper left: pale-orange n일차, white XYZ : x / y / z, white 플레이 시간 : HH:MM:SS, white 생물 군계 : 평원.
3. No dimension row, no rectangular background boxes, no always-on NIGHT IN.
4. Elapsed stopwatch advances normally. It is game-tick based, independent of /time set and uses the global world tick counter.
5. Full yellow bar at top right by default, no accompanying text. /bdhud preview cycles 86%-filled green, full yellow, full red, then normal.
6. Preview center lines explicitly say 미리보기 and do not spawn enemies or change Bed HP.
7. Use /time set 11500 and wait. Center warning appears only during final 60 seconds to provisional sleep time.
8. Test alternate biomes and cross-dimension time/stopwatch continuity.
9. Test GUI scales/window widths, /bdhud toggle and F1 hide. Rejoin world should clear preview.
10. Original /beddefense debug cracks test remains available for cobblestone in throwaway world.

Not yet implemented: real Spawn Bed status, real raids, challenge stopwatch lifecycle and defeat/victory logic.
Passing CI compilation does not substitute for visual play acceptance.
