using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace BedDefense.Core;

public static class NetworkPolicy
{
    public static Uri Validate(string address, bool redirect = false)
    {
        if (!Uri.TryCreate(address, UriKind.Absolute, out var u) || u.Scheme != "https" || !u.IsDefaultPort ||
            u.UserInfo.Length != 0 || u.Fragment.Length != 0 ||
            !(u.Host is "github.com" or "api.github.com" or "maven.fabricmc.net" ||
              (redirect && u.Host is "release-assets.githubusercontent.com" or "objects.githubusercontent.com")))
            throw new InvalidDataException("허용되지 않은 다운로드 주소입니다.");
        if (!redirect && u.Query.Length > 0 && u.Host != "api.github.com") throw new InvalidDataException("잘못된 다운로드 주소입니다.");
        return u;
    }
}

public sealed class Downloads : IDisposable
{
    private readonly HttpClient http;
    public Downloads(HttpMessageHandler? handler = null)
    {
        http = new HttpClient(handler ?? new HttpClientHandler { AllowAutoRedirect = false });
        http.Timeout = TimeSpan.FromMinutes(5);
        http.DefaultRequestHeaders.UserAgent.ParseAdd("BedDefense-Launcher/0.1.0");
    }
    private async Task<HttpResponseMessage> Get(string url, CancellationToken ct)
    {
        var uri = NetworkPolicy.Validate(url);
        for (var i = 0; i < 6; i++)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, uri);
            // Authenticated API reads ONLY for GitHub Actions release acceptance.
            // Do not attach secrets to github.com asset links, Maven, or redirects.
            if (uri.Host == "api.github.com" &&
                Environment.GetEnvironmentVariable("GITHUB_ACTIONS") == "true" &&
                Environment.GetEnvironmentVariable("BEDDEFENSE_CI_GITHUB_TOKEN") is string ciToken &&
                !string.IsNullOrWhiteSpace(ciToken))
            {
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", ciToken);
            }
            var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct);
            if ((int)response.StatusCode is >= 300 and < 400 && response.Headers.Location is not null)
            {
                var next = response.Headers.Location.IsAbsoluteUri ? response.Headers.Location : new Uri(uri, response.Headers.Location);
                response.Dispose();
                uri = NetworkPolicy.Validate(next.AbsoluteUri, true);
                continue;
            }
            if (!response.IsSuccessStatusCode)
            {
                var code = response.StatusCode;
                var reset = response.Headers.TryGetValues("X-RateLimit-Reset", out var values) ? values.FirstOrDefault() : null;
                response.Dispose();
                if (code is HttpStatusCode.Forbidden or HttpStatusCode.TooManyRequests)
                    throw new HttpRequestException($"GitHub 요청 제한 또는 접근 거부입니다. 잠시 후 다시 조회해 주세요. (HTTP {(int)code}, reset: {reset ?? "미제공"})");
                throw new HttpRequestException($"다운로드 실패 (HTTP {(int)code}). 인터넷 연결과 배포 상태를 확인해 주세요.");
            }
            return response;
        }
        throw new HttpRequestException("다운로드 리디렉션 횟수를 초과했습니다.");
    }
    public async Task<string> Text(string url, CancellationToken ct = default)
    {
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(ct);
        deadline.CancelAfter(TimeSpan.FromSeconds(60));
        ct = deadline.Token;
        using var response = await Get(url, ct);
        if (response.Content.Headers.ContentLength > 2 * 1024 * 1024) throw new InvalidDataException("배포 정보가 너무 큽니다.");
        await using var input = await response.Content.ReadAsStreamAsync(ct);
        using var memory = new MemoryStream();
        var buffer = new byte[16384];
        int n;
        while ((n = await input.ReadAsync(buffer, ct)) > 0)
        {
            if (memory.Length + n > 2 * 1024 * 1024) throw new InvalidDataException("배포 정보 크기 초과");
            memory.Write(buffer, 0, n);
        }
        return Encoding.UTF8.GetString(memory.ToArray());
    }
    public async Task Download(Payload file, string destination, IProgress<double>? progress = null, CancellationToken ct = default)
    {
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(ct);
        deadline.CancelAfter(TimeSpan.FromMinutes(3));
        ct = deadline.Token;
        SafePaths.NoLinks(destination);
        using var response = await Get(file.Url, ct);
        if (response.Content.Headers.ContentLength is long length && length != file.Size)
            throw new InvalidDataException("다운로드 크기가 배포 정보와 다릅니다.");
        try
        {
            await using (var output = new FileStream(destination, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            await using (var input = await response.Content.ReadAsStreamAsync(ct))
            {
                var buffer = new byte[65536];
                long total = 0;
                int n;
                while ((n = await input.ReadAsync(buffer, ct)) > 0)
                {
                    total += n;
                    if (total > file.Size) throw new InvalidDataException("다운로드 파일 크기 초과");
                    await output.WriteAsync(buffer.AsMemory(0, n), ct);
                    progress?.Report((double)total / file.Size);
                }
                await output.FlushAsync(ct);
                output.Flush(true);
            }
            Verify(destination, file.Size, file.Sha256);
        }
        catch { if (File.Exists(destination)) File.Delete(destination); throw; }
    }
    public static string Hash(string path)
    {
        using var input = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(input)).ToLowerInvariant();
    }
    public static void Verify(string path, long size, string hash)
    {
        if (!File.Exists(path) || new FileInfo(path).Length != size || !string.Equals(Hash(path), hash, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("파일 크기 또는 SHA-256 검증 실패: " + Path.GetFileName(path));
    }
    public void Dispose() => http.Dispose();
}

public sealed class ReleaseClient(Downloads downloads)
{
    private sealed record Candidate(long Build, string Tag, string ManifestUrl, string? Digest);
    public async Task<ReleaseManifest> Latest(CancellationToken ct = default)
    {
        var candidates = new List<Candidate>();
        for (var page = 1; page <= 10; page++)
        {
            using var doc = JsonDocument.Parse(await downloads.Text($"https://api.github.com/repos/{ReleaseManifest.Repository}/releases?per_page=100&page={page}", ct));
            var releases = doc.RootElement.EnumerateArray().ToArray();
            foreach (var release in releases)
            {
                if (release.GetProperty("draft").GetBoolean() || !release.GetProperty("prerelease").GetBoolean()) continue;
                var tag = release.GetProperty("tag_name").GetString() ?? "";
                var match = Regex.Match(tag, "^dev-mc26\\.3-([0-9]+)-[0-9a-f]{12}$");
                if (!match.Success || !long.TryParse(match.Groups[1].Value, out var build)) continue;
                var assets = release.GetProperty("assets").EnumerateArray().ToArray();
                var manifests = assets.Where(a => a.GetProperty("name").GetString() == "manifest.json" && a.GetProperty("state").GetString() == "uploaded").ToArray();
                if (manifests.Length != 1) continue;
                var asset = manifests[0];
                var url = asset.GetProperty("browser_download_url").GetString()!;
                ReleaseManifest.RequireReleaseUrl(NetworkPolicy.Validate(url), tag, "manifest.json");
                candidates.Add(new(build, tag, url, asset.TryGetProperty("digest", out var digest) ? digest.GetString() : null));
            }
            if (releases.Length < 100) break;
        }
        var latest = candidates.OrderByDescending(c => c.Build).FirstOrDefault()
            ?? throw new InvalidDataException("Minecraft 26.3용 검증된 개발 배포가 아직 없습니다.");
        var json = await downloads.Text(latest.ManifestUrl, ct);
        if (latest.Digest?.StartsWith("sha256:") == true &&
            !string.Equals(latest.Digest[7..], Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(json))), StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("배포 매니페스트 체크섬 오류");
        var manifest = ReleaseManifest.Parse(json);
        if (manifest.Tag != latest.Tag || manifest.BuildNumber != latest.Build) throw new InvalidDataException("배포 태그와 매니페스트가 다릅니다.");
        return manifest;
    }
}
