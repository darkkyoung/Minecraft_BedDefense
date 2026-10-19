using System.Text.Json;

namespace BedDefense.Core;

public sealed record InstallProgress(string Stage, double Percent, double? DownloadPercent = null);
public sealed class Installer(Downloads downloads, Action assertIdle)
{
    public bool Recover(Installation installation)
    {
        installation.ValidateExistingMinecraft();
        assertIdle();
        if (installation.IsExisting && File.Exists(SafePaths.Under(installation.Instance, ".beddefense/transaction.json")))
            throw new IOException("이 게임 폴더에 이전 독립 설치의 미완료 기록이 있습니다. 독립 환경 모드에서 먼저 복구해 주세요.");
        using var operation = new InstallLock(installation);
        return new FileTransaction(installation).Recover();
    }
    public async Task Install(Installation installation, ReleaseManifest manifest, IProgress<InstallProgress>? progress = null, CancellationToken ct = default)
    {
        manifest.Validate(); installation.ValidateExistingMinecraft(); assertIdle();
        if (installation.IsExisting && File.Exists(SafePaths.Under(installation.Instance, ".beddefense/transaction.json")))
            throw new IOException("이 게임 폴더에 이전 독립 설치의 미완료 기록이 있습니다. 독립 환경 모드에서 먼저 복구해 주세요.");
        using var operation = new InstallLock(installation);
        var transaction = new FileTransaction(installation);
        transaction.Recover();
        var old = installation.Installed();
        if (old is not null && old.BuildNumber > manifest.BuildNumber) throw new IOException("설치된 버전보다 오래된 배포입니다. 자동 다운그레이드는 하지 않습니다.");
        installation.CheckModConflicts();
        CompatibilityReport? compatibility = installation.IsExisting ? ModCompatibility.Check(installation, manifest) : null;
        var originalBedHash = File.Exists(installation.BedDefenseTarget) ? Downloads.Hash(installation.BedDefenseTarget) : null;
        foreach (var f in manifest.Files.Where(f => f.Role is "beddefense" or "fabricApi" or "fabricProfile"))
            if (!installation.IsExisting && old is null && File.Exists(installation.Target(f, manifest))) throw new IOException("이 설치기가 소유하지 않은 대상 파일이 있습니다: " + installation.Target(f, manifest));
        Directory.CreateDirectory(installation.Control);
        var stage = SafePaths.Under(installation.Control, "staging/" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(stage);
        try
        {
            var changes = new List<FileChange>();
            var files = installation.ManagedFiles(manifest).ToArray();
            for (var i = 0; i < files.Length; i++)
            {
                ct.ThrowIfCancellationRequested();
                var file = files[i];
                var target = installation.Target(file, manifest);
                var index = i;
                progress?.Report(new("파일 확인: " + file.Name, 5 + 70.0 * index / files.Length));
                if (File.Exists(target) && new FileInfo(target).Length == file.Size && Downloads.Hash(target).Equals(file.Sha256, StringComparison.OrdinalIgnoreCase))
                { Installation.ValidatePayload(target, file, manifest);
                    if (installation.IsExisting) ModCompatibility.Check(installation, manifest, target);
                    continue; }
                if (file.Role == "library" && File.Exists(target)) throw new IOException("공유 Fabric 라이브러리의 내용이 다릅니다. 다른 프로필 보호를 위해 덮어쓰지 않습니다: " + target);
                var temp = SafePaths.Under(stage, $"payload-{i}.tmp");
                await downloads.Download(file, temp, new InlineProgress<double>(fraction => progress?.Report(new("다운로드: " + file.Name, 5 + 70.0 * (index + fraction) / files.Length, fraction * 100))), ct);
                progress?.Report(new("파일 무결성 및 호환성 검증 중: " + file.Name, 75));
                Installation.ValidatePayload(temp, file, manifest);
                if (installation.IsExisting) ModCompatibility.Check(installation, manifest, temp);
                changes.Add(new(target, temp,
                    ExpectedOldHash: installation.IsExisting ? originalBedHash : null,
                    RequireAbsent: installation.IsExisting ? originalBedHash is null : file.Role == "library" || old is null));
            }
            ct.ThrowIfCancellationRequested(); assertIdle(); installation.CheckModConflicts();
            progress?.Report(new(installation.IsExisting ? "Bed Defense 백업 및 적용 준비" : "프로필 백업 및 등록 준비", 80));
            var profileIndex = 0;
            foreach (var path in installation.IsExisting ? Array.Empty<string>() : installation.ProfilePaths())
            {
                var staged = SafePaths.Under(stage, $"profile-{profileIndex++}.json");
                var expected = Downloads.Hash(path);
                File.WriteAllBytes(staged, installation.UpdatedProfile(path, manifest));
                if (Downloads.Hash(path) != expected) throw new IOException("공식 런처 프로필이 변경되었습니다. 런처를 종료하고 다시 시도해 주세요.");
                changes.Add(new(path, staged, expected));
            }
            var state = SafePaths.Under(stage, "installed.json");
            File.WriteAllText(state, installation.StateJson(manifest));
            changes.Add(new(installation.StatePath, state)); // State commits last, in the same recovery journal.
            Directory.CreateDirectory(SafePaths.Under(installation.Instance, "mods"));
            if (!installation.IsExisting)
            {
                Directory.CreateDirectory(SafePaths.Under(installation.Instance, "config"));
                Directory.CreateDirectory(SafePaths.Under(installation.Instance, "saves"));
            }
            progress?.Report(new("검증 완료 · 파일 적용 중 (종료하지 마세요)", 90));
            compatibility?.AssertUnchanged(installation);
            transaction.Apply(manifest, changes, () => { assertIdle(); compatibility?.AssertUnchanged(installation); });
            progress?.Report(new("설치 완료 · 공식 런처에서 ‘" + (installation.ExistingProfile?.Name ?? "Bed Defense 26.3") + "’을 선택해 주세요.", 100));
        }
        finally
        {
            // Only this invocation's random staging directory. Never enumerate/delete game data.
            if (Directory.Exists(stage)) { SafePaths.NoLinks(stage); Directory.Delete(stage, true); }
        }
    }
}
public sealed class InlineProgress<T>(Action<T> action) : IProgress<T> { public void Report(T value) => action(value); }
