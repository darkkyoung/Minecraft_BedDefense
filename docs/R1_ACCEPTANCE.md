# Phase R1 — PC acceptance

Target: Minecraft Java 26.3 / Fabric / Java 25. Run in a **new disposable world**. Automated tests generate separate worlds and never access the user's saves/mods/config.

## Install and start

1. Close Minecraft. In your existing Bed Defense Launcher select the existing Fabric 26.3 profile, check updates, then install the mod update. The R1 change does not require downloading another EXE.
2. Open the same profile and create a new survival world. Enable cheats only for the debug checks below.
3. On entry, `[게임 시작]` is above plain rules. Select normal, reset morning, ×2, confirm. Observe 3/2/1 and sound, then day 1 and running stopwatch. Close/reopen before confirming once to check retained choices.
4. Exit/rejoin after start: day, stopwatch, settings, HP and ownership must persist. `/beddefense` reopens the menu. No `/bdhud` command should exist.

## Bed and survival

1. Place two beds. Right-click the first during daylight: green particles, green bar fills, real respawn point. The second must refuse sleep/respawn.
2. `/beddefense debug hp 63`. Break your registered bed: yellow bar, `살아남으세요`, warning sound and hardcore heart style. Reinstall the issued “스폰 침대” item in the Overworld: HP remains 63 and normal hearts return.
3. Carry a normal bed too: it must not replace the carried Spawn Bed. In a creative test, copying the portable stack and placing one must invalidate the duplicate. Nether/End placement must fail.
4. Hold iron ingots and right-click the installed bed: +10 HP per ingot up to 100; no consumption at full HP.
5. `/beddefense debug hp 0`: white flash, red full bar, physical bed removed, no replacement. In a separate run test an explosion/removal of either half and immediate ordinary-bed replacement: permanent destruction must remain.
6. Separate disposable runs: die unregistered, carrying, or destroyed → defeat/spectator; die with placed bed in Overworld → normal respawn.
7. Set HP 20, enter Nether/End, die: one final Overworld respawn, destroyed bed. Next death → defeat. Rejoin between death and respawn to check allowance does not increase.

## Raids and siege

- `/beddefense debug state` shows bed state, HP, player death state and remaining raid count.
- `/beddefense debug raid` sets night and launches the current day's raid; it does not duplicate an already-accounted day. With no bed, first day must still have five zombies.
- With ×2, use `debug day 2` then `debug raid`; roster must be 3. Day 3 uses roster 5. Use new days after each dawn rather than asking for a duplicate wave.
- On multiplier ×1, test roster 41/51. From 51 all eligible zombies/skeletons/spiders/pillagers/vindicators/wither skeletons have Speed III. Creepers/witches/blazes/ravagers do not.
- Stand alternately nearer/farther from raiders than the installed bed. Different mobs can choose different targets and switch. Put dirt/planks/cobble/obsidian in their path: first-band durations 4/9.6/24/96 s, native cracks 0–9, cancellation clears cracks. Test gaps and a raised target for bridge/tower placement with 1–2 s cooldown.
- Bed hits reduce server HP, animate the bar, and show `스폰 침대 공격 받는 중!` above hotbar. No overlap with top-center remaining count.
- `/time of minecraft:overworld set 17000`: remaining raiders glow. `/time of minecraft:overworld set 23000`: Overworld survivors disappear immediately and HP settlement is fixed. Repeat with an unloaded survivor and a cave survivor. Nether/End survivors remain into next night's wave; queued troops must stay bounded and resume when present.

## End and visual acceptance

- Kill the Ender Dragon during a run: success/result, frozen timer. `/beddefense debug day 101`: deadline defeat, frozen timer. Day 100 itself must not fail.
- Check 1080p/1440p and Minecraft GUI scales 2/3/4, Korean labels, normal/damaged/carried/destroyed bar, hardcore hearts, particles, countdown/warning audio, left HUD and actionbar with Sodium/Iris/AllBlocks only where those mods actually declare 26.3 compatibility.

## Evidence boundaries

CI executes compiler, unit tests and real server GameTests; the release pipeline additionally executes Windows launcher file-preservation/recovery, real bundle validation, WPF single-EXE smoke, public updater download/checksum checks.

**Physical Acceptance Pending:** human client graphics/audio, live combat/pathfinding feel, multiplayer latency, modpack interactions and a natural full 100-day playthrough. Headless server tests cannot certify these. The exact external 100-day v2 type sheet was not present; existing PR counts/type distribution are preserved with explicitly documented captain/buff additions.

## Alpha.2: chat countdown and Spawn Bed heading

- First join and every option selection must display only **one** current menu block:
  the server clears the prior chat menu (same client protocol as AllBlocks master)
  before sending the new menu; selected options remain in the persistent world settings.
- Clicking **[설정 완료 · 시작]** closes the chat and clears the previous menu
  *before* the first centered title `3`; then `2`, `1`, and `시작!` occur.
  Verify the game view stays visible during the countdown.
- Top-center `스폰 침대` title uses actual Minecraft **bold** glyphs
  scaled to 150%, centered above the unchanged vanilla boss bar.
  Unregistered = neutral gray, installed = green, carried = yellow,
  destroyed = red. The explanatory status line stays at standard font size.
- Check all four states using `/beddefense debug preview` in a disposable test world;
  verify title does not overlap the HP bar, raid count, or vanilla boss bars.
- These are visual/chat fixes only. No gameplay/launcher install logic changed.

## Alpha.3 feedback acceptance (2026-10-20)
- Test raid-mob last HP hit vs owner pickup, externally destroyed bed, sunrise penalty, Nether/End penalty and no-bed start. **Only raid hit triggers '살아남으세요' title and Wither sound.** Risk hearts remain a separate visual state.
- Attempt other ordinary bed with a registered Spawn Bed: smoke from the rejected bed, error chat; no vanilla spawn setting or sleep.
- At HP 99, repair with one emerald or diamond: HP reaches 100 and consumes one item. Iron ingot does not repair; no item consumed at full HP.
- Summon Ender Dragon boss bar outside raid: should sit just below the special-bed status line; during a raid/countdown, spacing remains sufficient for the warning/count.
- Finish a run, type /beddefense: [다시 시작] opens settings, 3-second countdown, new 1일차 and stopwatch. Previous bed owner and HP, raid day and players are reset. Normal world construction/inventory is **not** rolled back (it is not a new seed/world).
- A completed run's restart must not be triggered by merely opening setup or cancelling it; reset happens only at confirmed countdown finish.

## Alpha.4 scoped first-join chat UX
- World entry before the challenge starts: **greeting → introduction and objective/rules → clickable [게임 시작]**. The action opens the existing settings screen. Completed and running challenge menus remain unchanged.
- Setup final `[설정 완료 · 시작]` button is **bold pale-lime** (#A8FF73), while `[취소]` remains secondary gray. Existing 3-second countdown behavior remains unchanged.
- **Not changed yet** (awaiting user discussion): spacing between setup groups; Spawn Bed glowing visual effect; difficulty multiplier balancing. No increased mob counts, AI stats, or damage effects in this alpha.

## Alpha.5 — one physical bed drop, no normal duplicate
- In an expendable world, place and register the unique bed. **Break its foot**, check one green-named Spawn Bed item appears in the world; inventory must NOT instantly gain the item. No ordinary bed item may also appear.
- Pick it up normally and reinstall, confirming HP and owner-token integrity.
- **Break its head** and repeat the one-drop/no-duplicate test. Confirm the BedBlock foot/head are both air, and no delayed ordinary drop appears from neighbor updates.
- Destroy the bed in a raid: must NOT drop a portable Spawn Bed. Only player manual pickup produces the unique portable item.
- Confirm no direct inventory insertion in Creative or Survival; user must collect the ground item.

## Alpha.6 glowing Spawn Bed visibility acceptance
1. On a throwaway survival world, register a two-part bed: existing green happy particles persist, plus a green glowing outline appears on both halves, even behind close walls.
2. Break the head then the foot in separate tests: **exactly one** portable unique bed item drops in-world. No inventory teleport or second ordinary bed.
3. Collect and reinstall the item: new outlined bed appears at the new location; old outline disappears.
4. Break or permanently destroy the placed bed: no invisible glowing markers remain. Rejoin and revisit unloaded chunks after a new challenge to ensure stale markers are removed.
5. Confirm transparent display entities have no collision or click interception and no obvious duplicate-texture flicker with shaders (Sodium/Iris).

## Alpha.9 clean start + silent conversion acceptance

1. In a disposable new survival world, hold items in hotbar/main inventory, armor/offhand and on the carried cursor. Choose settings, then click `[설정 완료 · 시작]`. Verify **Adventure mode** throughout `3→2→1`, with all items still present before the final tick.
2. After `시작!`: verify **Survival**, completely empty main inventory, equipment/offhand and carried cursor. Ender Chest, XP, world construction and dropped items should remain.
3. Watch chat through both transitions: there must be **no game-mode change notification and no item-cleared count**. The intended `챌린지 시작!` announcement is permitted.
4. Start a completed world's **[다시 시작]** from a formerly eliminated spectator. At confirmed finish the participant must rejoin Survival with cleared inventory; unrelated deliberate spectator observers should remain unaffected.
5. In a disposable world, disconnect the countdown owner before completion, or trigger a failed preflight. All still-online participants should have their former game modes restored and keep their items. Opening/cancelling settings alone must never clear inventory.

## Alpha.10 first welcome never clipped

1. Join a fresh disposable world at 1080p and 1440p, GUI scales 2/3/4. Without pressing T, confirm the initial **top border, 베드 디펜스, greeting, explanation, 100-day rules, [게임 시작], and bottom footer** are simultaneously visible instead of clipping the earliest messages.
2. Press T if required to click **[게임 시작]**. The normal settings menu should replace the intro and chat should return to the **user's ordinary chat-height setting immediately**, without overlapping the world HUD.
3. Use `/beddefense` before starting to re-display the full introduction. Reopen normal settings and confirm no duplicate chats, altered permanent chat options or chat-height change in `options.txt`. Disconnect/rejoin and start countdown; normal chat height must be restored.
4. Extremely small game windows still have viewport-constrained chat; the client should not render text above the screen. Test at a readable resolution if necessary.

## Alpha.11: raid zombie must not convert in water

1. Use a disposable world; choose any difficulty and start at night with a zombie raid. Lead/hold a tagged raid Zombie underwater for at least **45 seconds**. Check that it remains a Zombie with its original raid identity and does not trigger a premature `레이드 클리어!` or reduce the remaining-mob counter.
2. Repeat with a tagged **Husk** in difficult mode; it must not convert to a normal Zombie or Drowned. Other native hard-roster Drowned are unaffected.
3. Spawn a **wild, untracked Zombie** underwater as a control: it must still convert naturally to Drowned. Kill a tracked Zombie normally: the raid count should decrease exactly once. At sunrise, surviving tracked Overworld mobs should be accounted for and discarded exactly once.
4. Rejoin after a tracked Zombie had begun shaking before updating the mod; if it survives in the saved world, conversion must be stopped on load. Check server logs and the actual in-game countdown/raid-complete message for duplicates.

## Alpha.12 Normal V1 medium difficulty acceptance
1. Update via existing Launcher and create a **new disposable** world. Select **[보통]**, first-day morning, roster stride ×1, confirm 3-second countdown. Expect random mob totals 6–8 on day1, 9–11 day2, 11–13 day3, and 25–31 at effective roster day10; no change to other difficulty options or spawn bed HP/respawns.
2. Make a wall of dirt/planks/cobble/obsidian and measure Normal initial-band 1.5/3.4/8.5/34s mining after the mob has detected a blockage; verify block placement roughly 0.9–1.5s when it actually attempts a tower/bridge. Obstacle AI/pathfinding means exact observed wall-entry time can include extra navigation delay.
3. At day 10 check a single vindicator captain **inside the selected total**. At day20 expect up to three baby zombies, beginning day25 expect some eligible ordinary zombies with Speed III, and from day51 Speed III on every eligible normal raider. Baby zombies and excluded creepers/witches/ravagers remain excluded from general Speed III.
4. Confirm fire resistance, armor/weapons and late monster types increase without jumping to Hard levels; test dawn penalty for **spawned** survivors but not unspawned queued Normal V1 mobs.
5. Exit/rejoin during a Normal night, verify its count does not reroll. In an **existing in-progress pre-alpha.12 Normal save**, verify its old, fixed mob count/mining and options remain unchanged; new Normal V1 requires a fresh run after a prior completed challenge.
6. Try changing the difficulty with `/beddefense` during an active run: the menu shows status but no active difficulty-change controls; difficulty is fixed until the run is won/lost and a new challenge is confirmed. On restart, the old world/builds remain but player inventory resets by the alpha.9 start rule.
