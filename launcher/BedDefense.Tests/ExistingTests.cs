using System.Diagnostics;
using System.IO.Compression;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Nodes;
using BedDefense.Core;

internal static partial class Program
{
    private static FabricProfile AddProfile(string root, string? game, string key = "my-fabric", string loader = "0.19.5", string file = "launcher_profiles.json")
    {
        var version = "fabric-user-" + loader;
        var versionPath = Path.Combine(root, "versions", version); Directory.CreateDirectory(versionPath);
        File.WriteAllText(Path.Combine(versionPath, version + ".json"), JsonSerializer.Serialize(new { id = version, inheritsFrom = "26.3", mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient", libraries = new[] { new { name = "net.fabricmc:fabric-loader:" + loader } } }));
        var path = Path.Combine(root, file); var node = JsonNode.Parse(File.ReadAllText(path))!;
        var profile = new JsonObject { ["name"] = "내 Fabric 환경", ["lastVersionId"] = version, ["javaArgs"] = "-Xmx4G", ["icon"] = "Grass" };
        if (game is not null) profile["gameDir"] = game;
        node["profiles"]![key] = profile; File.WriteAllText(path, node.ToJsonString());
        return ExistingProfiles.Resolve(root, file, key);
    }
    private static byte[] Mod(string id, string version = "1.0.0", string mc = "~26.3", JsonObject? extra = null, Dictionary<string, byte[]>? nested = null)
    {
        var json = new JsonObject { ["schemaVersion"] = 1, ["id"] = id, ["version"] = version,
            ["depends"] = new JsonObject { ["minecraft"] = mc, ["fabricloader"] = ">=0.19.5", ["java"] = ">=25" } };
        if (extra is not null) foreach (var p in extra) json[p.Key] = p.Value?.DeepClone();
        if (nested is not null) json["jars"] = new JsonArray(nested.Keys.Select(k => (JsonNode)new JsonObject { ["file"] = k }).ToArray());
        using var memory = new MemoryStream();
        using (var zip = new ZipArchive(memory, ZipArchiveMode.Create, true))
        {
            using (var writer = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open())) writer.Write(json.ToJsonString());
            if (nested is not null) foreach (var pair in nested) using (var output = zip.CreateEntry(pair.Key).Open()) output.Write(pair.Value);
        }
        return memory.ToArray();
    }
    private static Dictionary<string, string> Snapshot(IEnumerable<string> paths) => paths.ToDictionary(p => p, Downloads.Hash);
    private static bool Same(Dictionary<string, string> before) => before.All(p => File.Exists(p.Key) && Downloads.Hash(p.Key) == p.Value);
    private static async Task ExistingBundle(Installation isolated, ReleaseManifest manifest, Downloads downloads)
    {
        var root = isolated.Root;
        var game = Path.Combine(root, "user-fabric-game"); Directory.CreateDirectory(Path.Combine(game, "mods"));
        var profile = AddProfile(root, game, "real-bundle-user");
        var api = Path.Combine(game, "mods", "existing-api.jar");
        File.Copy(isolated.Target(manifest.Files.Single(f => f.Role == "fabricApi"), manifest), api, true);
        var before = Snapshot(Directory.EnumerateFiles(isolated.Instance, "*", SearchOption.AllDirectories)
            .Concat(isolated.ProfilePaths()).Concat(Directory.EnumerateFiles(Path.Combine(root, "versions"), "*", SearchOption.AllDirectories)).Append(api));
        var installation = new Installation(root, profile);
        await new Installer(downloads, () => { }).Install(installation, manifest);
        await new Installer(downloads, () => { }).Install(installation, manifest);
        Check(installation.Healthy(manifest), "actual existing-profile bundle install and repeat update");
        Check(Same(before), "actual API, profiles, version JSON and previous independent environment preserved byte-for-byte");
    }
    private static void PrepareUiFixture(string bundle, string root)
    {
        Directory.CreateDirectory(root); CreateMinecraft(root, false); AddProfile(root, null);
        Directory.CreateDirectory(Path.Combine(root, "mods"));
        var manifest = ReleaseManifest.Parse(File.ReadAllText(Path.Combine(bundle, "manifest.json")));
        var index = Array.FindIndex(manifest.Files, f => f.Role == "fabricApi");
        File.Copy(Path.Combine(bundle, "validation-cache", $"payload-{index}.bin"), Path.Combine(root, "mods", "existing-api.jar"));
        File.WriteAllBytes(Path.Combine(root, "mods", "sodium.jar"), Mod("sodium"));
        Check(true, "existing-profile WPF fixture prepared");
    }
    private static async Task ExistingTests()
    {
        var sampleSha = new string('a', 64);
        Check(LauncherUpdates.ParseChecksum(sampleSha + "  BedDefense-Setup.exe") == sampleSha,
              "launcher self-update checksum parser");
        Reject(() => LauncherUpdates.ParseChecksum(sampleSha + "  unrelated.exe"),
               "launcher self-update rejects unrelated checksum files");
        Check(FabricVersions.Matches("26.3", JsonValue.Create("~26.3")), "Fabric tilde current MC");
        Check(!FabricVersions.Matches("26.4", JsonValue.Create("~26.3")), "Fabric tilde rejects next minor");
        Check(FabricVersions.Matches("0.19.6", JsonValue.Create(">=0.19.5 <0.20")), "Fabric comparator conjunction");
        Check(FabricVersions.Matches("26.3", new JsonArray("26.2", "26.3.x")), "Fabric array OR and wildcard");
        Check(!FabricVersions.Matches("0.19.5-beta.1", JsonValue.Create(">=0.19.5")), "prerelease below release");
        Check(FabricVersions.Matches("0.19.6+build", JsonValue.Create("^0.19.5")), "Fabric caret and build metadata");
        Reject(() => FabricVersions.Matches("26.3", JsonValue.Create("26.3 || 26.4")), "unknown predicate fails closed");
        Check(!FabricVersions.Matches("26.4-beta", JsonValue.Create("~26.3")), "tilde excludes next-minor prerelease");
        Check(!FabricVersions.Matches("26.1", JsonValue.Create("~26")), "Fabric tilde missing minor defaults to zero");
        var root = Temp(); var custom = Temp();
        try
        {
            CreateMinecraft(root, false);
            var profile = AddProfile(root, custom);
            var defaults = AddProfile(root, null, "default-root", file: "launcher_profiles_microsoft_store.json");
            Check(defaults.GameDirectory == root, "absent gameDir resolves to Minecraft root");
            var relativeDir = Path.Combine(root, "relative-game"); Directory.CreateDirectory(relativeDir);
            var relative = AddProfile(root, "relative-game", "relative");
            Check(relative.GameDirectory == relativeDir, "relative gameDir resolves against launcher root");
            Check(ExistingProfiles.Discover(root).Count(p => p.Problem is null) == 3, "discover both official profile formats by metadata");
            // Re-resolve after helper overwrites the same version JSON (identical bytes).
            var i = new Installation(root, profile);
            Check(i.Instance == custom && i.TransactionRoot == custom, "custom gameDir outside launcher root supported safely");
            Directory.CreateDirectory(Path.Combine(custom, "mods"));
            Directory.CreateDirectory(Path.Combine(custom, "config")); Directory.CreateDirectory(Path.Combine(custom, "saves", "world"));
            var api = Path.Combine(custom, "mods", "fabric-api-user.jar"); File.WriteAllBytes(api, Mod("fabric-api", "0.164.0+26.3"));
            var sodium = Path.Combine(custom, "mods", "sodium.jar"); File.WriteAllBytes(sodium, Mod("sodium"));
            var iris = Path.Combine(custom, "mods", "iris.jar"); File.WriteAllBytes(iris, Mod("iris", extra: new JsonObject { ["depends"] = new JsonObject { ["minecraft"] = "26.3", ["sodium"] = ">=1.0", ["fabric-api"] = ">=0.163.0" } }));
            var allblocks = Path.Combine(custom, "mods", "allblocks.jar"); File.WriteAllBytes(allblocks, Mod("allblocks"));
            var world = Path.Combine(custom, "saves", "world", "level.dat"); File.WriteAllText(world, "world unchanged");
            var config = Path.Combine(custom, "config", "iris.properties"); File.WriteAllText(config, "keep=true");
            var before = Snapshot(new[] { api, sodium, iris, allblocks, world, config }.Concat(i.ProfilePaths())
                .Concat(Directory.EnumerateFiles(Path.Combine(root, "versions"), "*", SearchOption.AllDirectories)));
            var (m, data) = Fixture(); var handler = new FakeHttp(data); using var downloads = new Downloads(handler);
            var installer = new Installer(downloads, () => { });
            await installer.Install(i, m);
            Check(handler.Requests == 1, "existing install downloads only Bed Defense, never compatible Loader/API");
            Check(i.Healthy(m) && Same(before), "Sodium Iris AllBlocks API worlds settings and both profile files byte-preserved");
            Check(!Directory.Exists(Path.Combine(root, "instances")), "existing mode creates no independent instance");
            await installer.Install(new Installation(root, ExistingProfiles.Resolve(root, profile.FileName, profile.Key)), m);
            Check(handler.Requests == 1, "existing mode repeat install performs no extra downloads");
            var (newer, newerData) = Fixture(11); using var downloads2 = new Downloads(new FakeHttp(newerData));
            await new Installer(downloads2, () => { }).Install(i, newer);
            Check(i.Healthy(newer) && Same(before), "existing mode update preserves every unrelated file");
            Check(Directory.EnumerateFiles(Path.Combine(custom, "mods")).Count(p => Installation.JarId(p) == "beddefense") == 1, "existing update keeps exactly one Bed Defense JAR");
            var oldHash = Downloads.Hash(i.BedDefenseTarget); var source = Path.Combine(root, "replacement.tmp"); File.WriteAllBytes(source, data[m.Files[0].Url]);
            Reject(() => new FileTransaction(i).Apply(newer, [new(i.BedDefenseTarget, source)], afterWrite: _ => throw new IOException("injected")), "existing replacement failure rolls back");
            Check(Downloads.Hash(i.BedDefenseTarget) == oldHash && Same(before), "existing rollback restores JAR and preserves users files");
            foreach (var target in new[] { sodium, api, world, i.ProfilePaths()[0] })
                Reject(() => new FileTransaction(i).Apply(newer, [new(target, source)]), "existing journal rejects non-Bed Defense target: " + Path.GetFileName(target));
            var manifestPath = Path.Combine(root, "crash.json"); File.WriteAllText(manifestPath, JsonSerializer.Serialize(newer, ReleaseManifest.Json));
            var start = new ProcessStartInfo(Environment.ProcessPath!) { UseShellExecute = false };
            if (Path.GetFileNameWithoutExtension(Environment.ProcessPath!).Equals("dotnet", StringComparison.OrdinalIgnoreCase)) start.ArgumentList.Add(Assembly.GetExecutingAssembly().Location);
            foreach (var a in new[] { "--crash-existing", root, manifestPath, source, profile.FileName, profile.Key }) start.ArgumentList.Add(a);
            using (var child = Process.Start(start)!) { await child.WaitForExitAsync(); Check(child.ExitCode == 73, "existing install process actually terminated mid-swap"); }
            Check(new FileTransaction(new Installation(root, profile)).Recover() && Downloads.Hash(i.BedDefenseTarget) == oldHash, "existing external-gameDir restart recovery restores old JAR");
            Check(Same(before), "crash recovery preserves worlds mods API and profile bytes");
            var low = AddProfile(root, custom, "old-loader", "0.18.0");
            await RejectAsync(() => installer.Install(new Installation(root, low), m), "old loader blocks without mutation");
            File.WriteAllBytes(allblocks, Mod("allblocks", mc: "~26.2"));
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "incompatible AllBlocks 26.2 blocks install");
            File.WriteAllBytes(allblocks, Mod("allblocks"));
            // Real Fabric API + Iris/Sodium may carry different versions of one nested
            // dependency. Fabric Loader resolves these candidates; they are not extra
            // top-level copies installed by the player.
            File.WriteAllBytes(sodium, Mod("sodium", nested: new() {
                ["META-INF/jars/base-old.jar"] = Mod("fabric-api-base", "0.4.1+26.3")
            }));
            File.WriteAllBytes(iris, Mod("iris", extra: new JsonObject { ["depends"] =
                new JsonObject { ["minecraft"] = "26.3", ["sodium"] = ">=1.0",
                                  ["fabric-api"] = ">=0.163.0" } }, nested: new() {
                ["META-INF/jars/base-new.jar"] = Mod("fabric-api-base", "0.4.2+26.3")
            }));
            Check(ModCompatibility.Check(i, newer).Summary.Contains("재사용"),
                "different nested fabric-api-base versions are candidates, not duplicate top-level installs");
            // Preserve unrelated files: these remain byte-identical after the update.
            var nestedBefore = Snapshot(new[] { sodium, iris, api });
            await new Installer(downloads2, () => { }).Install(i, newer);
            Check(Same(nestedBefore), "nested candidate fix does not touch Sodium Iris or Fabric API");
            File.WriteAllBytes(sodium, Mod("sodium"));
            File.WriteAllBytes(iris, Mod("iris", extra: new JsonObject { ["depends"] =
                new JsonObject { ["minecraft"] = "26.3", ["sodium"] = ">=1.0",
                                  ["fabric-api"] = ">=0.163.0" } }));
            var compatibleApi = File.ReadAllBytes(api);
            File.WriteAllBytes(api, Mod("fabric-api", "0.162.0+26.2", "~26.2"));
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "incompatible API blocked and never replaced");
            Check(Installation.JarId(api) == "fabric-api" && Downloads.Hash(i.BedDefenseTarget) == oldHash, "API conflict left old install intact");
            File.Delete(api);
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "missing API blocked without auto-install");
            Check(!File.Exists(api), "missing API remains untouched"); File.WriteAllBytes(api, compatibleApi);
            var duplicate = Path.Combine(custom, "mods", "duplicate-api.jar"); File.WriteAllBytes(duplicate, compatibleApi);
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "duplicate API blocked"); File.Delete(duplicate);
            File.WriteAllBytes(duplicate, Mod("beddefense"));
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "duplicate Bed Defense blocked"); File.Delete(duplicate);
            var blocking = Path.Combine(custom, "mods", "conflict.jar");
            File.WriteAllBytes(blocking, Mod("conflict", extra: new JsonObject { ["breaks"] = new JsonObject { ["beddefense"] = "*" } }));
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "declared conflict against incoming Bed Defense blocked"); File.Delete(blocking);
            File.WriteAllBytes(blocking, Mod("host", nested: new() { ["nested/dependency.jar"] = Mod("nested", mc: "~26.2") }));
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "nested incompatible dependency blocked"); File.Delete(blocking);
            var overridePath = Path.Combine(custom, "config", "fabric_loader_dependencies.json"); File.WriteAllText(overridePath, "{}");
            await RejectAsync(() => new Installer(downloads2, () => { }).Install(i, newer), "dependency overrides fail closed"); File.Delete(overridePath);
            var (next, nextData) = Fixture(12); using var offline = new Downloads(new FakeHttp([]));
            await RejectAsync(() => new Installer(offline, () => { }).Install(i, next), "existing offline update leaves previous JAR");
            nextData[next.Files[0].Url] = [1,2,3]; using var corrupt = new Downloads(new FakeHttp(nextData));
            await RejectAsync(() => new Installer(corrupt, () => { }).Install(i, next), "existing corrupt download rejected");
            Check(Downloads.Hash(i.BedDefenseTarget) == oldHash, "failed downloads preserve existing JAR");
            var snapshot = ModCompatibility.Check(i, newer); File.WriteAllBytes(sodium, Mod("sodium", "2.0.0"));
            Reject(() => snapshot.AssertUnchanged(i), "changed mod inventory detected before swap");
            var profilePath = Path.Combine(root, profile.FileName); var saved = File.ReadAllText(profilePath);
            var json = JsonNode.Parse(saved)!; json["profiles"]![profile.Key]!["name"] = "changed"; File.WriteAllText(profilePath, json.ToJsonString());
            Reject(() => ExistingProfiles.Unchanged(root, profile), "concurrent profile change rejected"); File.WriteAllText(profilePath, saved);
            // Two launcher roots pointing at one gameDir share a target lock.
            var root2 = Temp();
            try { CreateMinecraft(root2, false); var other = new Installation(root2, AddProfile(root2, custom));
                using var held = new InstallLock(i); Reject(() => { using var blocked = new InstallLock(other); }, "same gameDir across different roots cannot install concurrently"); }
            finally { Directory.Delete(root2, true); }
            // Adopt an existing manually named Bed Defense, preserving its name and avoiding duplicates.
            var manualRoot = Temp();
            try
            {
                CreateMinecraft(manualRoot, false); var manualProfile = AddProfile(manualRoot, null);
                Directory.CreateDirectory(Path.Combine(manualRoot, "mods"));
                File.WriteAllBytes(Path.Combine(manualRoot, "mods", "api.jar"), compatibleApi);
                var manual = Path.Combine(manualRoot, "mods", "beddefense-old-name.jar"); File.WriteAllBytes(manual, Mod("beddefense", "0.1.0"));
                var manualInstall = new Installation(manualRoot, manualProfile);
                await installer.Install(manualInstall, m);
                Check(manualInstall.BedDefenseTarget == manual && manualInstall.Healthy(m), "manual Bed Defense adopted and replaced at original filename");
                Check(!File.Exists(Path.Combine(manualRoot, "mods", "beddefense-managed.jar")), "adoption creates no duplicate JAR");
                Check(Directory.EnumerateFiles(manualInstall.Control, "*.bak", SearchOption.AllDirectories).Any(), "manual JAR backed up before replacement");
            }
            finally { Directory.Delete(manualRoot, true); }
        }
        finally { Directory.Delete(root, true); Directory.Delete(custom, true); }
    }
}
