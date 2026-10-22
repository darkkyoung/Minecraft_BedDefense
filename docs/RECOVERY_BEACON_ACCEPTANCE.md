# Bed Defense — Alpha.13 single-block recovery beacon

**Target** Minecraft Java 26.3 / Fabric / Java 25. Use a disposable survival world. These steps require human PC acceptance; CI server/client checks alone cannot certify visual fidelity or all multiplayer cases.

## Exact appearance
- When registered Spawn Bed HP becomes zero, the old FOOT becomes exactly **one** `beddefense:recovery_beacon` block with the appearance of Minecraft's vanilla beacon. A green sky beam must render immediately **without a mineral pyramid**; there must be no additional visible decorative blocks, block displays or beacon-tier buffs.
- Unlike vanilla beacons, the recovery block should reject all Survival and Creative mining, piston pushing, and ordinary TNT/crystal explosions. It is never obtainable as a drop or ordinary item; another vanilla beacon anywhere else must retain its normal menu/base/buffs.
- It exists ONLY while the owner's destroyed bed is eligible for regeneration. Manual pickup/carried-bed states do not spawn it; there is no beacon on third destruction. When the challenge ends/restarts, stale signals are cleaned.

## Server-owned 3×3 UI (owner only)
- Right-click the signal with an empty hand or materials. The Bed Defense 3×3 deposit screen must open for the **bed's UUID owner only**. Other players cannot open/pay/claim it.
- Slots 1–9 show client-only ghost icons, about 50% faded. Center 5 is a normal bed, the eight perimeter slots are one emerald each (first revival) or one emerald BLOCK each (second revival). Ghost icons are never actual items and **cannot be picked up, shifted or moved**. Correct real items show in slots normally; wrong inputs do not complete the recipe.
- Close the menu with partial/wrong inputs and verify all real deposited items are returned, including a full inventory (overflow drops at player feet, never extra copies). No deposit is consumed before successful bed restoration.
- Any ordinary bed color is accepted as the *center ingredient*. The restored bed uses the standard red Spawn Bed at the original head/foot orientation.

## Lifetime stages

| Destruction | Recovery material | Spawn Bed restored at old location | Remaining chances |
| --- | --- | --- | --- |
| First | 8 emeralds + 1 ordinary bed | HP 25 | 1 |
| Second | 8 emerald BLOCKS + 1 ordinary bed | HP 50 | 0 |
| Third | None | Remains destroyed, no beacon | 0 |

- Try to physically block the intended head space or remove support below it. The beacon must remain and all items must be returned if restoration cannot be placed.
- Confirm an actual server respawn at the restored bed. A dead player who hasn't yet restored a bed still gets eliminated when they die; the beacon is NOT a temporary bed.
- Owner-specific allowance and old location survive save/reconnect, including offline owners and multiplayer with several simultaneous signals. Gift resources using normal item transfers but do not transfer restoration ownership.
- After an actual challenge ends, start another and verify both allowances reset and orphaned beacon blocks disappear. Opening/cancelling setup alone must NOT reset allowances.

## Boundary
- Automation: Java 25 build and JUnit persistence; actual Minecraft/Fabric GameTest on one complete 0→25→0→50→0 cycle; graphical client smoke; Windows launcher safety and public manifest validation.
- Human: shader effects, long-range beam visibility, ordinary beacon independence, unusual dimension/chunk interleavings, multi-user/lag, and full-world 100-day performance remain pending.
