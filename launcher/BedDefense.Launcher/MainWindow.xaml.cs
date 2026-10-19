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
    private LauncherUpgrade? launcherUpgrade;
    private bool busy;
    private bool canInstall;
    private bool loadingProfiles;
    private string? savedProfile;
    private sealed record Preferences(string Root, int Mode, string? Profile);
    private bool ExistingMode => ModeBox.SelectedIndex == 0;
    private Installation SelectedInstallation() => ExistingMode
        ? new Installation(RootBox.Text, ProfileBox.SelectedItem as FabricProfile ?? throw new IOException("기존 Fabric 26.3 프로필을 선택해 주세요. 목록이 없으면 해당 프로필을 공식 런처에서 한 번 실행하거나 독립 환경을 선택해 주세요."))
        : new Installation(RootBox.Text);
    private void SavePreferences()
    {
        FileTransaction.AtomicWrite(Settings, JsonSerializer.SerializeToUtf8Bytes(new Preferences(RootBox.Text, ModeBox.SelectedIndex, (ProfileBox.SelectedItem as FabricProfile)?.Identity)));
    }
    private void LoadProfiles()
    {
        loadingProfiles = true;
        try
        {
            var identity = (ProfileBox.SelectedItem as FabricProfile)?.Identity ?? savedProfile;
            var profiles = ExistingProfiles.Discover(RootBox.Text);
            ProfileBox.ItemsSource = profiles;
            ProfileBox.SelectedItem = profiles.FirstOrDefault(p => p.Identity == identity)
                ?? profiles.FirstOrDefault(p => p.Problem is null && p.Key != "beddefense-managed-26.3")
                ?? profiles.FirstOrDefault();
            MigrateButton.Visibility = Directory.Exists(new Installation(RootBox.Text).Instance) ? Visibility.Visible : Visibility.Collapsed;
        }
        finally { loadingProfiles = false; }
    }
    private async void Selection_Changed(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (!IsLoaded || loadingProfiles || busy || smoke) return;
        await Refresh();
    }
    private async void Migrate_Click(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        loadingProfiles = true; ModeBox.SelectedIndex = 0; loadingProfiles = false;
        await Refresh();
        StatusText.Text = "전환할 기존 Fabric 프로필을 선택한 뒤 설치를 눌러 주세요. 이전 독립 폴더·프로필·월드는 그대로 보존됩니다.";
    }
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
            try
            {
                if (File.Exists(Settings))
                {
                    var json = File.ReadAllText(Settings);
                    using var doc = JsonDocument.Parse(json);
                    if (doc.RootElement.ValueKind == JsonValueKind.String) RootBox.Text = doc.RootElement.GetString() ?? RootBox.Text;
                    else
                    {
                        var preferences = JsonSerializer.Deserialize<Preferences>(json);
                        if (preferences is not null) { RootBox.Text = preferences.Root; ModeBox.SelectedIndex = preferences.Mode == 1 ? 1 : 0; savedProfile = preferences.Profile; }
                    }
                }
            }
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
        RefreshButton.IsEnabled = BrowseButton.IsEnabled = LaunchButton.IsEnabled = ModeBox.IsEnabled = MigrateButton.IsEnabled = !value;
        ProfileBox.IsEnabled = !value && ExistingMode;
        InstallButton.IsEnabled = !value && latest is not null && canInstall;
        UpdateLauncherButton.IsEnabled = !value && launcherUpgrade is not null;
    }
    private void ReadLocal()
    {
        var installation = SelectedInstallation();
        InstanceText.Text = "실제 게임 폴더: " + installation.Instance;
        ProfileHint.Text = ExistingMode ? "프로필 정보는 유지합니다. 같은 게임 폴더를 공유하는 프로필에도 모드가 적용됩니다. Loader/API 누락·충돌 시 설치를 보류합니다." : "별도의 Bed Defense 26.3 프로필과 게임 폴더를 생성합니다.";
        var installed = installation.Installed();
        var manual = installed is null && installation.IsExisting && File.Exists(installation.BedDefenseTarget)
            ? ModCompatibility.Read(installation.BedDefenseTarget)[0].Version : null;
        CurrentText.Text = installed?.DisplayVersion ?? (manual is null ? "미설치" : manual + " · 기존 수동 설치");
        var healthy = installed is not null && installation.Healthy(installed);
        InstalledText.Text = healthy ? "설치됨 · 파일 검증 완료" : installed is null ? "미설치" : "설치 파일 확인/복구 필요";
        FabricText.Text = ExistingMode ? "기존 Loader " + installation.ExistingProfile!.LoaderVersion + " · 호환성 검사 필요" : healthy ? "Loader 0.19.5 · API 0.163.0+26.3 · Java 25 (공식 런처 관리)" : "설치 전 · 순정 26.3 / Java 25 필요";
        InstallButton.Content = installed is null ? "설치" : latest is not null && latest.BuildNumber > installed.BuildNumber ? "업데이트 설치" : "설치 확인 / 복구";
    }
    private async Task Refresh()
    {
        if (busy) return;
        canInstall = false; launcherUpgrade = null; UpdateLauncherButton.Visibility = Visibility.Collapsed;
        SetBusy(true); latest = null; ErrorText.Text = ""; StatusText.Text = "최신 개발 빌드 조회 중";
        ProgressBar.IsIndeterminate = true;
        try
        {
            LoadProfiles();
            var installation = SelectedInstallation();
            if (File.Exists(SafePaths.Under(installation.Control, "transaction.json")))
            {
                var restored = new Installer(downloads, WindowsEnvironment.AssertIdle).Recover(installation);
                if (restored) StatusText.Text = "중단된 설치를 이전 버전으로 복구했습니다.";
            }
            ReadLocal();
            latest = await new ReleaseClient(downloads).Latest();
            LatestText.Text = latest.DisplayVersion; NotesBox.Text = latest.Notes;
            // Never block regular mod installation when the optional EXE update check fails.
            try
            {
                launcherUpgrade = await LauncherUpdates.Check(downloads, latest, Environment.ProcessPath!);
                if (launcherUpgrade is not null)
                {
                    UpdateLauncherButton.Visibility = Visibility.Visible;
                    StatusText.Text = "런처 프로그램 업데이트가 있습니다. [런처 업데이트]를 누르면 검증된 최신 EXE로 전환합니다.";
                }
            }
            catch (Exception updateError)
            {
                StatusText.Text += " · 런처 자체 업데이트 확인 보류: " + updateError.Message;
            }
            ReadLocal();
            installation.ValidateExistingMinecraft();
            if (ExistingMode) FabricText.Text = ModCompatibility.Check(installation, latest).Summary;
            launcher ??= WindowsEnvironment.FindLauncher();
            if (launcher is null) throw new IOException("공식 Minecraft Launcher를 찾지 못했습니다. 설치 안내를 확인하거나 ‘공식 런처 열기’에서 실행 파일을 선택하고 종료한 뒤 다시 조회해 주세요.");
            canInstall = true;
            SavePreferences();
            StatusText.Text = "조회 완료 · 설치 또는 업데이트할 수 있습니다.";
            var installed = installation.Installed();
            if (installed is not null && installed.BuildNumber == latest.BuildNumber && installation.Healthy(installed)) StatusText.Text = "최신 버전이 설치되어 있습니다.";

        }
        catch (Exception e) { ErrorText.Text = e is OperationCanceledException ? "조회 시간이 초과되었습니다. 인터넷 연결을 확인하고 다시 시도해 주세요." : e.Message; StatusText.Text = launcherUpgrade is not null ? "모드 환경 검사 오류가 있어 설치를 보류했습니다. [런처 업데이트]로 검사 기능을 먼저 갱신할 수 있습니다." : "조회/환경 확인 실패 · 기존 설치 파일은 유지됩니다."; }
        finally { ProgressBar.IsIndeterminate = false; SetBusy(false); }
    }

    private async void UpdateLauncher_Click(object sender, RoutedEventArgs e)
    {
        if (busy || launcherUpgrade is null) return;
        var requested = launcherUpgrade;
        SetBusy(true);
        ErrorText.Text = "";
        StatusText.Text = "새 런처를 검증된 버전별 폴더에 다운로드합니다...";
        ProgressBar.IsIndeterminate = true;
        try
        {
            var executable = await LauncherUpdates.Download(downloads, requested);
            if (Process.Start(new ProcessStartInfo(executable) { UseShellExecute = true }) is null)
                throw new IOException("새 런처를 열지 못했습니다.");
            // Closing is intentionally blocked while a normal install is busy.
            // Clear that guard before handing off to the verified replacement EXE.
            SetBusy(false);
            Application.Current.Shutdown();
        }
        catch (Exception error)
        {
            ErrorText.Text = "런처 업데이트 실패: " + error.Message;
            StatusText.Text = "기존 런처와 Bed Defense 설치 파일은 그대로 유지됩니다.";
        }
        finally
        {
            ProgressBar.IsIndeterminate = false;
            SetBusy(false);
        }
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await Refresh();
    private async void Install_Click(object sender, RoutedEventArgs e)
    {
        if (busy || latest is null) return;
        SetBusy(true); ErrorText.Text = ""; ProgressBar.Value = 0;
        try
        {
            var installation = SelectedInstallation();
            var progress = new Progress<InstallProgress>(p => { StatusText.Text = p.Stage; ProgressBar.Value = p.Percent; });
            if (launcher is null) throw new IOException("공식 Minecraft Launcher를 찾지 못했습니다. 설치 안내를 확인하거나 ‘공식 런처 열기’에서 MinecraftLauncher.exe를 선택한 뒤 런처를 종료해 주세요.");
            var selected = latest;
            await Task.Run(() => new Installer(downloads, WindowsEnvironment.AssertIdle).Install(installation, selected, progress));
            ReadLocal();
            SavePreferences();
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
            savedProfile = null;
            loadingProfiles = true; ProfileBox.SelectedItem = null; loadingProfiles = false;
            await Refresh();
        }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    private void Launch_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var installation = ExistingMode && ProfileBox.SelectedItem is null ? null : SelectedInstallation();
            if (installation is not null && File.Exists(SafePaths.Under(installation.Control, "transaction.json"))) throw new IOException("중단된 설치를 먼저 복구해 주세요. ‘업데이트 조회’를 눌러 주세요.");
            launcher = WindowsEnvironment.OpenLauncher(launcher);
            StatusText.Text = "공식 런처에서 ‘" + (installation?.ExistingProfile?.Name ?? "Bed Defense 26.3") + "’ 프로필을 선택하고 플레이를 눌러 주세요.";
        }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    private void Help_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo("https://github.com/darkkyoung/Minecraft_BedDefense#bed-defense-launcher-windows") { UseShellExecute = true }); }
        catch (Exception error) { ErrorText.Text = error.Message; }
    }
    internal void ShowSmokeExisting(ReleaseManifest manifest, string root)
    {
        if (!ExistingMode) throw new IOException("기본 설치 방식이 기존 Fabric이 아닙니다.");
        RootBox.Text = root; LoadProfiles();
        var installation = SelectedInstallation();
        if (!installation.IsExisting || installation.Instance != Path.GetFullPath(root)) throw new IOException("UI 프로필 선택/gameDir 해석 실패");
        latest = manifest; ReadLocal();
        FabricText.Text = ModCompatibility.Check(installation, manifest).Summary;
        canInstall = true; SetBusy(false);
        ModeBox.SelectedIndex = 1;
        if (SelectedInstallation().IsExisting) throw new IOException("독립 환경 옵션 전환 실패");
        ModeBox.SelectedIndex = 0;
        StatusText.Text = "CI: 기존 프로필 선택 / gameDir / 호환성 검사 / 독립 옵션 전환 통과";
    }
    internal void ShowSmokeManifest(ReleaseManifest manifest)
    {
        latest = manifest; LatestText.Text = manifest.DisplayVersion; NotesBox.Text = manifest.Notes;
        CurrentText.Text = "미설치"; InstalledText.Text = "CI: 사용자 환경 변경 없음";
        FabricText.Text = "Loader 0.19.5 · API 0.163.0+26.3 · Java 25 (공식 런처 관리)";
        InstanceText.Text = "기존 Fabric 프로필의 실제 gameDir에 Bed Defense만 설치";
        ProfileHint.Text = "기존 Fabric에 설치 (기본) · 독립 환경은 선택 옵션";
        StatusText.Text = "UI 초기화 및 배포 정보 조회 성공"; ProgressBar.Value = 100;
    }
}
