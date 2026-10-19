> Historical feedback snapshot. Implementation status and superseding R1 decisions: [GAME_DESIGN.md](GAME_DESIGN.md), [R1_ACCEPTANCE.md](R1_ACCEPTANCE.md).

# Bed Defense — 2026-10-19 user corrections / acceptance matrix

Canonical design for the existing in-progress PR #1. **Not an assertion that gameplay is complete.**
Test Java 26.3 + Fabric in a disposable world. Keep production worlds backed up.

## Exact intended behavior

1. **AllBlocks master-style Korean chat setup.** First join: clickable **[게임 시작]** near top; core rules below as ordinary text, no separate rules button. Clicking opens difficulty (쉬움/보통/어려움, real differences TBD), world-time reset or keep, daily raid **roster progression stride** (×1,×2,×3,×5 plus bounded manual +/-). A 3-second title/sound countdown precedes start. `/beddefense` is the only top-level command. All privileged test actions live under `/beddefense debug`.
2. **One owner bed only.** First-clicked bed is the sole usable bed; all other beds must reject both sleep and respawn setting. The original bed can be picked up and relocated **without losing HP**. An unrelated bed must not replace it. Temporary pickup/no placed bed is lethal if the player dies.
3. **Top-center HP bar (AllBlocks progress-style).** Never place this bar at the top right. Unregistered: empty, dark-gray, label under bar `스폰 침대를 지정하세요`. Registered, placed: green, smoothly fills on registration and reacts to damage/repair with interpolation + white flash/shake. Player-broken/unplaced: yellow. Mob-destroyed: momentary full-white flash, then full red. Either kind of missing bed raises `살아남으세요`, effects and hardcore-shaped hearts until valid reinstallation (only possible if portable, non-destroyed).
4. **Raid every night.** Bed absent or unregistered does *not* suppress raids: without bed, hunt player. One raid per challenge day independent of stride and irrespective of current dimension. Roster stride ×2 means challenge days 1,2,3,4... use planned roster days 1,3,5,7... capped to day 100; formula `min(100,1+(day-1)*stride)`. It is **not** a spawn-every-N-days option.
5. **Roaming and buffs.** Keep draft 100-day totals/mixtures (editable, subject to gameplay review). Selective Speed III appears from day 41; **from day 51 eligible regular ground raiders receive Speed III by default**. Avoid automatically buffing creepers/flying/magic attackers to the same pursuit speed.
6. **Each mob chooses nearest target.** Compare distance from that mob to each live player and each *placed* Spawn Bed in its dimension; attack whichever is closest, reassess while approaching. Detection uses each mob's own vanilla follow range ×2 in Overworld, ×5 in Nether/End. Do not impose a max-2-concurrent-bed-attacker cap.
7. **Siege block handling.** Use the established material/day table with every time multiplied by **0.8** (20% faster). Show vanilla cracking stage 0–9; clear on interruption. Block placement cooldown **20–40 game ticks** (1–2s at 20 TPS), bounded concurrent workers/placements, no mining bed blocks directly or griefing unbreakables/block entities. Need live pathfinding tests: basic gap bridging is not proof of arbitrary tower/maze penetration.
8. **Sunrise.** With approximately 5 real minutes until the Overworld-based sunrise, surviving Overworld raid mobs should be glowing even through walls/caves when their entities are loaded. At sunrise, snapshot the outstanding raiders, settle the bed HP penalty, then immediately discard their entities with no slow fire-damage/XP-kill loophole. Unloaded entities require deferred cleanup that does not postpone the penalty. Nether/End survivors persist and stack with the next wave (bounded active mob budget).
9. **Win/loss.** Enemy bed death or missing unique bed -> after next player death, lose. Nether/End death penalty that brings bed HP to zero still allows *that* respawn once, then permanently destroys bed. The Ender Dragon's death is victory. Day-100 failure boundary must be explicit.
10. **Multiplayer** singleplayer-first. A 50% reduction in multi-wave size is only being considered, **not yet a default or decided rule**.

## Current isolated correction branch: what is code vs remaining

- **Code added/modified and CI compiling:** each-night raid schedule with roster stride, speed III default for eligible ground raiders from roster day 51, per-mob nearest player/bed targeting and multiplier-aware detection, no 2-mob attack cap, center HP text without redundant raid-day headline, attack actionbar, brief mob-destroy white flash, manually unplaced bed warning, basic bounded siege cracks and 1–2s bridging with 0.8 mining duration.
- **Still incomplete / not physically accepted:** unique portable bed item and reinstall HP preservation, hardcore heart rendering, all 100 exact draft mixtures/boss variants and potion variants, final victory/defeat enforcement, robust pathfinding through complex structures, spawn/despawn/Nether chunk accumulation stress tests, real-world durability of the 5-minute glow and sunrise accounting. The separate WIP parent branch includes the first raid foundation but has not passed physical Minecraft gameplay acceptance.
- **Critical external dependency:** A parallel draft integration PR #1 exists. Do not merge two different setup implementations blindly; reconcile this design and the branch's code before any main Release.

## Required hands-on tests before main merge

- No-bed night produces zombies targeting player, not zero waves.
- Roster ×2 for challenge days 1/2/3 gives planned Day 1/3/5 waves **on each consecutive night**.
- One mob attacks nearer player while a more distant mob attacks nearer Spawn Bed; target switches without freezing AI.
- Above Day 51 speed III is universal to eligible regular ground mobs; creeper exclusion holds.
- 0.8× block times, increasing crack textures, interrupt clears cracks; placement respects 1–2 s cooldown and worker caps.
- With sunrise 5 minutes away, glowing survivors visible behind cave walls when loaded.
- At sunrise, all outstanding Overworld raiders are counted once, penalized first, instantly removed; no delayed burn-kill loophole.
- Manual bed removal = yellow/unsafe/one unique bed only; mob bed HP zero = white->red and permanent; hard-core hearts and reinstallation final-respawn edge cases once implemented.
- Time reset/keep settings, world reload persistence and release installer update all work in a disposable world.
