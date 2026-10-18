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

1. [공식 Minecraft Launcher](https://www.minecraft.net/download)에서 순정 **Java 26.3을 한 번 실행**한 뒤 게임과 런처를 모두 종료합니다.
2. EXE를 열고 Minecraft 폴더를 확인한 뒤 **설치**를 누릅니다. 사용자 지정 경로는 **폴더 선택**으로 지정합니다.
3. **공식 런처 열기** → **Bed Defense 26.3** 프로필 선택 → 플레이합니다. 공식 런처에서 프로필 선택은 직접 해 주셔야 합니다.
4. 이후 같은 EXE에서 **업데이트 조회 → 업데이트 설치**를 누르면 최신 검증된 개발 JAR이 적용됩니다.

지원 버전 선택은 현재 **26.3만** 제공됩니다. Loader **0.19.5**, Fabric API **0.163.0+26.3**, Java **25** 환경입니다. 게임과 Java는 공식 런처가 관리합니다.

전용 폴더는 `<Minecraft 폴더>\instances\BedDefense_26.3`이며, **AllBlocks 26.2 및 다른 프로필의 모드·월드·설정을 변경하지 않습니다.** 설치 실패는 자동 롤백하고, 강제 종료된 작업은 EXE 재실행 시 복구합니다. 오류가 나면 게임/런처를 종료한 뒤 다시 조회하십시오. 자동 복구가 보류되면 `.beddefense` 기록과 백업을 지우지 마십시오.

개발 EXE는 아직 코드 서명되지 않았습니다. 공식 저장소의 배포 및 SHA-256을 확인하십시오. 런처 자체 업데이트는 추후 단계이며 필요한 경우 새 EXE를 수동 다운로드합니다.

[상세 설치·오류 복구 안내](launcher/README.md) · [런처 구조와 배포 파이프라인](docs/LAUNCHER_PIPELINE.md) · [Windows 실기기 검증 절차](docs/LAUNCHER_ACCEPTANCE.md)

## In-game prototype test

After installing the development build:

1. In a disposable world, observe compact yellow bar at top right and `스폰 침대` label.
2. Place a regular bed and right-click it (even in daytime). Check registration message, green particles and full green bar.
3. Exit/rejoin world: registration should persist. Check preview with `/bdhud preview` four times.

Build success is not in-game acceptance; please send screenshot and errors if issues arise.

See [HUD testing details](docs/HUD.md), [game design](docs/GAME_DESIGN.md), [testing](docs/TESTING.md).
