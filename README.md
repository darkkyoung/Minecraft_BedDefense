# Bed Defense

**Minecraft Java 26.3 Fabric technical prototype. This is not a playable 100-day challenge yet.**

## v0.1.0-alpha.4+26.3

- Existing Korean HUD: day, XYZ, elapsed stopwatch and biome, without rectangular backgrounds.
- Upper right: **스폰 침대** label over a short **vanilla boss-bar texture** (approximately 1/4 former bar width).
- First Overworld bed right-click registers the player's first Spawn Bed coordinates even during daytime.
- A registered bed shows small green particles whenever the bed chunk is loaded, including after a world restart.
- **Yellow full bar:** no bed registered or known absent. **Green full:** a bed registered and presumed placed. **Red full:** preview only (actual destruction is not implemented).
- `/bdhud preview` cycles 86%-green, yellow, red and normal, without changing gameplay.
- `/bdhud` toggles HUD; `/beddefense debug cracks` remains a safe cobblestone visual test.
- Raid-warning is visual-only and does not spawn monsters.

**Not implemented:** real Spawn Bed block/item, HP/damage, pickup-and-relocation, respawn/permadeath rules, real raids, siege AI, dragon victory, multiplayer revival.

## Bed Defense Launcher (Windows)

**Windows 11 x64 자동 설치/업데이트 도구**는 [Releases](https://github.com/darkkyoung/Minecraft_BedDefense/releases)의 최신 `dev-mc26.3-…` 배포에서 **BedDefense-Setup.exe** 하나를 받으시면 됩니다. .NET 별도 설치와 매번 Actions ZIP 다운로드가 필요 없습니다.

1. 기존 **Fabric 26.3 프로필**을 공식 Minecraft Launcher에서 한 번 실행하고, 게임과 런처를 모두 종료합니다.
2. EXE를 열고 **기존 Fabric에 설치 (기본)** → 원하는 프로필을 선택합니다. 표시되는 **실제 게임 폴더**를 확인하고 **설치**를 누릅니다. `gameDir`가 없으면 Minecraft 기본 폴더를 사용합니다.
3. **공식 런처 열기** → 방금 선택한 기존 프로필로 플레이합니다. 이름·ID·게임 경로·실행 버전은 변경하지 않으며 신규 프로필도 만들지 않습니다.
4. 이후 같은 EXE에서 **업데이트 조회 → 업데이트 설치**를 누릅니다. 이미 수동 설치한 Bed Defense JAR이 하나 있으면 그 이름을 유지한 채 백업 후 교체합니다.

기존 환경에서는 **Bed Defense JAR만 다운로드·교체**합니다. 호환되는 Fabric Loader/API는 재사용하며, 누락·버전 불일치·중복·선언된 충돌은 구체적인 원인을 표시하고 설치를 중단합니다. Loader/API를 자동 변경하거나 다른 모드를 삭제하지 않습니다. 같은 `gameDir`를 공유하는 다른 프로필에도 해당 모드가 적용되므로 표시 경로를 확인하십시오. 26.2용 AllBlocks 등 호환되지 않는 모드가 같은 폴더에 있으면 설치하지 않습니다.

**독립 환경에 설치**는 선택 옵션으로 유지됩니다. 이때만 `<Minecraft 폴더>\instances\BedDefense_26.3`과 별도 프로필, Loader 0.19.5 / API 0.163.0+26.3을 구성합니다. **이전 독립 설치에서 기존 Fabric으로 전환** 버튼으로 대상을 바꿀 수 있습니다. 이전 환경·프로필·월드는 삭제하거나 이동하지 않습니다. 월드 이동은 자동으로 수행하지 않습니다.

지원 게임은 **26.3/dev**입니다. 기존 환경의 Loader는 Bed Defense의 `>=0.19.5` 조건과 각 모드의 선언을 검사하며, API는 설치된 버전의 Minecraft·Loader·모드 의존성을 검사합니다. Java 25는 공식 런처가 관리합니다. 메타데이터 검사는 실제 게임 구동 검증을 대체하지 않습니다.

개발 EXE는 아직 코드 서명되지 않았습니다. 런처 자체 자동 업데이트는 지원하지 않으므로 **이번 설치 방식 변경을 사용하려면 새 EXE를 한 번 다운로드**하십시오. 이후 모드 업데이트에는 같은 EXE를 사용합니다.

[상세 설치·오류 복구 안내](launcher/README.md) · [런처 구조와 배포 파이프라인](docs/LAUNCHER_PIPELINE.md) · [Windows 실기기 검증 절차](docs/LAUNCHER_ACCEPTANCE.md)

## In-game prototype test

After installing the development build:

1. In a disposable world, observe compact yellow bar at top right and `스폰 침대` label.
2. Place a regular bed and right-click it (even in daytime). Check registration message, green particles and full green bar.
3. Exit/rejoin world: registration should persist. Check preview with `/bdhud preview` four times.

Build success is not in-game acceptance; please send screenshot and errors if issues arise.

See [HUD testing details](docs/HUD.md), [game design](docs/GAME_DESIGN.md), [testing](docs/TESTING.md).
