# Launcher L1 — release and transaction contract

## Source and scope

Started at canonical main `97b07e0178813fa8a232c7a71d82e20e7971d12c`. The gameplay/HUD/raid source is unchanged.
Dependencies match `gradle.properties`: MC 26.3, Loader 0.19.5, API 0.163.0+26.3, Java 25. Launcher: .NET 10 WPF, Windows 11 x64; no third-party NuGet dependencies.

## Fabric integration decision

Official references checked:

- https://wiki.fabricmc.net/install — official CLI supports `client -dir <root> -mcversion 26.3 -loader 0.19.5 -noprofile`.
- https://github.com/FabricMC/fabric-installer/blob/master/src/main/java/net/fabricmc/installer/client/ClientInstaller.java — client installation writes the metadata profile and downloads its libraries; official Launcher can also download libraries itself.
- https://github.com/FabricMC/fabric-installer/blob/master/src/main/java/net/fabricmc/installer/client/ClientHandler.java — explicit launcher-type selection and no-profile behavior.
- https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/profile/json — exact official client metadata.
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.163.0+26.3/ — exact official API distribution.
- https://learn.microsoft.com/dotnet/core/deploying/single-file/overview — self-contained publishing and native extraction.

L1 **does not execute the Fabric installer JAR**. It implements the equivalent metadata/libraries installation using the official endpoints. CI validates inheritance, entry class, exact dependency coordinates, official Maven host, Maven SHA-1 sidecars and JAR metadata; clients verify every payload against the release's SHA-256/length. Only the profile `id` is changed to a Bed Defense-specific namespace. This avoids executing a network-provided installer and avoids modifying an existing Fabric version used by AllBlocks. Minecraft binaries and runtime are neither downloaded by this application nor redistributed; require the official vanilla 26.3 first run. Java 25 requirement is checked from the installed vanilla version metadata; actual runtime launch is physical acceptance.

## Release protocol

`dev-mc26.3-<workflow run number>-<12-character commit SHA>` identifies an immutable release. The manifest contains schemaVersion, channel, Minecraft/Loader/API/mod versions, full commit SHA, build number, tag, notes, and payload records (role/name/HTTPS URL/size/SHA-256; libraryPath only for libraries). The mod's Gradle version may be unchanged across development commits; **buildNumber and commit identify updates**.

1. `mod-package`: existing Gradle build on Java 25. `prepare_release.py` reads the built JAR's embedded mod version and validates every official dependency before preparing assets.
2. `windows`: run safety/recovery tests; install the actual prepared bundle into a synthetic official-launcher directory; publish win-x64 self-contained single-file; run that actual EXE to initialize/render WPF and parse the prepared manifest. Screenshot and result log are retained as CI evidence.
3. `publish-dev`: **main only**, requires both jobs. Uses only job-scoped `contents: write` with Actions `GITHUB_TOKEN`. Create draft, upload complete assets, then publish prerelease. Never overwrite an existing release on rerun. To retry a failed draft, inspect/delete that draft manually or dispatch a new run/build number.
4. Public Windows validation fetches Release API + published manifest + actual payloads, performs fixture install and repeat update, and launches the published EXE's real API-query smoke path. A failed public validation hides the release as a draft. The pre-publication tests remain the prerequisite to any visibility.

Assets: `BedDefense-Setup.exe`, EXE SHA-256 sidecar, `beddefense-*.jar`, `fabric-profile.json`, `manifest.json`, `SHA256SUMS.txt`. Fabric API/libraries stay at official Maven URLs, not repackaged game files. Temporary Actions artifacts are only CI job transport and diagnostic evidence, never client download sources.

Client lists up to 1,000 recent releases, filters public complete dev/26.3 releases by exact tag grammar, and selects the greatest build number rather than `/releases/latest` (which excludes prereleases). A corrupt/incompatible newest manifest fails closed; it does not silently claim an older release is current. No automatic downgrade. A future larger catalog should introduce a version/channel index or pagination policy before reaching this bound. A future stable channel needs explicit UI and selection policy; unknown dependency versions/schema fail closed and require a manually updated launcher.

## Transaction and isolation

- User-selected launcher root must already contain recognized profile JSON and the official vanilla 26.3 JSON/JAR. No fabricated game installation.
- Instance is always `<root>/instances/BedDefense_26.3`; no arbitrary remote destination.
- Reject rooted relative paths, traversal, NTFS stream syntax, reserved names, unsafe URLs, symlinks/junctions/reparse points in the path chain.
- Single root-level exclusive file handle prevents concurrent operations across processes; UI disables actions during work.
- Conservative Java/Minecraft process guard before download and before every apply step. No privileged WMI, forced termination, or account/session access.
- Download to random staging under the instance; check length, SHA-256, ZIP identity/version/MC constraint and profile dependency correspondence before writes.
- New shared library files are create-only; checksum-identical files are reused; conflicting files are preserved and block install.
- Both official profile formats are patched only at the owned profile key. Other nodes and selected profile remain semantically identical. Profile snapshots are checked again to detect concurrent edits. No undocumented launch/profile-selection flags.
- Durable backup + prepared journal is written before the first swap. Each individual replacement uses a flushed temporary file and same-directory atomic rename. Installed state is last in the transaction. A committed marker separates complete install from interrupted apply.
- Exceptions trigger rollback. On startup, uncommitted journals roll back after validating their target allowlist and backups. Later external edits cause recovery to stop, preserving both files and evidence. Backups are retained after success/rollback.
- Fixed mod filenames prevent update-created duplicates. Manual duplicate mod IDs are detected and reported without deleting user files.

No program can interlock with a game process launched by the user after a process check; close game/launcher throughout the operation. Atomic rename plus journal recovery protects common interruption cases, but power loss/disk corruption can still require preserved backups and manual assistance. Tests cover process death, not physical power-failure filesystem durability. State is local user data, not a signed trust boundary against another process already running as that user. Release hashes are anchored to the trusted repository/HTTPS channel; no detached signature or Authenticode certificate is provided in L1.

## Self update / future versions

The portable EXE does not replace itself. Updating mods uses the same EXE while dependency policy remains supported. New Minecraft versions require a matching mod branch, verified dependency policy, separate instance key/directory, updated release matcher, and acceptance. Never substitute another MC version's files. Branch CI validates but never publishes dev updates. The unchanged original mod workflow continues to build main/mc branches independently.
