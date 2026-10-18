# BED DEFENSE LAUNCHER — Phase L1

Windows 11 x64 · .NET 10 WPF · 사용자별 설치 · 관리자 권한 불필요.
이 프로그램은 **공식 Minecraft Launcher의 설치 도우미**입니다. 계정 로그인, 게임 실행 엔진, 유료 게임/Java 재배포, 런처 자체 자동 업데이트를 구현하지 않습니다.

## 사용자 안내

1. [공식 Minecraft Launcher](https://www.minecraft.net/download)를 설치하고 로그인합니다. **순정 Java 26.3을 한 번 실행한 뒤 게임과 공식 런처를 모두 종료**합니다. 이 단계에서 게임과 Java 25를 공식 런처가 준비합니다.
2. [Releases](https://github.com/darkkyoung/Minecraft_BedDefense/releases)에서 가장 최근 `dev-mc26.3-…` 배포의 **BedDefense-Setup.exe 하나**를 다운로드합니다. 별도 .NET 설치는 필요 없습니다. 개발 빌드 EXE는 아직 Authenticode 서명되지 않았습니다. 출처와 함께 제공하는 SHA-256을 확인하십시오.
3. EXE 실행 → 표시된 Minecraft 폴더 확인 → **설치**. 다른 드라이브의 설치는 `launcher_profiles.json` 또는 `launcher_profiles_microsoft_store.json`이 있는 폴더를 선택합니다. `mods`나 개별 인스턴스 폴더가 아닙니다.
4. **공식 런처 열기** → 공식 런처에서 **Bed Defense 26.3** 선택 → 플레이. 설치 목록에서 모드 적용 프로필 표시가 켜져 있어야 합니다. 지원되는 공식 프로필 자동 선택 API가 없어 자동 선택을 가장하지 않습니다.
5. 이후 같은 EXE 실행 → 현재/최신 버전·변경 내역 확인 → **업데이트 설치**. 이미 실행한 설치기는 **업데이트 조회**로 새 배포를 확인합니다. 업데이트할 때마다 EXE를 다시 받을 필요가 없습니다.

현재 실제 지원 버전은 **26.3 하나**, 채널은 **dev**입니다. 지원 Loader/API 조합이 바뀌는 향후 단계에서는 런처도 수동 업데이트해야 합니다. `stable`은 스키마에 채널을 분리해 확장할 수 있지만 UI에 제공하지 않습니다.

## 기존 올블록과의 분리

기본 전용 폴더는 `%APPDATA%\.minecraft\instances\BedDefense_26.3`입니다. 다른 루트를 선택하면 그 아래 `instances\BedDefense_26.3`을 사용합니다.

- `mods`, `config`, `saves`는 이 인스턴스에서만 사용합니다.
- 고정 관리 파일: `mods\beddefense-managed.jar`, `mods\fabric-api-managed.jar`.
- Fabric 버전 ID와 프로필 키도 `beddefense-…` 전용입니다.
- 기존 AllBlocks 프로필, 선택된 프로필, 다른 모드, 월드, 설정은 보존합니다.
- 공용 `libraries`에 공식 Fabric 의존성이 없으면 추가합니다. 이미 같은 체크섬이면 재사용하고, 다른 내용이면 덮어쓰지 않고 중단합니다.
- 외부에서 수동 복사한 Bed Defense/Fabric API가 같은 전용 mods 폴더에 있으면 충돌을 안내합니다. 자동 삭제하지 않습니다.

## 오류와 복구

| 안내 | 조치 |
| --- | --- |
| 공식 런처/26.3 없음 | 공식 런처 설치·로그인 후 순정 26.3을 한 번 실행하십시오. 임의의 게임 파일은 생성하지 않습니다. |
| 비표준 경로 | 폴더 선택으로 실제 런처 데이터 루트를 지정하십시오. 공식 EXE를 못 찾으면 `공식 런처 열기`에서 직접 선택하십시오. |
| Minecraft 실행 중 | 게임과 공식 런처를 닫으십시오. 보수적 보호를 위해 다른 Java/javaw 프로그램도 닫아야 합니다. 프로세스를 강제 종료하지 않습니다. |
| 연결 오류/API 제한 | 기존 버전은 유지됩니다. 연결을 복구하거나 잠시 기다린 뒤 조회하십시오. 개인 토큰을 넣지 마십시오. |
| 파일 크기/SHA-256 오류 | 적용 전 중단합니다. 다시 시도하되 반복되면 해당 배포를 관리자에게 알리십시오. |
| 권한 오류/정션 | 일반 사용자에게 쓰기 권한이 있는 실제 로컬 폴더를 사용하십시오. 관리자 권한을 요구하거나 정션을 따라가지 않습니다. |
| 교체 실패 | 기존 파일로 자동 롤백합니다. 백업은 `.beddefense\backups\<작업 ID>`에 남깁니다. |
| 강제 종료/전원 종료 | 게임을 실행하지 말고 EXE를 다시 여십시오. `업데이트 조회`에서 남은 기록을 검사하고 복구합니다. 게임 실행 중에는 먼저 닫으라는 안내가 나옵니다. |
| 복구 보류/외부 변경 발견 | 게임과 런처를 종료하고 다시 시도하십시오. `.beddefense`를 삭제하지 마십시오. 백업·기록을 보존하고 문제를 보고하십시오. 나중에 바뀐 사용자 파일을 자동 덮어쓰지 않습니다. |
| 설치 파일 누락 | 최신 버전을 조회하고 `설치 확인 / 복구`를 누르십시오. 단, 공유 라이브러리 내용 충돌은 자동 복구하지 않습니다. |

백업에는 공식 프로필 JSON도 포함됩니다. 계정 관련 로컬 정보가 포함될 수 있으므로 **백업 전체를 공개 이슈에 올리지 마십시오**. 오류 문구와 배포 태그만 먼저 제공하십시오. 성공한 작업의 백업 및 강제 종료로 남은 staging 폴더는 자동 정리하지 않습니다. 설치/복구가 모두 끝났고 `transaction.json`이 없을 때만 필요에 따라 과거 백업을 정리하십시오.

## 개발 구조

- `BedDefense.Core`: 매니페스트/출처/경로 검증, 제한된 HTTP 다운로드, SHA-256, 릴리스 선택, Fabric/JAR 검사, 설치 및 트랜잭션 복구. 외부 NuGet 패키지 없음.
- `BedDefense.Launcher`: 한국어 WPF UI, Windows 앱 탐색, 프로세스 보호, 단일 EXE.
- `BedDefense.Tests`: 패키지 없는 자동 테스트 실행기. 실제 프로세스 종료 후 복구 포함.
- `scripts/prepare_release.py`: Gradle 출력과 공식 Fabric 메타데이터를 검증해 공개 배포를 준비.
- `.github/workflows/launcher.yml`: Linux 모드 빌드 → Windows 테스트/EXE 게시/UI 실행 → main에서만 공개 배포.

로컬 개발 (.NET 10 SDK):

```powershell
dotnet run --project launcher/BedDefense.Tests -c Release
dotnet publish launcher/BedDefense.Launcher -c Release -r win-x64 --self-contained true -p:DebugType=None -p:DebugSymbols=false -o publish
```

CI와 배포 계약, 검증 경계는 [배포 파이프라인](../docs/LAUNCHER_PIPELINE.md), [수동 승인](../docs/LAUNCHER_ACCEPTANCE.md)을 확인하십시오.
