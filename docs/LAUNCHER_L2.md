# Launcher L2 — Windows UI acceptance

Starting canonical main: `3bd7d7c0e6367f6bd512debe111376bbb8a52ef9`.

## Implementation

The launcher uses shared navy/orange resources in `Themes/Launcher.xaml`. Native
Windows window controls remain; the native caption uses the Windows dark theme.
The environment and release cards sit side by side above 900 logical pixels and
stack below that width. Only the body scrolls. Actions stay outside the scroller.
The window fits the monitor work area and declares PerMonitorV2 awareness.

Existing Fabric remains the initial selection. The actual game directory has
ellipsis, a complete tooltip, and a copy action. Existing handlers still perform
profile discovery, environment checks, installation, recovery and launcher handoff.
A verified current install promotes opening the official launcher to the main
action; invalid environments retain the disabled install action and an explanation.

Release bullets are extracted from actual release notes (up to four). Legacy
unstructured commit logs are available in details, without invented summaries.
Mod version and development build are shown separately. Technical release text
is selectable and scrollable. Error text is at the start of the body and copyable.

The only Core change is an optional `DownloadPercent` presentation field plus a
verification-stage notification. Download percentages are bytes received divided
by the manifest payload size. Other work is indeterminate. Installation targets,
SHA-256 checks, transactions, rollback and conflict policies are unchanged.

`Assets/BedDefense.svg` is original artwork: a bed inside a shield. The ICO includes
16, 24, 32, 48, 64, 128 and 256 pixel frames. `ApplicationIcon`, WPF Window Icon,
and a separate high-resolution brand image are packaged into the single EXE.
No third-party font, game artwork or UI framework is included.

## Automated Windows acceptance

The workflow publishes the real self-contained EXE, then runs it with
`--ui-acceptance --manifest <fixture manifest> --existing-root <isolated fixture>`.
This explicit test mode does not read preferences, discover the user's Minecraft
folder, hand off to a cached launcher, or contact the network. Production startup
is unchanged. The fixture includes real Fabric API metadata and many profiles.

The EXE captures its actual WPF render surface in these states:

- Current installed build; update available; 64% download.
- Compatibility error with installation disabled.
- Open profile popup, including long Korean profile names and scrolling.
- Long expanded release notes; narrow window.
- 125%, 150% and 200% WPF layout scaling.

Assertions cover primary action bounds, operation locks, indeterminate verification,
Home/Down/End keyboard routing, full path clipboard copy, existing-profile default,
gameDir resolution, and independent-mode switching. Windows extracts both large
and small icons from the published EXE, so missing PE icon resources fail CI.
`windows-validation` contains all PNGs, extracted icons and `results.txt`.

The existing safety tests additionally verify actual byte-progress semantics.
Existing bundle tests cover repeat updates, nested Fabric API candidates, retained
profiles and previous independent environments. The public-release job downloads
and hashes the new EXE and runs it again; failure hides the incomplete release.

**Physical Acceptance Pending:** CI uses a Windows Server runner. The scale matrix
uses actual WPF LayoutTransform rendering, not a claim that the runner monitor's
OS DPI changed. Windows 11 monitor DPI at 100/125/150/200%, moving between monitors,
taskbar/Explorer icon caches and physical keyboard/mouse acceptance require a PC.

## Minimal Windows 11 check

1. Download the new `BedDefense-Setup.exe` from the public dev release and open it.
2. Select the existing Fabric 26.3 profile. Confirm the full gameDir via tooltip/copy.
3. Close Minecraft and the official launcher, then install or update. Confirm other
   mod JARs, saves, settings and profile identity remain unchanged.
4. Reopen the official launcher using the primary action. Select that Fabric profile.
5. Check the dropdown using Tab, arrows, Home/End, Enter and Escape. Expand the notes.
6. At 1366×768 and 1920×1080, try Windows display scales 100/125/150/200% and resize.
   The main action should remain visible; long body content can scroll.
7. Check the file, native window and taskbar icons. Existing launchers can also use
   their self-update action to fetch the new EXE.

No Java gameplay or balance files are changed by L2.
