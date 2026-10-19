using System.Text.Json;
using System.Text.RegularExpressions;

namespace BedDefense.Core;

/// <summary>
/// Updates only the Bed Defense setup executable, never Minecraft profiles or mods.
/// A new verified EXE is stored under LocalAppData instead of replacing a running EXE.
/// </summary>
public sealed record LauncherUpgrade(string Tag, string Sha256);
public sealed record PreferredLauncher(string Tag, string Sha256);

public static class LauncherUpdates
{
    private const string ExecutableName = "BedDefense-Setup.exe";
    private const long MaximumExecutableBytes = 300L * 1024 * 1024;
    private static readonly Regex TagFormat = new(@"^dev-mc26\.3-[0-9]+-[0-9a-f]{12}$", RegexOptions.CultureInvariant);

    private static string Root =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                     "BedDefenseLauncher", "updates");

    private static string PathFor(string tag)
    {
        if (!TagFormat.IsMatch(tag)) throw new InvalidDataException("런처 배포 태그가 잘못되었습니다.");
        return SafePaths.Under(Root, tag + "/" + ExecutableName);
    }

    public static string ParseChecksum(string text)
    {
        var line = text.Trim().TrimStart('\uFEFF');
        var match = Regex.Match(line, @"^([0-9a-fA-F]{64})\s+\*?BedDefense-Setup\.exe$");
        if (!match.Success) throw new InvalidDataException("런처 SHA-256 체크섬 형식이 잘못되었습니다.");
        return match.Groups[1].Value.ToLowerInvariant();
    }

    public static async Task<LauncherUpgrade?> Check(Downloads downloads,
        ReleaseManifest latest, string runningExe, CancellationToken ct = default)
    {
        latest.Validate();
        if (!File.Exists(runningExe)) throw new FileNotFoundException("현재 런처 실행 파일이 없습니다.", runningExe);
        var checksumUrl = $"https://github.com/{ReleaseManifest.Repository}/releases/download/{latest.Tag}/{ExecutableName}.sha256";
        ReleaseManifest.RequireReleaseUrl(NetworkPolicy.Validate(checksumUrl), latest.Tag, ExecutableName + ".sha256");
        var sha = ParseChecksum(await downloads.Text(checksumUrl, ct));
        return string.Equals(Downloads.Hash(runningExe), sha, StringComparison.OrdinalIgnoreCase)
            ? null : new LauncherUpgrade(latest.Tag, sha);
    }

    public static async Task<string> Download(Downloads downloads, LauncherUpgrade upgrade, CancellationToken ct = default)
    {
        var target = PathFor(upgrade.Tag);
        if (!Regex.IsMatch(upgrade.Sha256, "^[0-9a-fA-F]{64}$"))
            throw new InvalidDataException("런처 체크섬 값이 잘못되었습니다.");
        SafePaths.NoLinks(target);
        Directory.CreateDirectory(Path.GetDirectoryName(target)!);

        if (File.Exists(target))
        {
            if (Downloads.Hash(target) != upgrade.Sha256)
                throw new IOException("기존에 저장된 런처 파일의 체크섬이 다릅니다. 임의로 덮어쓰지 않습니다.");
        }
        else
        {
            var url = $"https://api.github.com/repos/{ReleaseManifest.Repository}/releases/tags/{upgrade.Tag}";
            using var doc = JsonDocument.Parse(await downloads.Text(url, ct));
            var root = doc.RootElement;
            if (root.GetProperty("draft").GetBoolean() || !root.GetProperty("prerelease").GetBoolean()
                || root.GetProperty("tag_name").GetString() != upgrade.Tag)
                throw new IOException("검증된 개발용 런처 릴리스가 아닙니다.");
            var assets = root.GetProperty("assets").EnumerateArray()
                .Where(x => x.GetProperty("name").GetString() == ExecutableName && x.GetProperty("state").GetString() == "uploaded")
                .ToArray();
            if (assets.Length != 1) throw new IOException("런처 실행 파일이 한 개가 아닙니다.");
            var asset = assets[0];
            var size = asset.GetProperty("size").GetInt64();
            var uri = NetworkPolicy.Validate(asset.GetProperty("browser_download_url").GetString()!);
            ReleaseManifest.RequireReleaseUrl(uri, upgrade.Tag, ExecutableName);
            if (size < 1024 * 1024 || size > MaximumExecutableBytes)
                throw new IOException("런처 실행 파일 크기가 허용 범위를 벗어났습니다.");
            var temp = target + ".partial";
            if (File.Exists(temp)) File.Delete(temp);
            var file = new Payload("launcher-update", ExecutableName, uri.ToString(), size, upgrade.Sha256);
            try
            {
                await downloads.Download(file, temp, ct: ct);
                File.Move(temp, target);
            }
            catch
            {
                if (File.Exists(temp)) File.Delete(temp);
                throw;
            }
        }

        if (!string.Equals(Downloads.Hash(target), upgrade.Sha256, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("새 런처 해시 검증 실패");
        var current = SafePaths.Under(Root, "current.json");
        Directory.CreateDirectory(Root);
        FileTransaction.AtomicWrite(current,
            JsonSerializer.SerializeToUtf8Bytes(new PreferredLauncher(upgrade.Tag, upgrade.Sha256)));
        return target;
    }

    public static string? CachedSuccessor(string runningExe)
    {
        try
        {
            var marker = SafePaths.Under(Root, "current.json");
            if (!File.Exists(marker)) return null;
            var data = JsonSerializer.Deserialize<PreferredLauncher>(File.ReadAllText(marker));
            if (data is null || !Regex.IsMatch(data.Sha256, "^[0-9a-fA-F]{64}$")) return null;
            var file = PathFor(data.Tag);
            if (!File.Exists(file) || Path.GetFullPath(file).Equals(Path.GetFullPath(runningExe), StringComparison.OrdinalIgnoreCase))
                return null;
            if (!string.Equals(Downloads.Hash(file), data.Sha256, StringComparison.OrdinalIgnoreCase))
                return null;
            if (string.Equals(Downloads.Hash(runningExe), data.Sha256, StringComparison.OrdinalIgnoreCase))
                return null;
            return file;
        }
        catch { return null; }
    }
}
