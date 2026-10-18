# BED DEFENSE LAUNCHER — Phase L1 existing Fabric follow-up

Windows 11 x64 · .NET 10 WPF · 사용자별 설치 · 관리자 권한 불필요.
공식 Minecraft Launcher의 설치 도우미이며 계정 인증과 실제 게임 실행은 공식 런처를 사용합니다.

## 기존 Fabric에 설치 — 기본값

1. 기존 Fabric **26.3** 프로필을 공식 런처에서 한 번 실행합니다. 게임과 공식 런처를 종료합니다.
2. [Releases](https://github.com/darkkyoung/Minecraft_BedDefense/releases)에서 최신 `dev-mc26.3-…`의 **BedDefense-Setup.exe**를 받습니다. 별도 .NET은 필요 없습니다. 이전 L1 EXE에는 새 프로필 선택 기능이 없으므로 이번에는 새 EXE를 받으십시오.
3. 공식 Minecraft 데이터 폴더를 확인합니다. 다른 위치는 **폴더 선택**으로 `launcher_profiles.json` 또는 `launcher_profiles_microsoft_store.json`이 있는 루트를 선택합니다.
4. **기존 Fabric에 설치 (기본)**에서 프로필을 선택하고 **실제 게임 폴더**를 확인합니다. 프로필에 `gameDir`가 없으면 Minecraft 루트, 있으면 해당 경로를 사용합니다. 사용자 지정 외부 드라이브, 상대 경로와 Windows 환경 변수도 해석합니다. 네트워크 경로·링크·정션·해석 불가 경로는 거부합니다.
5. 호환성 검사를 통과하면 **설치**를 누릅니다. 설치한 뒤 **공식 런처 열기**에서 선택한 기존 프로필로 플레이합니다. 자동 프로필 선택은 지원하지 않습니다.
6. 이후 **업데이트 조회 → 업데이트 설치**를 누릅니다. 현재/최신 빌드와 변경 내역을 표시하고 새 버전만 적용합니다.

기존 프로필 이름·ID·gameDir·실행 버전과 두 프로필 JSON 파일을 수정하지 않습니다. 신규 프로필을 만들지 않습니다. 같은 gameDir를 쓰는 프로필은 같은 mods를 공유합니다.

## 무엇을 변경하나요?

- 기존 환경에서는 **Bed Defense JAR만** 다운로드·교체합니다. 첫 설치는 `mods/beddefense-managed.jar`, 이미 Bed Defense가 하나 있으면 메타데이터의 ID로 식별해 **그 파일명 그대로** 교체합니다. 파일명이 예전 버전이어도 실제 버전은 화면과 내장 메타데이터 기준입니다.
- 호환되는 Loader 및 API는 재사용합니다. 누락·불일치 시 이유를 안내하고 **변경하지 않습니다**. 필요하면 사용자가 공식 도구로 의존성을 준비한 뒤 다시 조회합니다. 자동 의존성 교체 동의 UI는 제공하지 않습니다.
- Sodium/Iris/AllBlocks 등 다른 JAR, `saves`, `config`는 삭제·덮어쓰기하지 않습니다. JAR 내부 `depends`, `breaks`, `conflicts`, `provides`, 중첩 JAR을 검사합니다. 26.2용 모드가 섞이면 차단합니다.
- 설치 상태·백업·복구 기록은 선택한 게임 폴더의 `.beddefense-existing`에 저장합니다. Minecraft 루트와 대상 게임 폴더의 잠금 파일로 중복 설치를 차단합니다.
- 기존 환경에서 자동 판정할 수 없는 버전 조건, 서로 다른 중첩 의존성 버전, 의존성 재정의 파일, mods 하위 폴더 등은 보수적으로 중단합니다. Fabric 전체 의존성 해석기를 대체하지 않습니다. 충돌 해결을 위해 사용자 파일을 자동 수정하지 않습니다.

## 독립 설치와 전환

**독립 환경에 설치**를 선택하면 기존 L1 방식대로 `<Minecraft 루트>/instances/BedDefense_26.3`에 별도 mods/config/saves, 전용 Fabric 버전과 프로필을 구성합니다. Loader 0.19.5, API 0.163.0+26.3을 사용합니다. 다른 프로필은 보존합니다.

**이전 독립 설치에서 기존 Fabric으로 전환** → 원하는 기존 프로필 선택 → **설치**. 이후 선택을 기억합니다. 이전 폴더·프로필·설정·월드는 삭제/이동하지 않습니다. 기존 월드를 옮기려면 별도로 백업 후 직접 복사해야 합니다. 전환은 최신 모드를 선택한 환경에 설치하는 방식입니다.

## 오류와 복구

| 상황 | 조치 |
| --- | --- |
| 프로필 없음/26.3 없음 | 공식 런처에서 Fabric 26.3을 한 번 실행하거나 데이터 루트를 다시 선택합니다. 독립 설치도 선택할 수 있습니다. |
| Loader/API 누락·불일치 | 표시된 요구 버전을 확인해 사용자께서 준비한 뒤 재조회합니다. 설치기는 기존 의존성을 변경하지 않습니다. |
| 중복/충돌 JAR | 오류에 표시된 파일/모드 ID를 확인합니다. 설치기는 임의 삭제하지 않습니다. |
| 게임 실행 중 | Minecraft, 공식 런처, 다른 java/javaw 프로그램을 닫습니다. 프로세스 강제 종료는 하지 않습니다. |
| 네트워크/크기/SHA-256 실패 | 기존 JAR을 보존합니다. 연결 복구 후 다시 시도합니다. |
| 파일 교체 실패 | 백업으로 자동 롤백합니다. `.beddefense-existing/backups`를 보존합니다. 독립 설치는 `.beddefense/backups`입니다. |
| 설치 중 강제 종료 | 같은 프로필을 선택해 다시 조회합니다. 네트워크 조회 전에 복구합니다. 복구 완료 전 게임을 실행하지 마십시오. |
| 프로필/다른 모드가 검사 후 변경됨 | 파일 적용을 중단합니다. 새 상태를 확인하고 재조회합니다. |
| 복구 보류 | 백업·transaction.json을 지우지 말고 오류와 배포 태그를 알려 주세요. 이후 외부 변경 파일을 자동 덮어쓰지 않습니다. |

백업에 개인 프로필 정보가 포함될 수 있으므로 백업 전체를 공개하지 마십시오. 코드 서명과 런처 자체 업데이트는 후속 단계입니다.

## 개발 구조

- `BedDefense.Core/ExistingProfiles.cs`: 공식 프로필 탐색, 버전 상속·실제 gameDir 해석, 변경 감지.
- `ModCompatibility.cs`: Fabric 버전 조건 및 중첩 JAR 의존성 검사, 다른 파일 변경 감지.
- `Installation`, `Installer`, `FileTransaction`: 설치 방식별 관리 범위, 검증 다운로드, 백업·원자적 교체·복구. 기존 환경의 트랜잭션 허용 범위는 Bed Defense JAR과 상태 파일뿐입니다.
- `BedDefense.Launcher`: 한국어 WPF, 프로필/설치 방식 선택, Windows 앱 탐색, self-contained EXE.
- `BedDefense.Tests`: 기존 테스트 및 외부 gameDir·의존성·보존·강제 종료 복구 테스트.
- `.github/workflows/launcher.yml`: 모드 빌드 → Windows 검증/EXE/UI → main 공개 배포 → 공개 다운로드 검증.

```powershell
dotnet run --project launcher/BedDefense.Tests -c Release
dotnet publish launcher/BedDefense.Launcher -c Release -r win-x64 --self-contained true -p:DebugType=None -p:DebugSymbols=false -o publish
```

[배포 계약](../docs/LAUNCHER_PIPELINE.md) · [실기기 검증](../docs/LAUNCHER_ACCEPTANCE.md)
