using System.Text.Json;
using System.Text.RegularExpressions;

namespace BedDefense.Core;

public sealed record FileChange(string Target, string Source, string? ExpectedOldHash = null, bool RequireAbsent = false);
public sealed record JournalEntry(string RelativeTarget, string BackupName, string? OldHash, string NewHash);
public sealed record Journal(string Id, string Phase, ReleaseManifest Manifest, JournalEntry[] Entries);

// One root-level lock serializes shared libraries and both profile formats across processes.
public sealed class InstallLock : IDisposable
{
    private readonly FileStream stream;
    private readonly FileStream? targetStream;
    public InstallLock(Installation installation)
    {
        var path = SafePaths.Under(installation.Root, ".beddefense-install.lock");
        try
        {
            stream = new FileStream(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
            try
            {
                Directory.CreateDirectory(installation.Instance);
                targetStream = new FileStream(SafePaths.Under(installation.Instance, ".beddefense-target.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
            }
            catch { stream.Dispose(); throw; }
        }
        catch (IOException e) { throw new IOException("다른 Bed Defense 설치 작업이 진행 중입니다.", e); }
    }
    public void Dispose() { targetStream?.Dispose(); stream.Dispose(); }
}

public sealed class FileTransaction(Installation installation)
{
    private string JournalPath => SafePaths.Under(installation.Control, "transaction.json");
    private string BackupDir(string id) => SafePaths.Under(installation.Control, "backups/" + id);
    public static void AtomicWrite(string target, byte[] data, bool overwrite = true)
    {
        SafePaths.NoLinks(target);
        Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        var temp = target + ".bd-" + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            using (var stream = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            { stream.Write(data); stream.Flush(true); }
            File.Move(temp, target, overwrite);
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    private HashSet<string> Allowed(ReleaseManifest manifest)
    {
        var allowed = installation.ManagedFiles(manifest).Select(f => installation.Target(f, manifest)).ToHashSet(StringComparer.OrdinalIgnoreCase);
        allowed.Add(installation.StatePath);
        if (!installation.IsExisting) foreach (var p in installation.ProfilePaths()) allowed.Add(p);
        return allowed;
    }
    private Journal ReadJournal()
    {
        var j = JsonSerializer.Deserialize<Journal>(File.ReadAllText(JournalPath), ReleaseManifest.Json) ?? throw new IOException("복구 기록을 읽지 못했습니다.");
        j.Manifest.Validate();
        if (!Regex.IsMatch(j.Id, "^[a-f0-9]{32}$") || j.Phase is not ("prepared" or "committed") || j.Entries.Length > 110)
            throw new IOException("복구 기록 형식 오류");
        var allowed = Allowed(j.Manifest);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var e in j.Entries)
        {
            var target = SafePaths.Under(installation.TransactionRoot, e.RelativeTarget);
            if (!allowed.Contains(target) || !seen.Add(target) || !Regex.IsMatch(e.BackupName, "^[0-9]+\\.bak$") ||
                !Regex.IsMatch(e.NewHash, "^[0-9a-f]{64}$") || (e.OldHash is not null && !Regex.IsMatch(e.OldHash, "^[0-9a-f]{64}$")))
                throw new IOException("안전하지 않은 복구 경로/해시입니다.");
        }
        return j;
    }
    public bool Recover()
    {
        if (!File.Exists(JournalPath)) return false;
        var j = ReadJournal();
        if (j.Phase == "committed") { File.Delete(JournalPath); return false; }
        // Preflight all entries before restoring any: never destroy a later external edit.
        foreach (var e in j.Entries)
        {
            var target = SafePaths.Under(installation.TransactionRoot, e.RelativeTarget);
            var current = File.Exists(target) ? Downloads.Hash(target) : null;
            if (current != e.OldHash && current != e.NewHash)
                throw new IOException("복구 중 외부 변경을 발견했습니다. 백업을 보존했습니다: " + target);
            if (e.OldHash is not null)
            {
                var backup = SafePaths.Under(BackupDir(j.Id), e.BackupName);
                if (!File.Exists(backup) || Downloads.Hash(backup) != e.OldHash) throw new IOException("복구 백업 검증 실패");
            }
        }
        foreach (var e in j.Entries.Reverse())
        {
            var target = SafePaths.Under(installation.TransactionRoot, e.RelativeTarget);
            if (e.OldHash is null) { if (File.Exists(target)) File.Delete(target); }
            else AtomicWrite(target, File.ReadAllBytes(SafePaths.Under(BackupDir(j.Id), e.BackupName)));
        }
        File.Delete(JournalPath);
        return true;
    }
    public void Apply(ReleaseManifest manifest, IReadOnlyList<FileChange> changes, Action? guard = null, Action<int>? afterWrite = null)
    {
        manifest.Validate();
        if (File.Exists(JournalPath)) throw new IOException("먼저 이전 설치 복구를 완료해 주세요.");
        var id = Guid.NewGuid().ToString("N");
        Directory.CreateDirectory(BackupDir(id));
        var allowed = Allowed(manifest);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var entries = new List<JournalEntry>();
        for (var i = 0; i < changes.Count; i++)
        {
            var change = changes[i];
            SafePaths.NoLinks(change.Target); SafePaths.NoLinks(change.Source);
            if (!allowed.Contains(change.Target) || !seen.Add(change.Target)) throw new IOException("허용되지 않은 교체 경로입니다.");
            string? oldHash = null;
            if (File.Exists(change.Target))
            {
                if (change.RequireAbsent) throw new IOException("새 파일 위치에 다른 파일이 생겼습니다. 덮어쓰지 않습니다.");
                oldHash = Downloads.Hash(change.Target);
                var backup = SafePaths.Under(BackupDir(id), $"{i}.bak");
                using (var input = File.OpenRead(change.Target))
                using (var output = new FileStream(backup, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                { input.CopyTo(output); output.Flush(true); }
                if (Downloads.Hash(backup) != oldHash) throw new IOException("설치 중 원본 파일이 변경되었습니다.");
            }
            if (change.ExpectedOldHash is not null && oldHash != change.ExpectedOldHash) throw new IOException("프로필이 준비 중 변경되었습니다. 다시 시도해 주세요.");
            entries.Add(new(Path.GetRelativePath(installation.TransactionRoot, change.Target).Replace('\\', '/'), $"{i}.bak", oldHash, Downloads.Hash(change.Source)));
        }
        var journal = new Journal(id, "prepared", manifest, entries.ToArray());
        AtomicWrite(JournalPath, JsonSerializer.SerializeToUtf8Bytes(journal, ReleaseManifest.Json));
        try
        {
            for (var i = 0; i < changes.Count; i++)
            {
                guard?.Invoke();
                var c = changes[i]; var e = entries[i];
                var current = File.Exists(c.Target) ? Downloads.Hash(c.Target) : null;
                if (current != e.OldHash || Downloads.Hash(c.Source) != e.NewHash) throw new IOException("설치 중 파일이 변경되었습니다.");
                AtomicWrite(c.Target, File.ReadAllBytes(c.Source), e.OldHash is not null);
                if (Downloads.Hash(c.Target) != e.NewHash) throw new IOException("적용 파일 검증 실패");
                afterWrite?.Invoke(i);
            }
            AtomicWrite(JournalPath, JsonSerializer.SerializeToUtf8Bytes(journal with { Phase = "committed" }, ReleaseManifest.Json));
            File.Delete(JournalPath);
        }
        catch (Exception failure)
        {
            try { Recover(); }
            catch (Exception rollback) { throw new AggregateException("설치 실패. 자동 복구가 보류되었습니다. 게임을 실행하지 말고 다시 설치기를 실행해 주세요. 백업은 보존됩니다.", failure, rollback); }
            throw new IOException("설치에 실패하여 이전 파일로 복구했습니다. " + failure.Message, failure);
        }
    }
}
