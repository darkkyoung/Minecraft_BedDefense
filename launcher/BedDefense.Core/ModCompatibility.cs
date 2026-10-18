using System.IO.Compression;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace BedDefense.Core;

// Conservative metadata preflight, not a replacement for Fabric's full dependency solver.
public static class FabricVersions
{
    public static bool Matches(string version, JsonNode? requirement)
    {
        if (requirement is JsonArray alternatives) return alternatives.Select(x => Matches(version, x)).ToArray().Any(x => x);
        if (requirement is not JsonValue value || !value.TryGetValue<string>(out var range) || string.IsNullOrWhiteSpace(range))
            throw new IOException("해석할 수 없는 모드 버전 조건입니다.");
        return range.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(token => Term(version, token)).ToArray().All(x => x);
    }
    private sealed record Sem(long[] Parts, string? Pre);
    private static Sem Parse(string text)
    {
        var m = Regex.Match(text, @"^(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]*))?(?:\+[^\s]+)?$");
        if (!m.Success) throw new IOException("자동 판정할 수 없는 버전입니다: " + text);
        return new(m.Groups[1].Value.Split('.').Select(long.Parse).ToArray(), m.Groups[2].Success ? m.Groups[2].Value : null);
    }
    private static int Compare(Sem a, Sem b)
    {
        for (var i = 0; i < Math.Max(a.Parts.Length, b.Parts.Length); i++)
        { var c = (i < a.Parts.Length ? a.Parts[i] : 0).CompareTo(i < b.Parts.Length ? b.Parts[i] : 0); if (c != 0) return c; }
        if (a.Pre is null || b.Pre is null) return a.Pre is null ? b.Pre is null ? 0 : 1 : -1;
        var x = a.Pre.Split('.'); var y = b.Pre.Split('.');
        for (var i = 0; i < Math.Min(x.Length, y.Length); i++)
        {
            var nx = long.TryParse(x[i], out var vx); var ny = long.TryParse(y[i], out var vy);
            var c = nx && ny ? vx.CompareTo(vy) : nx != ny ? nx ? -1 : 1 : string.CompareOrdinal(x[i], y[i]);
            if (c != 0) return c;
        }
        return x.Length.CompareTo(y.Length);
    }
    public static int CompareVersions(string a, string b) => Compare(Parse(a), Parse(b));

    private static bool Term(string version, string token)
    {
        if (token is "*" or "x" or "X") return true;
        var m = Regex.Match(token, @"^(>=|<=|>|<|=|~|\^)?(.+)$");
        var op = m.Groups[1].Value; var wanted = m.Groups[2].Value;
        if ((op is "" or "=") && version == wanted) return true;
        if (Regex.IsMatch(wanted, @"^\d+(?:\.\d+)*\.[xX*](?:\.[xX*])*$"))
        {
            if (op is not ("" or "=")) throw new IOException("지원하지 않는 와일드카드 버전 조건: " + token);
            var prefix = wanted.Split('.').TakeWhile(p => p is not ("x" or "X" or "*")).Select(long.Parse).ToArray();
            var actual = Parse(version);
            return prefix.Select((n, i) => n == (i < actual.Parts.Length ? actual.Parts[i] : 0)).All(x => x);
        }
        var v = Parse(version); var w = Parse(wanted); var c = Compare(v, w);
        if (op is "~" or "^")
        {
            // Fabric checks equal major/minor components, including prerelease boundaries.
            return c >= 0 && v.Parts[0] == w.Parts[0] && (op == "^" ||
                (v.Parts.Length > 1 ? v.Parts[1] : 0) == (w.Parts.Length > 1 ? w.Parts[1] : 0));
        }
        return op switch { "" or "=" => c == 0, ">=" => c >= 0, "<=" => c <= 0, ">" => c > 0, "<" => c < 0, _ => throw new IOException("지원하지 않는 버전 조건: " + token) };
    }
}

public sealed record ModInfo(string Id, string Version, JsonObject Metadata, string Source, bool Nested);
public sealed record CompatibilityReport(string Summary, IReadOnlyDictionary<string, string> OtherHashes)
{
    public void AssertUnchanged(Installation installation)
    {
        ExistingProfiles.Unchanged(installation.Root, installation.ExistingProfile!);
        var now = ModCompatibility.OtherHashes(installation);
        if (now.Count != OtherHashes.Count || OtherHashes.Any(p => !now.TryGetValue(p.Key, out var hash) || hash != p.Value))
            throw new IOException("검사 후 다른 모드/의존성 설정이 변경되었습니다. 다시 조회해 주세요.");
    }
}
public static class ModCompatibility
{
    public static List<ModInfo> Read(string path)
    {
        SafePaths.NoLinks(path);
        using var stream = File.OpenRead(path);
        var result = new List<ModInfo>(); long expanded = 0;
        ReadArchive(stream, path, false, 0, result, ref expanded);
        return result;
    }
    private static void ReadArchive(Stream stream, string source, bool nested, int depth, List<ModInfo> output, ref long expanded)
    {
        if (depth > 8 || output.Count > 1000) throw new IOException("중첩 모드 검사 한도를 초과했습니다: " + source);
        using var zip = new ZipArchive(stream, ZipArchiveMode.Read, true);
        var entry = zip.GetEntry("fabric.mod.json");
        if (entry is null) { if (nested) return; throw new IOException("Fabric 모드 정보를 확인할 수 없습니다: " + source); }
        if (entry.Length > 1024 * 1024) throw new IOException("모드 메타데이터 크기 초과");
        using var metadata = entry.Open();
        var node = JsonNode.Parse(metadata)?.AsObject() ?? throw new IOException("모드 정보 오류: " + source);
        var id = node["id"]?.GetValue<string>() ?? throw new IOException("모드 ID 없음: " + source);
        var version = node["version"]?.GetValue<string>() ?? throw new IOException("모드 버전 없음: " + source);
        output.Add(new(id, version, node, source, nested));
        foreach (var child in node["jars"]?.AsArray() ?? [])
        {
            var name = child?["file"]?.GetValue<string>() ?? throw new IOException("중첩 JAR 경로 오류");
            var jar = zip.GetEntry(name) ?? throw new IOException("중첩 JAR 누락: " + name);
            expanded += jar.Length;
            if (jar.Length > 64 * 1024 * 1024 || expanded > 256 * 1024 * 1024) throw new IOException("중첩 JAR 크기 초과");
            using var input = jar.Open(); using var bytes = new MemoryStream(); input.CopyTo(bytes); bytes.Position = 0;
            ReadArchive(bytes, source + "!" + name, true, depth + 1, output, ref expanded);
        }
    }
    public static string[] JarPaths(Installation installation)
    {
        var mods = SafePaths.Under(installation.Instance, "mods");
        if (!Directory.Exists(mods)) return [];
        // Subdirectory loading/config overrides cannot be predicted safely by this MVP.
        if (Directory.EnumerateDirectories(mods).Any()) throw new IOException("mods 하위 폴더가 있어 자동 호환성 판정을 보류합니다. 파일은 변경하지 않습니다.");
        return Directory.EnumerateFiles(mods).Where(p => Path.GetExtension(p).Equals(".jar", StringComparison.OrdinalIgnoreCase)).ToArray();
    }
    public static Dictionary<string, string> OtherHashes(Installation installation)
    {
        var result = JarPaths(installation).Where(p => !p.Equals(installation.BedDefenseTarget, StringComparison.OrdinalIgnoreCase))
            .ToDictionary(p => p, p => { SafePaths.NoLinks(p); return Downloads.Hash(p); }, StringComparer.OrdinalIgnoreCase);
        var overrides = SafePaths.Under(installation.Instance, "config/fabric_loader_dependencies.json");
        if (File.Exists(overrides)) throw new IOException("Fabric 의존성 강제 재정의 파일이 있어 자동 설치를 보류합니다: " + overrides);
        return result;
    }
    public static CompatibilityReport Check(Installation installation, ReleaseManifest manifest, string? incoming = null)
    {
        var profile = installation.ExistingProfile ?? throw new IOException("기존 프로필을 선택해 주세요.");
        ExistingProfiles.Unchanged(installation.Root, profile);
        if (!FabricVersions.Matches(profile.LoaderVersion, JsonValue.Create(">=" + manifest.LoaderVersion)))
            throw new IOException($"Fabric Loader {profile.LoaderVersion}은 Bed Defense 요구 조건 >= {manifest.LoaderVersion}과 맞지 않습니다. 기존 Loader는 변경하지 않았습니다.");
        var all = new List<ModInfo>();
        foreach (var path in JarPaths(installation))
        {
            var mods = Read(path);
            var top = mods[0];
            if (path.Equals(installation.BedDefenseTarget, StringComparison.OrdinalIgnoreCase))
            { if (top.Id != "beddefense") throw new IOException("Bed Defense 대상에 다른 모드가 있습니다. 덮어쓰지 않습니다."); continue; }
            if (mods.Any(m => m.Id == "beddefense")) throw new IOException("Bed Defense 중복/중첩 설치를 발견했습니다: " + path);
            all.AddRange(mods.Where(m => m.Metadata["environment"]?.GetValue<string>() != "server"));
        }
        if (all.Count(m => m.Id == "fabric-api" && !m.Nested) != 1)
            throw new IOException("호환되는 Fabric API JAR이 하나 필요합니다. 누락/중복을 확인해 주세요. 기존 모드는 자동 변경하지 않습니다.");
        var api = all.Single(m => m.Id == "fabric-api" && !m.Nested);
        if (api.Metadata["depends"]?["minecraft"] is null) throw new IOException("Fabric API의 Minecraft 호환 정보를 확인할 수 없습니다.");
        var bed = incoming is null ? new ModInfo("beddefense", manifest.ModVersion,
            new JsonObject { ["depends"] = new JsonObject { ["minecraft"] = "~26.3", ["fabricloader"] = ">=" + manifest.LoaderVersion, ["java"] = ">=25", ["fabric-api"] = "*" } }, "새 Bed Defense", false) : Read(incoming)[0];
        all.Add(bed);
        var versions = new Dictionary<string, string>(StringComparer.Ordinal) { ["minecraft"] = "26.3", ["java"] = "25", ["fabricloader"] = profile.LoaderVersion };
        var activeMods = new List<ModInfo>();
        foreach (var group in all.GroupBy(m => m.Id))
        {
            // Independent top-level JARs with one mod ID are true duplicates.
            // Different *nested* versions are candidates, not duplicate installations:
            // Fabric Loader itself resolves the best compatible nested dependency.
            var topLevel = group.Where(m => !m.Nested).ToArray();
            if (topLevel.Length > 1 || versions.ContainsKey(group.Key))
                throw new IOException("동일한 모드를 중복 설치했습니다: " + group.Key);
            var candidates = topLevel.Length == 1 ? topLevel : group.ToArray();
            var ordered = candidates.OrderByDescending(m => m.Version,
                Comparer<string>.Create(FabricVersions.CompareVersions)).ToArray();
            ModInfo? selected = null;
            foreach (var candidate in ordered)
            {
                try
                {
                    // Only candidates usable on this MC, Java and Loader can be active.
                    var deps = candidate.Metadata["depends"]?.AsObject();
                    if ((deps?["minecraft"] is not null && !FabricVersions.Matches("26.3", deps["minecraft"])) ||
                        (deps?["java"] is not null && !FabricVersions.Matches("25", deps["java"])) ||
                        (deps?["fabricloader"] is not null && !FabricVersions.Matches(profile.LoaderVersion, deps["fabricloader"])))
                        continue;
                    selected = candidate;
                    break;
                }
                catch (IOException) { /* Try another nested candidate. */ }
            }
            if (selected is null)
                throw new IOException("현재 게임 버전과 호환되는 모듈 후보가 없습니다: " + group.Key);
            versions[group.Key] = selected.Version;
            activeMods.Add(selected);
        }
        // Resolve constraints on the selected candidates, never on unselected nested copies.
        foreach (var mod in activeMods)
            foreach (var alias in mod.Metadata["provides"]?.AsArray() ?? [])
            { var name = alias?.GetValue<string>() ?? ""; if (!versions.TryAdd(name, mod.Version) && versions[name] != mod.Version) throw new IOException("모드 별칭 충돌: " + name); }
        foreach (var mod in activeMods)
        {
            foreach (var dependency in mod.Metadata["depends"]?.AsObject() ?? [])
                if (!versions.TryGetValue(dependency.Key, out var found) || !FabricVersions.Matches(found, dependency.Value))
                    throw new IOException($"호환성 경고: {mod.Id} {mod.Version} → {dependency.Key} {dependency.Value?.ToJsonString()} 필요 (현재 {found ?? "없음"}). 파일은 변경하지 않았습니다.");
            foreach (var kind in new[] { "breaks", "conflicts" })
                foreach (var conflict in mod.Metadata[kind]?.AsObject() ?? [])
                    if (versions.TryGetValue(conflict.Key, out var found) && FabricVersions.Matches(found, conflict.Value))
                        throw new IOException($"호환성 경고: {mod.Id}와 {conflict.Key} {found}의 {kind} 선언을 발견했습니다. 설치를 보류합니다.");
        }
        return new($"Loader {profile.LoaderVersion} · API {api.Version} 재사용 · 모드 선언 검사 통과 (게임 실행 검증 별도)", OtherHashes(installation));
    }
}
