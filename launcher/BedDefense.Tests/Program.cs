using System.Diagnostics;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using BedDefense.Core;

internal static class Program
{
    private static int passed;
    private static void Check(bool value, string name) { if (!value) throw new Exception("FAIL: " + name); Console.WriteLine("PASS: " + name); passed++; }
    private static void Reject(Action action, string name)
    { try { action(); } catch { Check(true, name); return; } throw new Exception("FAIL (accepted): " + name); }
    private static async Task RejectAsync(Func<Task> action, string name)
    { try { await action(); } catch { Check(true, name); return; } throw new Exception("FAIL (accepted): " + name); }
    public static async Task<int> Main(string[] args)
    {
        try
        {
            if (args.FirstOrDefault() == "--crash")
            {
                var installation = new Installation(args[1]);
                var m = ReleaseManifest.Parse(File.ReadAllText(args[2]));
                new FileTransaction(installation).Apply(m, [new(installation.Target(m.Files[0], m), args[3])], afterWrite: _ => Environment.Exit(73));
                return 2;
            }
            if (args.FirstOrDefault() == "--validate-bundle")
            {
                var m = ReleaseManifest.Parse(File.ReadAllText(Path.Combine(args[1], "manifest.json")));
                for (var i = 0; i < m.Files.Length; i++)
                    Installation.ValidatePayload(Path.Combine(args[1], "validation-cache", $"payload-{i}.bin"), m.Files[i], m);
                Check(true, "actual manifest, mod compatibility, every dependency hash and archive");
                await BundleInstall(args[1], m);
                return 0;
            }
            if (args.FirstOrDefault() == "--live-release")
            {
                using var downloads = new Downloads();
                var m = await new ReleaseClient(downloads).Latest();
                var root = Temp();
                try
                {
                    var installation = CreateMinecraft(root);
                    await new Installer(downloads, () => { }).Install(installation, m);
                    Check(installation.Healthy(m), "public GitHub Release downloaded and installed in isolated Windows fixture");
                    await new Installer(downloads, () => { }).Install(installation, m);
                    Check(installation.Healthy(m), "repeat live update is idempotent");
                    Console.WriteLine("LIVE RELEASE: " + m.Tag);
                }
                finally { Directory.Delete(root, true); }
                return 0;
            }
            await Tests();
            Console.WriteLine($"{passed} assertions passed"); return 0;
        }
        catch (Exception e) { Console.Error.WriteLine(e); return 1; }
    }
    private static string Temp() { var p = Path.Combine(Path.GetTempPath(), "beddefense-tests-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(p); return p; }
    private static Installation CreateMinecraft(string root)
    {
        Directory.CreateDirectory(Path.Combine(root, "versions", "26.3"));
        File.WriteAllText(Path.Combine(root, "versions", "26.3", "26.3.json"), """{"id":"26.3","javaVersion":{"majorVersion":25}}""");
        File.WriteAllText(Path.Combine(root, "versions", "26.3", "26.3.jar"), "test fixture only; not Minecraft");
        foreach (var p in new[] { "launcher_profiles.json", "launcher_profiles_microsoft_store.json" })
            File.WriteAllText(Path.Combine(root, p), """{"profiles":{"allblocks":{"name":"AllBlocks 26.2","gameDir":"D:\\AllBlocks","lastVersionId":"fabric-26.2"}},"selectedProfile":"allblocks","settings":{"keep":true}}""");
        var i = new Installation(root);
        Directory.CreateDirectory(i.Control);
        return i;
    }
    private static byte[] Jar(string id, string version, string mc = "~26.3")
    {
        using var bytes = new MemoryStream();
        using (var zip = new ZipArchive(bytes, ZipArchiveMode.Create, true))
        { using var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open()); writer.Write(JsonSerializer.Serialize(new { id, version, depends = new { minecraft = mc } })); }
        return bytes.ToArray();
    }
    private static (ReleaseManifest Manifest, Dictionary<string, byte[]> Data) Fixture(long build = 10)
    {
        const string sha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        var tag = $"dev-mc26.3-{build}-{sha[..12]}";
        var baseUrl = $"https://github.com/{ReleaseManifest.Repository}/releases/download/{tag}/";
        var map = new Dictionary<string, byte[]>();
        Payload P(string role, string name, string url, byte[] bytes, string? library = null)
        { map[url] = bytes; return new(role, name, url, bytes.Length, Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant(), library); }
        var lib = "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar";
        var profile = Encoding.UTF8.GetBytes("""{"id":"beddefense-fabric-0.19.5-26.3","inheritsFrom":"26.3","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient","libraries":[{"name":"net.fabricmc:fabric-loader:0.19.5","url":"https://maven.fabricmc.net/"}]}""");
        var version = "0.1.0-alpha." + build + "+26.3";
        var files = new[] {
            P("beddefense", "beddefense-test.jar", baseUrl + "beddefense-test.jar", Jar("beddefense", version)),
            P("fabricApi", "fabric-api-0.163.0+26.3.jar", "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.163.0+26.3/fabric-api-0.163.0+26.3.jar", Jar("fabric-api", "0.163.0+26.3")),
            P("fabricProfile", "fabric-profile.json", baseUrl + "fabric-profile.json", profile),
            P("library", "fabric-loader-0.19.5.jar", "https://maven.fabricmc.net/" + lib, Jar("fixture-library", "0.19.5"), lib)
        };
        return (new(1, "dev", "26.3", "0.19.5", "0.163.0+26.3", version, sha, build, tag, "test notes", files), map);
    }
    private sealed class FakeHttp(Dictionary<string, byte[]> data) : HttpMessageHandler
    {
        public int Requests;
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
        {
            Requests++;
            var key = Uri.UnescapeDataString(request.RequestUri!.AbsoluteUri);
            return Task.FromResult(data.TryGetValue(key, out var value)
                ? new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(value) }
                : new HttpResponseMessage(HttpStatusCode.NotFound));
        }
    }
    private static async Task BundleInstall(string path, ReleaseManifest m)
    {
        var data = m.Files.Select((f, i) => (f.Url, Bytes: File.ReadAllBytes(Path.Combine(path, "validation-cache", $"payload-{i}.bin")))).ToDictionary(x => x.Url, x => x.Bytes);
        using var downloads = new Downloads(new FakeHttp(data));
        var root = Temp();
        try
        {
            var i = CreateMinecraft(root);
            await new Installer(downloads, () => { }).Install(i, m);
            Check(i.Healthy(m), "actual release bundle end-to-end fixture install");
        }
        finally { Directory.Delete(root, true); }
    }
    private static async Task Tests()
    {
        var (m, data) = Fixture();
        Check(ReleaseManifest.Parse(JsonSerializer.Serialize(m, ReleaseManifest.Json)).Tag == m.Tag, "manifest parsing");
        Reject(() => (m with { MinecraftVersion = "26.2" }).Validate(), "reject unsupported Minecraft");
        Reject(() => (m with { LoaderVersion = "0.18.0" }).Validate(), "reject dependency mismatch");
        Reject(() => (m with { SchemaVersion = 2 }).Validate(), "reject unknown schema");
        Reject(() => (m with { Files = [.. m.Files, m.Files[0]] }).Validate(), "reject duplicate targets");
        Reject(() => (m with { Files = m.Files.Select((f, i) => i == 0 ? f with { Url = f.Url.Replace("darkkyoung", "attacker") } : f).ToArray() }).Validate(), "reject different release owner");
        foreach (var path in new[] { "../x", "C:/Windows/a", "foo\\bar", "foo/../bar", "con.jar", "x:y", "/tmp/a", "foo./x" }) Reject(() => SafePaths.Relative(path), "reject path " + path);
        foreach (var url in new[] { "http://github.com/a", "https://github.com.evil.test/a", "https://user:pass@github.com/a", "https://127.0.0.1/a", "file:///tmp/a" }) Reject(() => NetworkPolicy.Validate(url), "reject URL " + url);
        var root = Temp();
        try
        {
            var noMinecraft = new Installation(root);
            Reject(noMinecraft.ValidateExistingMinecraft, "no launcher does not fabricate game data");
            Check(!Directory.Exists(noMinecraft.Instance), "missing game left untouched");
            var installation = CreateMinecraft(root); installation.ValidateExistingMinecraft();
            var allblocks = Path.Combine(root, "instances", "AllBlocks_26.2", "saves"); Directory.CreateDirectory(allblocks);
            var marker = Path.Combine(allblocks, "level.dat"); File.WriteAllText(marker, "untouched");
            var originalProfile = File.ReadAllText(installation.ProfilePaths()[0]);
            using (var held = new InstallLock(installation)) Reject(() => { using var other = new InstallLock(installation); }, "exclusive duplicate install lock");
            var handler = new FakeHttp(data);
            using var downloads = new Downloads(handler);
            var installer = new Installer(downloads, () => { });
            await RejectAsync(() => new Installer(downloads, () => throw new IOException("game running")).Install(installation, m), "running game blocks before download");
            Check(handler.Requests == 0, "blocked install performed no network work");
            await installer.Install(installation, m);
            Check(installation.Healthy(m), "first install and post-install hashes");
            Check(File.ReadAllText(marker) == "untouched", "AllBlocks directory preserved");
            foreach (var path in installation.ProfilePaths())
            {
                var json = JsonNode.Parse(File.ReadAllText(path))!;
                Check(JsonNode.DeepEquals(json["profiles"]?["allblocks"], JsonNode.Parse(originalProfile)?["profiles"]?["allblocks"]), "other launcher profile preserved: " + Path.GetFileName(path));
                Check(json["selectedProfile"]?.GetValue<string>() == "allblocks" && json["settings"]?["keep"]?.GetValue<bool>() == true, "launcher preferences preserved");
                Check(json["profiles"]?[installation.ProfileKey]?["gameDir"]?.GetValue<string>() == installation.Instance, "isolated gameDir registered");
            }
            var calls = handler.Requests;
            await installer.Install(installation, m);
            Check(handler.Requests == calls && installation.Healthy(m), "same version avoids duplicate download");
            var target = installation.Target(m.Files[0], m);
            Downloads.Verify(target, m.Files[0].Size, m.Files[0].Sha256); Check(true, "SHA-256 succeeds");
            Reject(() => Downloads.Verify(target, m.Files[0].Size, new string('0', 64)), "SHA-256 mismatch rejected");
            Reject(() => Downloads.Verify(target, m.Files[0].Size + 1, m.Files[0].Sha256), "size mismatch rejected");
            var unrelated = Path.Combine(installation.Instance, "mods", "another-mod.jar"); File.WriteAllBytes(unrelated, Jar("other-author", "1.0"));
            var world = Path.Combine(installation.Instance, "saves", "world.dat"); File.WriteAllText(world, "world");
            var config = Path.Combine(installation.Instance, "config", "custom.json"); File.WriteAllText(config, "config");
            var (newer, newData) = Fixture(11);
            using var downloads2 = new Downloads(new FakeHttp(newData));
            await new Installer(downloads2, () => { }).Install(installation, newer);
            Check(installation.Healthy(newer) && installation.Installed()?.BuildNumber == 11, "new build selected and applied");
            Check(Directory.GetFiles(Path.GetDirectoryName(target)!, "beddefense*.jar").Length == 1, "one managed Bed Defense JAR");
            Check(File.Exists(unrelated) && File.ReadAllText(world) == "world" && File.ReadAllText(config) == "config", "other authors, saves and config preserved");
            await RejectAsync(() => installer.Install(installation, m), "downgrade blocked");
            var before = Downloads.Hash(target);
            var source = Path.Combine(root, "replacement.tmp"); File.WriteAllBytes(source, data[m.Files[0].Url]);
            Reject(() => new FileTransaction(installation).Apply(newer, [new(target, source)], afterWrite: _ => throw new IOException("injected failure")), "replacement failure reports rollback");
            Check(Downloads.Hash(target) == before, "rollback restored previous bytes");
            Check(!File.Exists(Path.Combine(installation.Control, "transaction.json")), "rollback journal cleared");
            var apiTarget = installation.Target(newer.Files[1], newer);
            var apiBefore = Downloads.Hash(apiTarget);
            var profileTarget = installation.ProfilePaths()[0];
            var profileBefore = Downloads.Hash(profileTarget);
            Reject(() => new FileTransaction(installation).Apply(newer, [new(target, source), new(apiTarget, source), new(profileTarget, source)], afterWrite: n => { if (n == 2) throw new IOException("failure after profile update"); }), "multi-file apply fails after profile write");
            Check(Downloads.Hash(target) == before && Downloads.Hash(apiTarget) == apiBefore && Downloads.Hash(profileTarget) == profileBefore, "whole transaction restores mod, API and launcher profile");
            Reject(() => new FileTransaction(installation).Apply(newer, [new(target, source, RequireAbsent: true)]), "create-only target refuses an existing file");
            Check(Downloads.Hash(target) == before, "create-only collision preserves bytes");
            Reject(() => new FileTransaction(installation).Apply(newer, [new(profileTarget, source, new string('0', 64))]), "concurrent profile edit detected before swap");
            Check(Downloads.Hash(profileTarget) == profileBefore, "concurrent profile conflict preserves profile");

            var conflict = Path.Combine(installation.Instance, "mods", "manually-installed.jar"); File.WriteAllBytes(conflict, Jar("beddefense", "9.0"));
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(installation, newer), "duplicate mod id blocked without deletion");
            Check(File.Exists(conflict), "manual conflicting JAR preserved"); File.Delete(conflict);
            var (bad, badData) = Fixture(12); badData[bad.Files[0].Url] = [1, 2, 3];
            using var broken = new Downloads(new FakeHttp(badData));
            await RejectAsync(() => new Installer(broken, () => { }).Install(installation, bad), "corrupt download fails before swap");
            Check(Downloads.Hash(target) == before && installation.Installed()?.BuildNumber == 11, "download failure preserves install state and old mod");
            using var offline = new Downloads(new FakeHttp([]));
            await RejectAsync(() => new Installer(offline, () => { }).Install(installation, bad), "network failure retains installation");
            Check(Downloads.Hash(target) == before, "network failure preserves old JAR");
            // Real process termination, not a caught exception, exercises durable recovery.
            var manifestPath = Path.Combine(root, "crash-manifest.json"); File.WriteAllText(manifestPath, JsonSerializer.Serialize(newer, ReleaseManifest.Json));
            var start = new ProcessStartInfo(Environment.ProcessPath!) { UseShellExecute = false };
            if (Path.GetFileNameWithoutExtension(Environment.ProcessPath!).Equals("dotnet", StringComparison.OrdinalIgnoreCase)) start.ArgumentList.Add(Assembly.GetExecutingAssembly().Location);
            foreach (var a in new[] { "--crash", root, manifestPath, source }) start.ArgumentList.Add(a);
            using (var child = Process.Start(start)!) { await child.WaitForExitAsync(); Check(child.ExitCode == 73, "process actually terminated during swap"); }
            Check(Downloads.Hash(target) != before, "crash left uncommitted new bytes");
            Check(new FileTransaction(installation).Recover(), "startup recovers interrupted transaction");
            Check(Downloads.Hash(target) == before, "crash recovery restored previous version");
            Reject(() => new FileTransaction(installation).Apply(newer, [new(marker, source)]), "journal cannot target AllBlocks");
            // GitHub list: newest stable, wrong MC, drafts and incomplete uploads are excluded.
            object Release(string tag, bool draft = false, bool prerelease = true, bool asset = true) => new { tag_name = tag, draft, prerelease, assets = asset ? new[] { new { name = "manifest.json", state = "uploaded", browser_download_url = $"https://github.com/{ReleaseManifest.Repository}/releases/download/{tag}/manifest.json" } } : [] };
            var releaseData = new Dictionary<string, byte[]> {
                [$"https://api.github.com/repos/{ReleaseManifest.Repository}/releases?per_page=100&page=1"] = JsonSerializer.SerializeToUtf8Bytes(new[] { Release(bad.Tag, draft:true), Release(m.Tag), Release(newer.Tag), Release("dev-mc26.2-900-aaaaaaaaaaaa"), Release("stable-mc26.3-999-aaaaaaaaaaaa", prerelease:false), Release("dev-mc26.3-999-aaaaaaaaaaaa", asset:false) }),
                [$"https://github.com/{ReleaseManifest.Repository}/releases/download/{newer.Tag}/manifest.json"] = JsonSerializer.SerializeToUtf8Bytes(newer, ReleaseManifest.Json)
            };
            using var listing = new Downloads(new FakeHttp(releaseData));
            Check((await new ReleaseClient(listing).Latest()).BuildNumber == 11, "select highest complete compatible dev release");
            if (OperatingSystem.IsWindows())
            {
                var outside = Temp();
                var link = Path.Combine(root, "junction");
                try
                {
                    var junction = new ProcessStartInfo("cmd.exe") { UseShellExecute = false, RedirectStandardOutput = true };
                    foreach (var a in new[] { "/c", "mklink", "/J", link, outside }) junction.ArgumentList.Add(a);
                    using var process = Process.Start(junction)!;
                    await process.WaitForExitAsync();
                    Check(process.ExitCode == 0, "Windows junction test fixture created");
                    Reject(() => SafePaths.Under(root, "junction/file.jar"), "Windows junction escape rejected");
                }
                finally { if (Directory.Exists(link)) Directory.Delete(link); Directory.Delete(outside, true); }
            }
            else
            {
                var outside = Temp();
                try { var link = Path.Combine(root, "linked"); Directory.CreateSymbolicLink(link, outside); Reject(() => SafePaths.Under(root, "linked/file.jar"), "symlink escape rejected"); Directory.Delete(link);
                    Directory.CreateSymbolicLink(link, Path.Combine(outside, "missing")); Reject(() => SafePaths.Under(root, "linked/file.jar"), "dangling symlink escape rejected"); File.Delete(link); }
                finally { Directory.Delete(outside, true); }
            }
        }
        finally { Directory.Delete(root, true); }
    }
}
