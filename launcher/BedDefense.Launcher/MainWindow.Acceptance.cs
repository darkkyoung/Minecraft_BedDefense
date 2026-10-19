using System.IO;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Interop;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using BedDefense.Core;

namespace BedDefense.Launcher;
// Explicit --ui-acceptance mode only: isolated fixture, no network or user preferences.
public partial class MainWindow
{
    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern uint ExtractIconEx(string file, int index, [Out] IntPtr[]? large, [Out] IntPtr[]? small, uint count);
    [DllImport("user32.dll")] private static extern bool DestroyIcon(IntPtr icon);
    [StructLayout(LayoutKind.Sequential)] private struct ClientRect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr window, out ClientRect rect);

    internal async Task RunVisualAcceptance(ReleaseManifest manifest, string root)
    {
        Directory.CreateDirectory("ui-acceptance");
        ShowSmokeExisting(manifest, root);
        NotesBox.Text = manifest.Notes; UpdateNotes(manifest.Notes);
        LatestText.Text = manifest.ModVersion;
        var checks = new List<string>();
        Activate(); ProfileBox.Focus();
        int originalProfile = ProfileBox.SelectedIndex;
        void KeyPress(Key key) => ProfileBox.RaiseEvent(new KeyEventArgs(Keyboard.PrimaryDevice, PresentationSource.FromVisual(ProfileBox)!, 0, key) { RoutedEvent = Keyboard.KeyDownEvent });
        KeyPress(Key.Home); KeyPress(Key.Down);
        if (ProfileBox.SelectedIndex != 1) throw new IOException("ComboBox Home/Down keyboard selection failed");
        KeyPress(Key.End);
        if (ProfileBox.SelectedIndex != ProfileBox.Items.Count - 1) throw new IOException("ComboBox End keyboard selection failed");
        ProfileBox.SelectedIndex = originalProfile;
        CopyPath_Click(this, new RoutedEventArgs());
        if (Clipboard.GetText() != SelectedInstallation().Instance) throw new IOException("Full game path copy failed");
        checks.Add("PASS: ComboBox Home/Down/End keyboard routing and full path clipboard copy");
        foreach (var scenario in new[] { "current", "update", "progress", "compatibility-error", "profiles", "long-notes", "narrow", "scale-125", "scale-150", "scale-200" })
        {
            AppLayout.LayoutTransform = Transform.Identity;
            Width = 1060; Height = 780;
            NotesExpander.IsExpanded = false; ProfileBox.IsDropDownOpen = false;
            ErrorText.Text = ""; canInstall = true; launchPrimary = false;
            InstallButton.Content = "업데이트 설치";
            CurrentText.Text = manifest.ModVersion; LatestText.Text = manifest.ModVersion;
            CurrentBuildText.Text = $"dev.{manifest.BuildNumber - 1} · 이전 배포"; LatestBuildText.Text = $"dev.{manifest.BuildNumber} · {manifest.Commit[..7]}";
            InstalledText.Text = "↑ 업데이트 가능"; StatusText.Text = "조회 완료 · 새 버전을 설치할 수 있습니다.";
            ProgressBar.IsIndeterminate = false; ProgressBar.Value = 0;
            SetBusy(false);
            if (scenario == "current")
            {
                CurrentBuildText.Text = LatestBuildText.Text;
                launchPrimary = true; SetBusy(false);
                InstalledText.Text = "✓ 최신 버전 · 파일 검증 완료";
                InstallButton.Content = "공식 Minecraft Launcher 열기 ↗";
                StatusText.Text = "최신 버전이 설치되어 있습니다.";
            }
            if (scenario == "progress")
            {
                SetBusy(true); ShowProgress(new InstallProgress("파일 다운로드 중", 50, 64));
                if (InstallButton.IsEnabled || ModeBox.IsEnabled || ProfileBox.IsEnabled || !StatusText.Text.Contains("64%")) throw new IOException("Progress state/operation lock regression");
                checks.Add("PASS: real byte progress presentation and operation controls locked");
            }
            if (scenario == "compatibility-error")
            {
                canInstall = false; SetBusy(false);
                InstalledText.Text = "! 설치 보류";
                ErrorText.Text = "Fabric API 버전이 선택한 Minecraft 버전과 호환되지 않습니다. 호환되는 Fabric 26.3 프로필을 선택한 뒤 다시 조회해 주세요.\n기존 파일은 변경되지 않았습니다.";
                StatusText.Text = "환경 확인 실패 · 오류 안내를 확인해 주세요.";
                if (InstallButton.IsEnabled) throw new IOException("Incompatible environment install enabled");
                checks.Add("PASS: incompatible profile blocks installation");
            }
            if (scenario == "long-notes")
            {
                NotesBox.Text = string.Join("\n", Enumerable.Repeat(manifest.Notes, 12));
                NotesExpander.IsExpanded = true;
            }
            if (scenario == "narrow") { Width = 640; Height = 610; }
            if (scenario.StartsWith("scale-"))
            {
                // Actual WPF layout/render stress at OS-equivalent scaling, not a claim of changed monitor DPI.
                double scale = int.Parse(scenario[6..]) / 100.0;
                Width = 1280; Height = 900;
                AppLayout.LayoutTransform = new ScaleTransform(scale, scale);
                EnvironmentColumn.Width = new GridLength(1, GridUnitType.Star);
                GutterColumn.Width = new GridLength(0); ReleaseColumn.Width = new GridLength(0);
                System.Windows.Controls.Grid.SetColumn(ReleaseCard, 0); System.Windows.Controls.Grid.SetRow(ReleaseCard, 2);
                System.Windows.Controls.Grid.SetRow(ContentGrid.Children[2], 0);
                ReleaseCard.Margin = new Thickness(0, 16, 0, 0);
            }
            UpdateLayout();
            if (scenario == "profiles") { ProfileBox.SelectedIndex = ProfileBox.Items.Count - 1; ProfileBox.Focus(); ProfileBox.IsDropDownOpen = true; }
            await Dispatcher.InvokeAsync(() => UpdateLayout(), DispatcherPriority.Render);
            await Task.Delay(180); // Let the real popup and indeterminate animation render.
            var button = InstallButton.TransformToAncestor(this).TransformBounds(new Rect(InstallButton.RenderSize));
            if (button.Bottom > ActualHeight || button.Right > ActualWidth || button.Width < 100) throw new IOException("Primary action clipped: " + scenario);
            Capture(scenario);
            checks.Add("PASS: actual WPF " + scenario + "; primary action inside window");
        }
        busy = false;
        ShowProgress(new InstallProgress("파일 무결성 검증 중", 75));
        if (!ProgressBar.IsIndeterminate || StatusText.Text.Contains('%')) throw new IOException("Fake percentage on verification stage");
        checks.Add("PASS: verification is indeterminate, no synthetic percentage");
        var large = new IntPtr[1]; var small = new IntPtr[1];
        var extracted = ExtractIconEx(Environment.ProcessPath!, 0, large, small, 1);
        if (extracted is 0 or uint.MaxValue || large[0] == IntPtr.Zero || small[0] == IntPtr.Zero) throw new IOException($"Published EXE icon resources missing (count={extracted})");
        try
        {
            SaveBitmap(Imaging.CreateBitmapSourceFromHIcon(large[0], Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions()), "ui-acceptance/exe-icon-large.png");
            SaveBitmap(Imaging.CreateBitmapSourceFromHIcon(small[0], Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions()), "ui-acceptance/exe-icon-small.png");
        }
        finally { DestroyIcon(large[0]); DestroyIcon(small[0]); }
        if (Icon is null) throw new IOException("WPF Window icon missing");
        checks.Add("PASS: actual published PE large/small icon extracted; WPF Window icon loaded");
        File.WriteAllLines("ui-acceptance/results.txt", checks);
    }
    private void Capture(string scenario)
    {
        if (!GetClientRect(new WindowInteropHelper(this).Handle, out var client)) throw new IOException("Cannot measure actual client render surface");
        var dpi = VisualTreeHelper.GetDpi(this);
        var bitmap = new RenderTargetBitmap(client.Right, client.Bottom, dpi.PixelsPerInchX, dpi.PixelsPerInchY, PixelFormats.Pbgra32);
        bitmap.Render(this);
        SaveBitmap(bitmap, "ui-acceptance/" + scenario + ".png");
        if (ProfileBox.IsDropDownOpen && ProfileBox.Template.FindName("PART_Popup", ProfileBox) is Popup { Child: FrameworkElement child })
        {
            var popup = new RenderTargetBitmap((int)Math.Ceiling(child.ActualWidth), (int)Math.Ceiling(child.ActualHeight), 96, 96, PixelFormats.Pbgra32);
            popup.Render(child);
            SaveBitmap(popup, "ui-acceptance/profile-popup.png");
        }
    }
    private static void SaveBitmap(BitmapSource bitmap, string path)
    {
        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var stream = File.Create(path); encoder.Save(stream);
    }
}
