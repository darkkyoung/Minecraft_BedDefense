# 26.3 HUD v2 acceptance checklist

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
