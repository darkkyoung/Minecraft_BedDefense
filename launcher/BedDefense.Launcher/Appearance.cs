using System.IO;
using System.Text.Json;
using System.Windows;
using BedDefense.Core;

namespace BedDefense.Launcher;

// Presentation preference only: deliberately separate from installation/profile settings.
internal static class Appearance
{
    internal static string DefaultPath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BedDefenseLauncher", "appearance.json");
    private sealed record Preference(string Theme);
    internal static string Read(string path)
    {
        try { return JsonSerializer.Deserialize<Preference>(File.ReadAllText(path))?.Theme == "Light" ? "Light" : "Dark"; }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or JsonException) { return "Dark"; }
    }
    internal static void Save(string path, string theme) => FileTransaction.AtomicWrite(path, JsonSerializer.SerializeToUtf8Bytes(new Preference(theme == "Light" ? "Light" : "Dark")));
    internal static void Apply(string theme)
    {
        var dictionaries = Application.Current.Resources.MergedDictionaries;
        var palette = new ResourceDictionary { Source = new Uri($"Themes/{(theme == "Light" ? "Light" : "Dark")}Theme.xaml", UriKind.Relative) };
        dictionaries[0] = palette;
    }
}
