# Phase L1 acceptance

## Automated gates

The launcher workflow must be green, including Windows x64 publishing and WPF smoke. The package-free test executable covers manifest parsing, supported-version/dependency selection, path traversal/URL rejection, missing installation, hash/size success and failure, duplicate file targets, cross-process lock, running-game guard, first install, both official profile formats, original profile/settings preservation, AllBlocks/world/config/other-mod preservation, repeat update without redundant download, new version replacement, downgrade rejection, injected swap failure/rollback, network/corrupt download preservation, manual duplicate-JAR rejection, actual child-process termination and startup recovery, recovery target allowlist, and release selection excluding drafts/stable/other-MC/incomplete releases.

Actual release bundle tests validate all prepared JAR/JSON payloads and install them into a **synthetic** launcher root. Public-release validation repeats this using real GitHub Releases/Maven downloads. Published EXE smoke initializes and renders the real WPF window; after publication it also queries public updates. These are not claims of authenticated game launch.

## Existing-profile automated gates

Both official profile formats, absent/relative/external gameDir, existing compatible API reuse, only-one-download install, repeat update, original manual Bed Defense filename adoption, Sodium/Iris/AllBlocks/API/world/config byte preservation, duplicate/conflicting/wrong-version mods, nested incompatibility, missing API, old Loader, unsupported predicates, override refusal, concurrent profile/mod change, cross-root target locking, existing-mode rollback and real process termination recovery are tested. Actual prepared/public release payloads are installed in both modes and previous independent environment bytes are compared. Actual published WPF EXE exercises existing profile selection, gameDir resolution and independent-mode switching using fixtures.

## Physical Acceptance Pending — Windows 11 x64

1. Keep a backup of important worlds. Close the game and official Launcher after running the desired **Fabric 26.3** profile once.
2. Download the new `BedDefense-Setup.exe` (launcher 0.2.0). Confirm **기존 Fabric에 설치 (기본)** and select your profile. Check the displayed actual gameDir. Note the names/hashes of Sodium, Iris, API, other mods, config and saves before install.
3. Press **설치**. Confirm one Bed Defense JAR, unchanged API/other mods, and unchanged profile name/ID/gameDir/lastVersionId. No new profile should appear.
4. Open official Launcher and run the **same existing profile**. Confirm the current Bed Defense prototype HUD loads. Real mod interactions, graphics mods and Java runtime boot remain physical acceptance.
5. After the next successful dev release, use the same EXE's update buttons. Confirm new build, exactly one Bed Defense JAR and preserved other files/worlds. Test running-game refusal and offline error preservation.
6. On a backed-up test environment, select an incompatible/missing API or 26.2-mod configuration. Expect installation disabled with a specific reason and no dependency changes.
7. If previously independently installed, use **이전 독립 설치에서 기존 Fabric으로 전환**, select the target and install. Verify the old instance/profile/worlds remain. No automatic world transfer is expected.
8. If desired, select **독립 환경에 설치** to check the retained original L1 flow. Repeat with a nondefault root and external gameDir if used.

Pending user evidence: Store/Win32 Launcher discovery on the user's PC, profile display and real authenticated game boot, Java 25 runtime, actual modpack interactions, subsequent dev update on that PC, custom roots and unsigned EXE SmartScreen behavior. CI fixtures and WPF execution are not physical game acceptance.
