using System.Windows;
using System.Windows.Controls;
using BedDefense.Core;

namespace BedDefense.Launcher;
public partial class MainWindow
{
    private bool launchPrimary;
    private void Window_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        if (ReleaseCard is null) return;
        bool narrow = ActualWidth < 900;
        EnvironmentColumn.Width = new GridLength(narrow ? 1 : 1.35, GridUnitType.Star);
        GutterColumn.Width = new GridLength(narrow ? 0 : 20);
        ReleaseColumn.Width = new GridLength(narrow ? 0 : 1, GridUnitType.Star);
        Grid.SetColumn(ReleaseCard, narrow ? 0 : 2);
        Grid.SetRow(ReleaseCard, narrow ? 1 : 0);
        // Error follows both cards in either layout.
        var error = (Border)ContentGrid.Children[2];
        if (ContentGrid.RowDefinitions.Count == 2) ContentGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        Grid.SetRow(error, narrow ? 2 : 1);
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
