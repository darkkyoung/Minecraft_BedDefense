using System.IO;
using System.Diagnostics;
using System.Windows;
using BedDefense.Core;

namespace BedDefense.Launcher;
public partial class App : Application
{
    internal static bool CanHandoffTo(string running, string successor)
    {
        // CachedSuccessor still owns path/hash validation. This presentation-startup
        // guard prevents a freshly downloaded EXE from reopening an older cached UI.
        static Version FileVersion(string path)
        {
            var info = FileVersionInfo.GetVersionInfo(path);
            return new Version(info.FileMajorPart, info.FileMinorPart, info.FileBuildPart, info.FilePrivatePart);
        }
        try { return FileVersion(successor) >= FileVersion(running); }
        catch { return false; } // Keep the directly opened, usable launcher available.
    }
    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        var acceptance = e.Args.Contains("--ui-acceptance");
        var smoke = e.Args.Contains("--smoke-test") || acceptance;
        if (!smoke && Environment.ProcessPath is string running)
        {
            var successor = LauncherUpdates.CachedSuccessor(running);
            if (successor is not null && CanHandoffTo(running, successor))
            {
                try
                {
                    Process.Start(new ProcessStartInfo(successor) { UseShellExecute = true });
                    Shutdown(0);
                    return;
                }
                catch { /* Keep old launcher available if hand-off fails. */ }
            }
        }
        var window = new MainWindow(smoke);
        MainWindow = window;
        window.Show();
        if (!smoke) return;
        try
        {
            // Published single EXE must construct and render its real WPF window.
            await window.Dispatcher.InvokeAsync(() => window.UpdateLayout(), System.Windows.Threading.DispatcherPriority.Render);
            using var downloads = new Downloads();
            var fixtureIndex = Array.IndexOf(e.Args, "--manifest");
            ReleaseManifest manifest = fixtureIndex >= 0
                ? ReleaseManifest.Parse(File.ReadAllText(e.Args[fixtureIndex + 1]))
                : await new ReleaseClient(downloads).Latest();
            if (acceptance)
            {
                var rootIndex = Array.IndexOf(e.Args, "--existing-root");
                if (rootIndex < 0 || fixtureIndex < 0) throw new IOException("UI acceptance requires isolated manifest and root fixtures");
                await window.RunVisualAcceptance(manifest, e.Args[rootIndex + 1]);
                var previousIndex = Array.IndexOf(e.Args, "--previous-launcher");
                if (previousIndex >= 0)
                {
                    var previous = e.Args[previousIndex + 1];
                    if (CanHandoffTo(Environment.ProcessPath!, previous) || !CanHandoffTo(previous, Environment.ProcessPath!))
                        throw new IOException("Published old/new launcher handoff version guard failed");
                    File.AppendAllText("ui-acceptance/results.txt", "PASS: actual previous public EXE cannot replace the newer launcher; forward handoff remains allowed\n");
                }
                Shutdown(0); return;
            }
            window.ShowSmokeManifest(manifest);
            var existingIndex = Array.IndexOf(e.Args, "--existing-root");
            if (existingIndex >= 0) window.ShowSmokeExisting(manifest, e.Args[existingIndex + 1]);
            await window.Dispatcher.InvokeAsync(() => window.UpdateLayout(), System.Windows.Threading.DispatcherPriority.Render);
            var bmp = new System.Windows.Media.Imaging.RenderTargetBitmap((int)window.ActualWidth, (int)window.ActualHeight, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
            bmp.Render(window);
            var encoder = new System.Windows.Media.Imaging.PngBitmapEncoder();
            encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bmp));
            using (var output = File.Create("launcher-smoke.png")) encoder.Save(output);
            File.WriteAllText("launcher-smoke.txt", "PASS: WPF initialized, rendered, manifest parsed: " + manifest.Tag + (existingIndex >= 0 ? " · existing profile selection and mode switching verified" : ""));
            Shutdown(0);
        }
        catch (Exception error) { File.WriteAllText("launcher-smoke.txt", error.ToString()); Shutdown(1); }
    }
}
