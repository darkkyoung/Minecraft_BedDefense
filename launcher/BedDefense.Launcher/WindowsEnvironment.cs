using System.Diagnostics;
using System.IO;
using Microsoft.Win32;

namespace BedDefense.Launcher;
internal static class WindowsEnvironment
{
    public static void AssertIdle()
    {
        // Conservative by design: no dependency on privileged command-line/WMI inspection.
        foreach (var process in Process.GetProcesses())
        {
            using (process)
            {
                string name;
                try { name = process.ProcessName; } catch { throw new IOException("실행 프로세스를 확인할 수 없어 설치를 중단했습니다."); }
                if (name.Equals("java", StringComparison.OrdinalIgnoreCase) || name.Equals("javaw", StringComparison.OrdinalIgnoreCase) ||
                    name.StartsWith("Minecraft", StringComparison.OrdinalIgnoreCase))
                    throw new IOException("Minecraft와 공식 런처를 종료해 주세요. 안전을 위해 다른 Java 프로그램(java/javaw)이 실행 중이어도 설치를 보류합니다.");
            }
        }
    }
    public static string? FindLauncher()
    {
        foreach (var baseDir in new[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData) })
        {
            var exe = Path.Combine(baseDir, "Minecraft Launcher", "MinecraftLauncher.exe");
            if (File.Exists(exe)) return exe;
        }
        // Query registered applications; never use minecraft:// (may open Bedrock).
        try
        {
            var type = Type.GetTypeFromProgID("Shell.Application");
            if (type is null) return null;
            dynamic shell = Activator.CreateInstance(type)!;
            dynamic apps = shell.NameSpace("shell:AppsFolder");
            foreach (dynamic item in apps.Items())
            {
                string path = item.Path;
                if (path.StartsWith("Microsoft.4297127D64EC6_8wekyb3d8bbwe!", StringComparison.OrdinalIgnoreCase)) return "shell:AppsFolder\\" + path;
            }
        }
        catch { /* Unregistered Store install: offer explicit executable selection. */ }
        return null;
    }
    public static string? OpenLauncher(string? target)
    {
        target ??= FindLauncher();
        if (target is null)
        {
            var dialog = new OpenFileDialog { Title = "공식 MinecraftLauncher.exe 선택", Filter = "공식 Minecraft Launcher|MinecraftLauncher.exe", CheckFileExists = true };
            if (dialog.ShowDialog() != true) return null;
            target = dialog.FileName;
            if (!Path.GetFileName(target).Equals("MinecraftLauncher.exe", StringComparison.OrdinalIgnoreCase)) throw new IOException("공식 런처 실행 파일을 선택해 주세요.");
        }
        if (target.StartsWith("shell:AppsFolder\\", StringComparison.Ordinal))
            Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, Arguments = target });
        else Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
        return target;
    }
}
