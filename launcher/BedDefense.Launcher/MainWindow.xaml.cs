using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Windows;
using BedDefense.Core;
using Microsoft.Win32;

namespace BedDefense.Launcher;
public partial class MainWindow : Window
{
    private readonly Downloads downloads = new();
    private ReleaseManifest? latest;
    private bool busy;
    private bool canInstall;
    private string? launcher;
    private readonly bool smoke;
    private string Settings => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BedDefenseLauncher", "settings.json");
    public MainWindow(bool smoke = false)
    {
        this.smoke = smoke;
        InitializeComponent();
        RootBox.Text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
        if (!smoke)
        {
            try { if (File.Exists(Settings)) RootBox.Text = JsonSerializer.Deserialize<string>(File.ReadAllText(Settings)) ?? RootBox.Text; }
            catch { ErrorText.Text = "저장된 경로를 읽지 못해 기본 경로를 사용합니다."; }
        }
        Loaded += async (_, _) => { if (!smoke) { launcher = WindowsEnvironment.FindLauncher(); await Refresh(); } };
        Closing += OnClosing;
        Closed += (_, _) => downloads.Dispose();
    }
    private void OnClosing(object? sender, CancelEventArgs e)
    {
        if (busy) { e.Cancel = true; ErrorText.Text = "현재 작업이 끝난 뒤 종료해 주세요."; }
    }
    private void SetBusy(bool value)
    {
        busy = value;
        RefreshButton.IsEnabled = BrowseButton.IsEnabled = LaunchButton.IsEnabled = !value;
        InstallButton.IsEnabled = !value && latest is not null && canInstall;
    }
    private void ReadLocal()
    {
        var installation = new Installation(RootBox.Text);
        InstanceText.Text = "전용 폴더: " + installation.Instance;
        var installed = installation.Installed();
        CurrentText.Text = installed?.DisplayVersion ?? "미설치";
        var healthy = installed is not null && installation.Healthy(installed);
        InstalledText.Text = healthy ? "설치됨 · 파일 검증 완료" : installed is null ? "미설치" : "설치 파일 확인/복구 필요";
        FabricText.Text = healthy ? "Loader 0.19.5 · API 0.163.0+26.3 · Java 25 (공식 런처 관리)" : "설치 전 · 순정 26.3 / Java 25 필요";
        InstallButton.Content = installed is null ? "설치" : latest is not null && latest.BuildNumber > installed.BuildNumber ? "업데이트 설치" : "설치 확인 / 복구";
    }
    private async Task Refresh()
    {
        if (busy) return;
        canInstall = false; SetBusy(true); latest = null; ErrorText.Text = ""; StatusText.Text = "최신 개발 빌드 조회 중";
        ProgressBar.IsIndeterminate = true;
        try
        {
            var installation = new Installation(RootBox.Text);
            if (File.Exists(SafePaths.Under(installation.Control, "transaction.json")))
            {
                var restored = new Installer(downloads, WindowsEnvironment.AssertIdle).Recover(installation);
                if (restored) StatusText.Text = "중단된 설치를 이전 버전으로 복구했습니다.";
            }
            ReadLocal();
            latest = await new ReleaseClient(downloads).Latest();
            LatestText.Text = latest.DisplayVersion; NotesBox.Text = latest.Notes;
            ReadLocal();
            installation.ValidateExistingMinecraft();
            launcher ??= WindowsEnvironment.FindLauncher();
            if (launcher is null) throw new IOException("공식 Minecraft Launcher를 찾지 못했습니다. 설치 안내를 확인하거나 ‘공식 런처 열기’에서 실행 파일을 선택하고 종료한 뒤 다시 조회해 주세요.");
            canInstall = true;
            StatusText.Text = "조회 완료 · 설치 또는 업데이트할 수 있습니다.";
            var installed = installation.Installed();
            if (installed is not null && installed.BuildNumber == latest.BuildNumber && installation.Healthy(installed)) StatusText.Text = "최신 버전이 설치되어 있습니다.";
        }
        catch (Exception e) { ErrorText.Text = e is OperationCanceledException ? "조회 시간이 초과되었습니다. 인터넷 연결을 확인하고 다시 시도해 주세요." : e.Message; StatusText.Text = "조회/환경 확인 실패 · 기존 설치 파일은 유지됩니다."; }
        finally { ProgressBar.IsIndeterminate = false; SetBusy(false); }
    }
    private async void Refresh_Click(object sender, RoutedEventArgs e) => await Refresh();
    private async void Install_Click(object sender, RoutedEventArgs e)
    {
        if (busy || latest is null) return;
        SetBusy(true); ErrorText.Text = "";
        try
        {
            var installation = new Installation(RootBox.Text);
            var progress = new Progress<InstallProgress>(p => { StatusText.Text = p.Stage; ProgressBar.Value = p.Percent; });
            if (launcher is null) throw new IOException("공식 Minecraft Launcher를 찾지 못했습니다. 설치 안내를 확인하거나 ‘공식 런처 열기’에서 MinecraftLauncher.exe를 선택한 뒤 런처를 종료해 주세요.");
            var selected = latest;
            await Task.Run(() => new Installer(downloads, WindowsEnvironment.AssertIdle).Install(installation, selected, progress));
            ReadLocal();
        }
        catch (Exception error) { ErrorText.Text = error is OperationCanceledException ? "다운로드 시간이 초과되었습니다. 기존 설치 파일은 유지됩니다. 다시 시도해 주세요." : error.Message; StatusText.Text = "설치 중단 · 안내를 확인하고 다시 시도해 주세요."; }
        finally { SetBusy(false); }
    }
    private async void Browse_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        var dialog = new OpenFolderDialog { Title = "launcher_profiles 파일이 있는 공식 Minecraft 데이터 폴더 선택" };
        if (dialog.ShowDialog() != true) return;
        try
        {
            var installation = new Installation(dialog.FolderName);
            installation.ValidateExistingMinecraft();
            RootBox.Text = installation.Root;
            FileTransaction.AtomicWrite(Settings, JsonSerializer.SerializeToUtf8Bytes(installation.Root));
            await Refresh();
        }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    private void Launch_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var installation = new Installation(RootBox.Text);
            if (File.Exists(SafePaths.Under(installation.Control, "transaction.json"))) throw new IOException("중단된 설치를 먼저 복구해 주세요. ‘업데이트 조회’를 눌러 주세요.");
            launcher = WindowsEnvironment.OpenLauncher(launcher);
            StatusText.Text = "공식 런처에서 ‘Bed Defense 26.3’을 선택하고 플레이를 눌러 주세요. 목록에 없으면 설치 항목의 ‘모드 적용’ 표시를 확인해 주세요.";
        }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    private void Help_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo("https://github.com/darkkyoung/Minecraft_BedDefense#bed-defense-launcher-windows") { UseShellExecute = true }); }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    internal void ShowSmokeManifest(ReleaseManifest manifest)
    {
        latest = manifest; LatestText.Text = manifest.DisplayVersion; NotesBox.Text = manifest.Notes;
        CurrentText.Text = "미설치"; InstalledText.Text = "CI: 사용자 환경 변경 없음";
        FabricText.Text = "Loader 0.19.5 · API 0.163.0+26.3 · Java 25 (공식 런처 관리)";
        InstanceText.Text = "독립 게임 폴더: instances\\BedDefense_26.3";
        StatusText.Text = "UI 초기화 및 배포 정보 조회 성공"; ProgressBar.Value = 100;
    }
}
