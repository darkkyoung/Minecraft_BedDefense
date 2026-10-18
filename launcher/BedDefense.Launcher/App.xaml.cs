using System.IO;
using System.Windows;
using BedDefense.Core;

namespace BedDefense.Launcher;
public partial class App : Application
{
    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        var smoke = e.Args.Contains("--smoke-test");
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
            window.ShowSmokeManifest(manifest);
            await window.Dispatcher.InvokeAsync(() => window.UpdateLayout(), System.Windows.Threading.DispatcherPriority.Render);
            var bmp = new System.Windows.Media.Imaging.RenderTargetBitmap((int)window.ActualWidth, (int)window.ActualHeight, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
            bmp.Render(window);
            var encoder = new System.Windows.Media.Imaging.PngBitmapEncoder();
            encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bmp));
            using (var output = File.Create("launcher-smoke.png")) encoder.Save(output);
            File.WriteAllText("launcher-smoke.txt", "PASS: WPF initialized, rendered, manifest parsed: " + manifest.Tag);
            Shutdown(0);
        }
        catch (Exception error) { File.WriteAllText("launcher-smoke.txt", error.ToString()); Shutdown(1); }
    }
}
