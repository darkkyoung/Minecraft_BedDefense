using System.Text.Json;

namespace BedDefense.Core;

public sealed record InstallProgress(string Stage, double Percent);
public sealed class Installer(Downloads downloads, Action assertIdle)
{
    public bool Recover(Installation installation)
    {
        installation.ValidateExistingMinecraft();
        assertIdle();
        using var operation = new InstallLock(installation);
        return new FileTransaction(installation).Recover();
    }
    public async Task Install(Installation installation, ReleaseManifest manifest, IProgress<InstallProgress>? progress = null, CancellationToken ct = default)
    {
        manifest.Validate(); installation.ValidateExistingMinecraft(); assertIdle();
        using var operation = new InstallLock(installation);
        var transaction = new FileTransaction(installation);
        transaction.Recover();
        var old = installation.Installed();
        if (old is not null && old.BuildNumber > manifest.BuildNumber) throw new IOException("설치된 버전보다 오래된 배포입니다. 자동 다운그레이드는 하지 않습니다.");
        installation.CheckModConflicts();
        foreach (var f in manifest.Files.Where(f => f.Role is "beddefense" or "fabricApi" or "fabricProfile"))
            if (old is null && File.Exists(installation.Target(f, manifest))) throw new IOException("이 설치기가 소유하지 않은 대상 파일이 있습니다: " + installation.Target(f, manifest));
        Directory.CreateDirectory(installation.Control);
        var stage = SafePaths.Under(installation.Control, "staging/" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(stage);
        try
        {
            var changes = new List<FileChange>();
            for (var i = 0; i < manifest.Files.Length; i++)
            {
                ct.ThrowIfCancellationRequested();
                var file = manifest.Files[i];
                var target = installation.Target(file, manifest);
                var index = i;
                progress?.Report(new("파일 확인: " + file.Name, 5 + 70.0 * index / manifest.Files.Length));
                if (File.Exists(target) && new FileInfo(target).Length == file.Size && Downloads.Hash(target).Equals(file.Sha256, StringComparison.OrdinalIgnoreCase))
                { Installation.ValidatePayload(target, file, manifest); continue; }
                if (file.Role == "library" && File.Exists(target)) throw new IOException("공유 Fabric 라이브러리의 내용이 다릅니다. 다른 프로필 보호를 위해 덮어쓰지 않습니다: " + target);
                var temp = SafePaths.Under(stage, $"payload-{i}.tmp");
                await downloads.Download(file, temp, new InlineProgress<double>(fraction => progress?.Report(new("다운로드: " + file.Name, 5 + 70.0 * (index + fraction) / manifest.Files.Length))), ct);
                Installation.ValidatePayload(temp, file, manifest);
                changes.Add(new(target, temp, RequireAbsent: file.Role == "library" || old is null));
            }
            ct.ThrowIfCancellationRequested(); assertIdle(); installation.CheckModConflicts();
            progress?.Report(new("프로필 백업 및 등록 준비", 80));
            var profileIndex = 0;
            foreach (var path in installation.ProfilePaths())
            {
                var staged = SafePaths.Under(stage, $"profile-{profileIndex++}.json");
                var expected = Downloads.Hash(path);
                File.WriteAllBytes(staged, installation.UpdatedProfile(path, manifest));
                if (Downloads.Hash(path) != expected) throw new IOException("공식 런처 프로필이 변경되었습니다. 런처를 종료하고 다시 시도해 주세요.");
                changes.Add(new(path, staged, expected));
            }
            var state = SafePaths.Under(stage, "installed.json");
            File.WriteAllText(state, JsonSerializer.Serialize(manifest, ReleaseManifest.Json));
            changes.Add(new(installation.StatePath, state)); // State commits last, in the same recovery journal.
            Directory.CreateDirectory(SafePaths.Under(installation.Instance, "mods"));
            Directory.CreateDirectory(SafePaths.Under(installation.Instance, "config"));
            Directory.CreateDirectory(SafePaths.Under(installation.Instance, "saves"));
            progress?.Report(new("검증 완료 · 파일 적용 중 (종료하지 마세요)", 90));
            transaction.Apply(manifest, changes, assertIdle);
            progress?.Report(new("설치 완료 · 공식 런처에서 Bed Defense 26.3을 선택해 주세요.", 100));
        }
        finally
        {
            // Only this invocation's random staging directory. Never enumerate/delete game data.
            if (Directory.Exists(stage)) { SafePaths.NoLinks(stage); Directory.Delete(stage, true); }
        }
    }
}
public sealed class InlineProgress<T>(Action<T> action) : IProgress<T> { public void Report(T value) => action(value); }
