using System.Text.Json.Nodes;
using System.Security.Cryptography;
using System.Text;

namespace BedDefense.Core;

public sealed record FabricProfile(string FileName, string Key, string Name, string VersionId, string GameDirectory,
    string LoaderVersion, string Fingerprint, string? Problem = null)
{
    public string Identity => FileName + ":" + Key;
    public string Display => $"{Name} · {VersionId} [{FileName}]" + (Problem is null ? "" : " · 확인 필요");
}

public static class ExistingProfiles
{
    public static IReadOnlyList<FabricProfile> Discover(string root)
    {
        var result = new List<FabricProfile>();
        var installation = new Installation(root);
        foreach (var path in installation.ProfilePaths())
        {
            var profiles = JsonNode.Parse(File.ReadAllText(path))?["profiles"]?.AsObject()
                ?? throw new IOException("공식 런처 프로필 목록을 읽을 수 없습니다.");
            foreach (var pair in profiles)
            {
                if (pair.Value is not JsonObject p) continue;
                var version = p["lastVersionId"]?.GetValue<string>() ?? "";
                // Only metadata proves Fabric; names are used solely to surface broken Fabric profiles.
                try { result.Add(Resolve(root, Path.GetFileName(path), pair.Key)); }
                catch (Exception e) when (e is IOException or System.Text.Json.JsonException or InvalidOperationException)
                {
                    if (version.Contains("fabric", StringComparison.OrdinalIgnoreCase))
                        result.Add(new(Path.GetFileName(path), pair.Key, p["name"]?.GetValue<string>() ?? pair.Key,
                            version, "", "", "", e.Message));
                }
            }
        }
        return result;
    }
    public static FabricProfile Resolve(string root, string fileName, string key)
    {
        if (fileName is not ("launcher_profiles.json" or "launcher_profiles_microsoft_store.json")) throw new IOException("알 수 없는 프로필 파일입니다.");
        var p = JsonNode.Parse(File.ReadAllText(SafePaths.Under(root, fileName)))?["profiles"]?[key]?.AsObject()
            ?? throw new IOException("선택한 프로필이 삭제되었습니다. 다시 선택해 주세요.");
        var version = p["lastVersionId"]?.GetValue<string>() ?? throw new IOException("프로필 실행 버전이 없습니다.");
        var current = version;
        var seen = new HashSet<string>();
        var evidence = new StringBuilder(p.ToJsonString());
        string? main = null, loader = null;
        var minecraft = false;
        for (var depth = 0; depth < 10; depth++)
        {
            SafePaths.FileName(current);
            if (!seen.Add(current)) throw new IOException("실행 버전 상속이 순환합니다.");
            var json = File.ReadAllText(SafePaths.Under(root, $"versions/{current}/{current}.json"));
            evidence.Append(json);
            var node = JsonNode.Parse(json) ?? throw new IOException("실행 버전 정보 오류");
            if (node["id"]?.GetValue<string>() != current) throw new IOException("실행 버전 ID 불일치");
            main ??= node["mainClass"]?.GetValue<string>();
            foreach (var lib in node["libraries"]?.AsArray() ?? [])
            {
                var coordinate = lib?["name"]?.GetValue<string>() ?? "";
                if (!coordinate.StartsWith("net.fabricmc:fabric-loader:", StringComparison.Ordinal)) continue;
                var candidate = coordinate.Split(':');
                if (candidate.Length != 3 || (loader is not null && loader != candidate[2])) throw new IOException("Fabric Loader 버전이 충돌합니다.");
                loader = candidate[2];
            }
            var parent = node["inheritsFrom"]?.GetValue<string>();
            if (parent is null) { minecraft = current == "26.3"; break; }
            current = parent;
        }
        if (main != "net.fabricmc.loader.impl.launch.knot.KnotClient" || loader is null) throw new IOException("Fabric 클라이언트 프로필이 아닙니다.");
        if (!minecraft) throw new IOException("Minecraft 26.3용 Fabric 프로필만 지원합니다.");
        var game = p["gameDir"]?.GetValue<string>();
        var directory = string.IsNullOrWhiteSpace(game) ? root : Environment.ExpandEnvironmentVariables(game);
        if (directory.Contains('%') || directory.Contains("${", StringComparison.Ordinal)) throw new IOException("게임 경로의 환경 변수를 해석할 수 없습니다.");
        directory = Path.TrimEndingDirectorySeparator(Path.GetFullPath(directory, root));
        if (directory.StartsWith(@"\\") || directory == Path.GetPathRoot(directory)) throw new IOException("로컬 게임 폴더를 선택해 주세요.");
        SafePaths.NoLinks(directory);
        if (!Directory.Exists(directory)) throw new IOException("프로필의 게임 폴더가 없습니다. 공식 런처에서 해당 프로필을 한 번 실행해 주세요.");
        return new(fileName, key, p["name"]?.GetValue<string>() ?? key, version, directory, loader,
            Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(evidence.ToString()))));
    }
    public static void Unchanged(string root, FabricProfile selected)
    {
        var now = Resolve(root, selected.FileName, selected.Key);
        if (now.Fingerprint != selected.Fingerprint || now.GameDirectory != selected.GameDirectory)
            throw new IOException("선택한 프로필/실행 버전이 변경되었습니다. 다시 조회해 주세요.");
    }
}
