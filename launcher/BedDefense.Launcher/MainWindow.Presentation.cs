using System.Windows;
using System.Windows.Controls;
using BedDefense.Core;

namespace BedDefense.Launcher;
public partial class MainWindow
{
    private bool launchPrimary;
    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);
    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        int enabled = 1;
        // Native Windows caption and window controls, with the supported dark appearance.
        _ = DwmSetWindowAttribute(new System.Windows.Interop.WindowInteropHelper(this).Handle, 20, ref enabled, sizeof(int));
    }
    private void Window_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        if (ReleaseCard is null) return;
        bool narrow = ActualWidth / (AppLayout.LayoutTransform is System.Windows.Media.ScaleTransform scale ? scale.ScaleX : 1) < 900;
        EnvironmentColumn.Width = new GridLength(narrow ? 1 : 1.35, GridUnitType.Star);
        GutterColumn.Width = new GridLength(narrow ? 0 : 20);
        ReleaseColumn.Width = new GridLength(narrow ? 0 : 1, GridUnitType.Star);
        Grid.SetColumn(ReleaseCard, narrow ? 0 : 2);
        Grid.SetRow(ReleaseCard, narrow ? 2 : 1);
        // Error stays above both cards so the disabled-action reason is immediately visible.
        var error = (Border)ContentGrid.Children[2];
        Grid.SetRow(error, 0);
        ReleaseCard.Margin = new Thickness(0, narrow ? 16 : 0, 0, 0);
        AppLayout.Margin = new Thickness(narrow ? 18 : 28, 20, narrow ? 18 : 28, 18);
        InstallButton.MinWidth = narrow ? 120 : 145;
    }
    private void UpdateNotes(string notes)
    {
        // Only actual release bullets are promoted. Legacy git logs stay in details.
        var bullets = notes.Split('\n').Select(l => l.Trim()).Where(l => l.StartsWith("- ") || l.StartsWith("* ")).Take(4).Select(l => "• " + l[2..]);
        NotesSummary.Text = bullets.Any() ? string.Join("\n", bullets) : "이 배포의 상세 변경 내역을 확인해 주세요.";
    }
    private void ShowProgress(InstallProgress progress)
    {
        ProgressBar.IsIndeterminate = progress.DownloadPercent is null && progress.Percent < 100;
        ProgressBar.Value = progress.DownloadPercent ?? (progress.Percent == 100 ? 100 : 0);
        StatusText.Text = progress.Stage + (progress.DownloadPercent is double percentage ? $" · {percentage:0}%" : "");
    }
    private void CopyPath_Click(object sender, RoutedEventArgs e)
    {
        try { Clipboard.SetText(SelectedInstallation().Instance); StatusText.Text = "전체 게임 디렉터리를 복사했습니다."; }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    private void CopyError_Click(object sender, RoutedEventArgs e)
    {
        try { Clipboard.SetText(ErrorText.Text + "\n" + StatusText.Text); }
        catch (Exception error) { StatusText.Text = "복사하지 못했습니다: " + error.Message; }
    }
}
