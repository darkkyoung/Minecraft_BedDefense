# Phase L1 acceptance

## Automated gates

The launcher workflow must be green, including Windows x64 publishing and WPF smoke. The package-free test executable covers manifest parsing, supported-version/dependency selection, path traversal/URL rejection, missing installation, hash/size success and failure, duplicate file targets, cross-process lock, running-game guard, first install, both official profile formats, original profile/settings preservation, AllBlocks/world/config/other-mod preservation, repeat update without redundant download, new version replacement, downgrade rejection, injected swap failure/rollback, network/corrupt download preservation, manual duplicate-JAR rejection, actual child-process termination and startup recovery, recovery target allowlist, and release selection excluding drafts/stable/other-MC/incomplete releases.

Actual release bundle tests validate all prepared JAR/JSON payloads and install them into a **synthetic** launcher root. Public-release validation repeats this using real GitHub Releases/Maven downloads. Published EXE smoke initializes and renders the real WPF window; after publication it also queries public updates. These are not claims of authenticated game launch.

## Physical Acceptance Pending — Windows 11 x64

Use a legitimate Minecraft account and keep valuable worlds backed up. Do not use a live production world for the prototype.

1. Note the AllBlocks 26.2 launcher profile and its files. Run vanilla **26.3** once in the official Launcher, exit game, then close Launcher.
2. Download `BedDefense-Setup.exe` from the successful dev Release, run it as a normal user, verify the displayed root. Press **설치**. Expected: progress completes, Loader/API/Bed Defense installed state and latest build appear.
3. Press **공식 런처 열기**, select **Bed Defense 26.3**. Launch and create a disposable world. Confirm existing Bed Defense HUD/first-bed prototype loads; check `latest.log` contains the expected Loader/API/mod version and Java 25. Confirm AllBlocks profile/world files remain unchanged.
4. While Minecraft is running, attempt **설치 확인 / 복구**: it must refuse. Also test while official Launcher is open. Close both before retrying.
5. After another successful main build, reopen the same EXE or press **업데이트 조회**; press **업데이트 설치**. Confirm new build number, exactly one `beddefense-managed.jar`, unchanged world/config/other mods, and successful game launch.
6. Disconnect networking before update: error should leave installed version usable. Reconnect and retry. For unusual installations, repeat with a custom root selected using **폴더 선택**.

Still pending until user evidence: actual Store/Win32 Launcher discovery on the user's PC, registered profile shown/selected, Java 25 runtime game boot, in-game mod compatibility, actual later-build update on that PC, custom launcher roots, and Windows Defender/SmartScreen presentation for the unsigned EXE. Do not describe all functionality as physically accepted merely because CI compiles or WPF smoke passes.
