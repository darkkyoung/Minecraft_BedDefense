using System.Windows;
using System.Windows.Controls;
using BedDefense.Core;

namespace BedDefense.Launcher;
public partial class MainWindow
{
    private bool launchPrimary;
    private string theme = "Dark";
    private void ApplyTheme(string value)
    {
        theme = value == "Light" ? "Light" : "Dark";
        Appearance.Apply(theme);
        ThemeButton.Content = theme == "Dark" ? "☀ 라이트" : "☾ 다크";
        ThemeButton.ToolTip = theme == "Dark" ? "라이트 모드로 전환 (선택을 기억합니다)" : "다크 모드로 전환 (선택을 기억합니다)";
        UpdateCaptionTheme();
    }
    private void Theme_Click(object sender, RoutedEventArgs e)
    {
        ApplyTheme(theme == "Dark" ? "Light" : "Dark");
        if (smoke) return;
        try { Appearance.Save(Appearance.DefaultPath, theme); }
        catch (Exception error) when (error is System.IO.IOException or UnauthorizedAccessException)
        { StatusText.Text = "테마는 적용했지만 선택을 저장하지 못했습니다: " + error.Message; }
    }
    private void UpdateCaptionTheme()
    {
        var handle = new System.Windows.Interop.WindowInteropHelper(this).Handle;
        if (handle == IntPtr.Zero) return;
        int enabled = theme == "Dark" ? 1 : 0;
        _ = DwmSetWindowAttribute(handle, 20, ref enabled, sizeof(int));
    }
    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);
    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        UpdateCaptionTheme();
    }
    private void Window_SizeChanged(object sender, SizeChangedEventArgs e) => UpdateResponsiveLayout();
    private void UpdateResponsiveLayout()
    {
        if (ReleaseCard is null) return;
        bool narrow = ActualWidth / (AppLayout.LayoutTransform is System.Windows.Media.ScaleTransform scale ? scale.ScaleX : 1) < 900;
        EnvironmentColumn.Width = new GridLength(narrow ? 1 : 1.15, GridUnitType.Star);
        GutterColumn.Width = new GridLength(narrow ? 0 : 16);
        ReleaseColumn.Width = new GridLength(narrow ? 0 : 1, GridUnitType.Star);
        Grid.SetColumn(ReleaseCard, narrow ? 0 : 2);
        Grid.SetRow(ReleaseCard, narrow ? 2 : 1);
        // Error stays above both cards so the disabled-action reason is immediately visible.
        Grid.SetRow(ErrorCard, 0);
        ReleaseCard.Margin = new Thickness(0, narrow ? 16 : 0, 0, 0);
        AppLayout.Margin = new Thickness(narrow ? 16 : 24, 18, narrow ? 16 : 24, 16);
        HeroFrame.Height = narrow ? 112 : 160;
    }
    private void ResetQueryPresentation()
    {
        launchPrimary = false;
        InstallButton.Content = "설치";
        InstalledText.Text = "○ 환경 확인 중";
        CurrentText.Text = "확인 중"; CurrentBuildText.Text = "";
        LatestText.Text = "조회 중"; LatestBuildText.Text = "";
        InstanceText.Text = "프로필 확인 후 표시됩니다.";
        FabricText.Text = "선택한 환경의 호환성을 확인합니다.";
        NotesBox.Text = ""; NotesSummary.Text = "배포 정보를 가져오고 있습니다.";
        BodyScroll.ScrollToTop();
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
