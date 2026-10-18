using System.Text.Json;
using System.Text.RegularExpressions;

namespace BedDefense.Core;

public sealed record Payload(string Role, string Name, string Url, long Size, string Sha256, string? LibraryPath = null);
public sealed record ReleaseManifest(int SchemaVersion, string Channel, string MinecraftVersion,
    string LoaderVersion, string FabricApiVersion, string ModVersion, string Commit, long BuildNumber,
    string Tag, string Notes, Payload[] Files)
{
    public const string Repository = "darkkyoung/Minecraft_BedDefense";
    public const string SupportedMinecraft = "26.3";
    public const string SupportedLoader = "0.19.5";
    public const string SupportedApi = "0.163.0+26.3";
    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { WriteIndented = true };
    public string VersionId => $"beddefense-fabric-{LoaderVersion}-{MinecraftVersion}";
    public string DisplayVersion => $"{ModVersion} · dev.{BuildNumber} · {Commit[..7]}";
    public static ReleaseManifest Parse(string text)
    {
        var value = JsonSerializer.Deserialize<ReleaseManifest>(text, Json) ?? throw new InvalidDataException("빈 배포 정보입니다.");
        value.Validate();
        return value;
    }
    public void Validate(string channel = "dev", string minecraft = SupportedMinecraft)
    {
        if (SchemaVersion != 1 || Channel != channel || MinecraftVersion != minecraft ||
            minecraft != SupportedMinecraft || LoaderVersion != SupportedLoader || FabricApiVersion != SupportedApi ||
            BuildNumber < 1 || (Commit is null || !Regex.IsMatch(Commit, "^[0-9a-f]{40}$")) ||
            Tag != $"{Channel}-mc{MinecraftVersion}-{BuildNumber}-{Commit[..12]}" ||
            string.IsNullOrWhiteSpace(ModVersion) || ModVersion.Length > 100 || Notes is null || Notes.Length > 65536)
            throw new InvalidDataException("지원하지 않거나 손상된 배포 정보입니다. 런처 업데이트가 필요할 수 있습니다.");
        if (Files is null || Files.Length is < 4 or > 100 ||
            Files.Count(x => x.Role == "beddefense") != 1 || Files.Count(x => x.Role == "fabricApi") != 1 ||
            Files.Count(x => x.Role == "fabricProfile") != 1 || !Files.Any(x => x.Role == "library"))
            throw new InvalidDataException("필수 배포 파일 구성이 잘못되었습니다.");
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var f in Files)
        {
            SafePaths.FileName(f.Name);
            if (f.Size is <= 0 or > 128 * 1024 * 1024 || !Regex.IsMatch(f.Sha256 ?? "", "^[a-fA-F0-9]{64}$"))
                throw new InvalidDataException("파일 크기 또는 SHA-256이 잘못되었습니다.");
            var u = NetworkPolicy.Validate(f.Url);
            string target;
            switch (f.Role)
            {
                case "beddefense":
                    if (!f.Name.StartsWith("beddefense-", StringComparison.Ordinal) || !f.Name.EndsWith(".jar")) throw new InvalidDataException("모드 파일 이름 오류");
                    RequireReleaseUrl(u, Tag, f.Name); target = "mods/beddefense.jar"; break;
                case "fabricApi":
                    var expected = $"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{FabricApiVersion}/fabric-api-{FabricApiVersion}.jar";
                    if (Uri.UnescapeDataString(u.AbsoluteUri) != expected) throw new InvalidDataException("Fabric API 출처/버전 오류");
                    target = "mods/fabric-api.jar"; break;
                case "fabricProfile":
                    if (f.Name != "fabric-profile.json" || f.Size > 1024 * 1024) throw new InvalidDataException("Fabric 프로필 오류");
                    RequireReleaseUrl(u, Tag, f.Name); target = "profile"; break;
                case "library":
                    SafePaths.Relative(f.LibraryPath ?? "");
                    if (!f.LibraryPath!.EndsWith(".jar") || u.Host != "maven.fabricmc.net" ||
                        Uri.UnescapeDataString(u.AbsolutePath) != "/" + f.LibraryPath ||
                        !(f.LibraryPath.StartsWith("net/fabricmc/") || f.LibraryPath.StartsWith("org/ow2/asm/")))
                        throw new InvalidDataException("허용되지 않는 Fabric 라이브러리입니다.");
                    target = "library/" + f.LibraryPath; break;
                default: throw new InvalidDataException("알 수 없는 파일 역할입니다.");
            }
            if (!paths.Add(target)) throw new InvalidDataException("중복 배포 경로입니다.");
        }
    }
    public static void RequireReleaseUrl(Uri u, string tag, string name)
    {
        if (u.Host != "github.com" || Uri.UnescapeDataString(u.AbsolutePath) != $"/{Repository}/releases/download/{tag}/{name}")
            throw new InvalidDataException("공식 Bed Defense 배포 주소가 아닙니다.");
    }
}

public static class SafePaths
{
    public static void FileName(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || value.Length > 180 || !Regex.IsMatch(value, "^[a-zA-Z0-9.][a-zA-Z0-9._+\\-]*$") ||
            value.EndsWith('.') || value.Contains("..") || Regex.IsMatch(value, "^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\\.|$)", RegexOptions.IgnoreCase))
            throw new InvalidDataException("안전하지 않은 파일 이름입니다.");
    }
    public static void Relative(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || value.Length > 400 || value.Contains('\\') || Path.IsPathRooted(value))
            throw new InvalidDataException("안전하지 않은 상대 경로입니다.");
        foreach (var part in value.Split('/')) FileName(part);
    }
    public static string Under(string root, string relative)
    {
        Relative(relative);
        var full = Path.GetFullPath(Path.Combine(root, relative.Replace('/', Path.DirectorySeparatorChar)));
        var prefix = Path.TrimEndingDirectorySeparator(Path.GetFullPath(root)) + Path.DirectorySeparatorChar;
        if (!full.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("경로 이탈을 차단했습니다.");
        NoLinks(full);
        return full;
    }
    public static void NoLinks(string path)
    {
        var current = Path.GetFullPath(path);
        while (!string.IsNullOrEmpty(current))
        {
            try
            {
                if ((File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException("링크/정션 경로에는 설치할 수 없습니다: " + current);
            }
            catch (FileNotFoundException) { }
            catch (DirectoryNotFoundException) { }
            current = Path.GetDirectoryName(current);
        }
    }
}
