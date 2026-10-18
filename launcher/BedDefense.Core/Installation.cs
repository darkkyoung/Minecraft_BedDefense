using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace BedDefense.Core;

public sealed class Installation
{
    public string Root { get; }
    public string Instance { get; }
    public string Control { get; }
    public string ProfileKey => "beddefense-managed-26.3";
    public string StatePath => SafePaths.Under(Control, "installed.json");
    public Installation(string root)
    {
        if (!Path.IsPathFullyQualified(root) || root.StartsWith(@"\\")) throw new IOException("로컬 Minecraft 폴더를 선택해 주세요.");
        Root = Path.TrimEndingDirectorySeparator(Path.GetFullPath(root));
        SafePaths.NoLinks(Root);
        Instance = SafePaths.Under(Root, "instances/BedDefense_26.3");
        Control = SafePaths.Under(Instance, ".beddefense");
    }
    public string[] ProfilePaths() => new[] { "launcher_profiles.json", "launcher_profiles_microsoft_store.json" }
        .Select(n => SafePaths.Under(Root, n)).Where(File.Exists).ToArray();
    public void ValidateExistingMinecraft()
    {
        if (!Directory.Exists(Root) || ProfilePaths().Length == 0)
            throw new IOException("공식 Minecraft Launcher 설치 정보를 찾지 못했습니다. 공식 런처를 설치·실행하거나 올바른 Minecraft 폴더를 선택해 주세요.");
        var vanilla = SafePaths.Under(Root, "versions/26.3/26.3.json");
        var jar = SafePaths.Under(Root, "versions/26.3/26.3.jar");
        if (!File.Exists(vanilla) || !File.Exists(jar))
            throw new IOException("공식 런처에서 순정 Minecraft Java 26.3을 한 번 실행하고 종료해 주세요. 게임과 Java 25는 공식 런처가 준비합니다.");
        var doc = JsonNode.Parse(File.ReadAllText(vanilla));
        if (doc?["id"]?.GetValue<string>() != "26.3" || doc?["javaVersion"]?["majorVersion"]?.GetValue<int>() != 25)
            throw new InvalidDataException("Minecraft 26.3 / Java 25 버전 정보가 일치하지 않습니다.");
        foreach (var p in ProfilePaths())
            if (JsonNode.Parse(File.ReadAllText(p))?["profiles"] is not JsonObject)
                throw new InvalidDataException("공식 런처 프로필 파일이 손상되었습니다. 자동으로 덮어쓰지 않습니다.");
    }
    public ReleaseManifest? Installed() => File.Exists(StatePath) ? ReleaseManifest.Parse(File.ReadAllText(StatePath)) : null;
    public string Target(Payload file, ReleaseManifest manifest) => file.Role switch
    {
        "beddefense" => SafePaths.Under(Instance, "mods/beddefense-managed.jar"),
        "fabricApi" => SafePaths.Under(Instance, "mods/fabric-api-managed.jar"),
        "fabricProfile" => SafePaths.Under(Root, $"versions/{manifest.VersionId}/{manifest.VersionId}.json"),
        "library" => SafePaths.Under(Root, "libraries/" + file.LibraryPath),
        _ => throw new InvalidDataException("지원하지 않는 파일")
    };
    public bool Healthy(ReleaseManifest manifest)
    {
        try
        {
            foreach (var f in manifest.Files) Downloads.Verify(Target(f, manifest), f.Size, f.Sha256);
            foreach (var p in ProfilePaths())
            {
                var profile = JsonNode.Parse(File.ReadAllText(p))?["profiles"]?[ProfileKey];
                if (profile?["gameDir"]?.GetValue<string>() != Instance || profile?["lastVersionId"]?.GetValue<string>() != manifest.VersionId) return false;
            }
            return ProfilePaths().Length > 0;
        }
        catch { return false; }
    }
    public byte[] UpdatedProfile(string path, ReleaseManifest manifest)
    {
        var root = JsonNode.Parse(File.ReadAllText(path))?.AsObject() ?? throw new InvalidDataException("프로필 JSON 오류");
        var profiles = root["profiles"]?.AsObject() ?? throw new InvalidDataException("프로필 목록 없음");
        var existing = profiles[ProfileKey];
        if (existing is not null && (existing["gameDir"]?.GetValue<string>() != Instance || existing["name"]?.GetValue<string>() != "Bed Defense 26.3"))
            throw new IOException("Bed Defense 프로필 키가 다른 설치에서 사용 중입니다. 기존 프로필을 덮어쓰지 않습니다.");
        var p = existing?.DeepClone().AsObject() ?? new JsonObject();
        p["name"] = "Bed Defense 26.3"; p["type"] = "custom"; p["gameDir"] = Instance;
        p["lastVersionId"] = manifest.VersionId; p["icon"] = "Red_Bed";
        if (existing is null) p["created"] = DateTimeOffset.UtcNow.ToString("o");
        profiles[ProfileKey] = p;
        return System.Text.Encoding.UTF8.GetBytes(root.ToJsonString(ReleaseManifest.Json));
    }
    public void CheckModConflicts()
    {
        var mods = SafePaths.Under(Instance, "mods");
        if (!Directory.Exists(mods)) return;
        foreach (var path in Directory.EnumerateFiles(mods, "*.jar"))
        {
            SafePaths.NoLinks(path);
            if (Path.GetFileName(path) is "beddefense-managed.jar" or "fabric-api-managed.jar") continue;
            string? id;
            try { id = JarId(path); }
            catch (InvalidDataException) { throw new IOException("읽을 수 없는 모드 JAR을 확인해 주세요: " + Path.GetFileName(path)); }
            if (id is "beddefense" or "fabric-api")
                throw new IOException("중복 모드를 발견했습니다. 이 전용 폴더의 파일을 다른 폴더로 옮겨 주세요: " + path);
        }
    }
    public static string? JarId(string path)
    {
        using var zip = ZipFile.OpenRead(path);
        var entry = zip.GetEntry("fabric.mod.json");
        if (entry is null) return null;
        if (entry.Length > 1024 * 1024) throw new InvalidDataException("모드 메타데이터 크기 초과");
        using var stream = entry.Open();
        using var doc = JsonDocument.Parse(stream);
        return doc.RootElement.GetProperty("id").GetString();
    }
    public static void ValidatePayload(string path, Payload file, ReleaseManifest manifest)
    {
        Downloads.Verify(path, file.Size, file.Sha256);
        if (file.Role is "beddefense" or "fabricApi")
        {
            using var zip = ZipFile.OpenRead(path);
            var entry = zip.GetEntry("fabric.mod.json") ?? throw new InvalidDataException("Fabric 모드 정보 없음");
            if (entry.Length > 1024 * 1024) throw new InvalidDataException("모드 정보 크기 초과");
            using var stream = entry.Open();
            using var doc = JsonDocument.Parse(stream);
            var r = doc.RootElement;
            if (r.GetProperty("id").GetString() != (file.Role == "beddefense" ? "beddefense" : "fabric-api") ||
                r.GetProperty("version").GetString() != (file.Role == "beddefense" ? manifest.ModVersion : manifest.FabricApiVersion))
                throw new InvalidDataException("모드 ID/버전 불일치");
            var mc = r.GetProperty("depends").GetProperty("minecraft").GetRawText();
            if (!mc.Contains("26.3", StringComparison.Ordinal) || mc.Contains("26.2", StringComparison.Ordinal)) throw new InvalidDataException("모드 Minecraft 호환성 오류");
        }
        else if (file.Role == "fabricProfile")
        {
            var root = JsonNode.Parse(File.ReadAllText(path)) ?? throw new InvalidDataException("Fabric 프로필 오류");
            if (root["id"]?.GetValue<string>() != manifest.VersionId || root["inheritsFrom"]?.GetValue<string>() != "26.3" ||
                root["mainClass"]?.GetValue<string>() != "net.fabricmc.loader.impl.launch.knot.KnotClient") throw new InvalidDataException("Fabric 프로필 호환성 오류");
            var libraries = root["libraries"]?.AsArray() ?? throw new InvalidDataException("Fabric 라이브러리 없음");
            var names = new HashSet<string>(StringComparer.Ordinal);
            foreach (var node in libraries)
            {
                var name = node?["name"]?.GetValue<string>() ?? "";
                var parts = name.Split(':');
                if (parts.Length != 3) throw new InvalidDataException("Fabric 라이브러리 좌표 오류");
                var relative = $"{parts[0].Replace('.', '/')}/{parts[1]}/{parts[2]}/{parts[1]}-{parts[2]}.jar";
                if (!names.Add(relative) || !manifest.Files.Any(f => f.Role == "library" && f.LibraryPath == relative) || node?["url"]?.GetValue<string>() != "https://maven.fabricmc.net/")
                    throw new InvalidDataException("Fabric 라이브러리 출처 불일치");
            }
            if (names.Count != manifest.Files.Count(f => f.Role == "library")) throw new InvalidDataException("Fabric 라이브러리 목록 불일치");
        }
        else if (file.Role == "library") { using var zip = ZipFile.OpenRead(path); if (zip.Entries.Count == 0) throw new InvalidDataException("빈 라이브러리"); }
    }
}
