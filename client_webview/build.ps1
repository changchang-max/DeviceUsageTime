#Requires -Version 5.1
<#
.SYNOPSIS
    打包新版 WebView 客户端（client_webview）为单文件 exe。

.DESCRIPTION
    按 .github/workflows/ci.yml 中 build-client 的同一条流程在本地执行：
        1. 检查 Python 环境与依赖（Python 3.10 + package.txt）
        2. 检查 main.spec 是否被 PyInstaller 默认模板覆盖（防止手滑 pyinstaller main.py）
        3. 结束正在运行的客户端（否则 dist 下的 exe 被占用）
        4. 清理 dist/build 后执行 pyinstaller main.spec --noconfirm --clean
        5. 校验产物名、体积，以及前端 web/ 资源是否真的打进包里

    用法：
        # 直接打包
        .\build.ps1

        # 打包并做启动冒烟测试（会短暂弹出客户端窗口）
        .\build.ps1 -SmokeTest

        # 依赖已装好，跳过 pip install
        .\build.ps1 -SkipDeps

.PARAMETER SkipDeps
    跳过 pip install -r package.txt，适用于依赖已就绪的情况。

.PARAMETER NoClean
    不删除 dist/build，也不给 PyInstaller 传 --clean（增量打包，速度快但可能用到旧缓存）。

.PARAMETER SmokeTest
    打包成功后启动 exe，等待若干秒确认进程存活，再强制结束。

.PARAMETER PythonPath
    指定 Python 解释器路径；默认自动查找 conda 环境 DeviceUsageTime 下的 python.exe，
    找不到时退回 PATH 中的 python。

.EXAMPLE
    conda activate DeviceUsageTime; .\build.ps1 -SmokeTest
#>
[CmdletBinding()]
param(
    [switch]$SkipDeps,
    [switch]$NoClean,
    [switch]$SmokeTest,
    [string]$PythonPath
)

$ErrorActionPreference = 'Stop'

# 脚本所在目录即 client_webview，spec 里的相对路径（web/、os.getcwd()）都基于它
$ProjectDir = Split-Path -Parent $PSCommandPath
$SpecPath = Join-Path $ProjectDir 'main.spec'
$ExePath = Join-Path $ProjectDir 'dist\DeviceUsageTime.exe'
$TocPath = Join-Path $ProjectDir 'build\main\Analysis-00.toc'
$ExpectedMarkers = @(
    '("web", "web")',                       # 前端资源必须入包
    'name="DeviceUsageTime"',               # 产物名必须固定，CI 的 Verify 步骤依赖它
    'web", "assets", "icon.ico"'            # 图标必须指向真实存在的文件
)
$ExpectedTocEntries = @(
    'web\index.html',
    'web\js\views\main-view.js',
    'web\css'
)

function Write-Step {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Fail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

<#
.SYNOPSIS
    解析要使用的 Python 解释器路径。

.PARAMETER Explicit
    用户通过 -PythonPath 显式指定的路径，优先级最高。

.OUTPUTS
    System.String：可用的 python.exe 路径，找不到时返回 'python'（依赖 PATH）。
#>
function Resolve-Python {
    param([string]$Explicit)

    # 1. 显式指定优先
    if ($Explicit) { return $Explicit }

    # 2. 当前已激活的 conda 环境
    if ($env:CONDA_PREFIX) {
        $activated = Join-Path $env:CONDA_PREFIX 'python.exe'
        if (Test-Path $activated) { return $activated }
    }

    # 3. 常见 conda 安装位置下的 DeviceUsageTime 环境
    $candidates = @(
        (Join-Path $env:USERPROFILE '.conda\envs\DeviceUsageTime\python.exe'),
        (Join-Path $env:USERPROFILE 'miniconda3\envs\DeviceUsageTime\python.exe'),
        (Join-Path $env:USERPROFILE 'anaconda3\envs\DeviceUsageTime\python.exe'),
        (Join-Path $env:ProgramData 'miniconda3\envs\DeviceUsageTime\python.exe'),
        (Join-Path $env:ProgramData 'anaconda3\envs\DeviceUsageTime\python.exe')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }

    # 4. 兜底：PATH 中的 python
    return 'python'
}

$PythonPath = Resolve-Python -Explicit $PythonPath

Push-Location $ProjectDir
try {
    Write-Host "工作目录: $ProjectDir"

    # ---------- 1. Python 环境检查 ----------
    Write-Step '检查 Python 环境'
    Write-Host "解释器: $PythonPath"

    # 探针代码写成临时 .py 文件再执行：Windows PowerShell 5.1 向原生命令传多行字符串会破坏引号
    $probeCode = @'
import importlib.util, sys
mods = ["PyInstaller", "webview", "pythonnet", "pystray", "PIL", "psutil"]
missing = [m for m in mods if importlib.util.find_spec(m) is None]
try:
    import PyInstaller
    pi_ver = PyInstaller.__version__
except Exception:
    pi_ver = "-"
print("%d.%d.%d|%s|%s|%s" % (sys.version_info[0], sys.version_info[1], sys.version_info[2],
                             sys.executable, ",".join(missing), pi_ver))
'@

    <#
    .SYNOPSIS
        运行环境探针，返回 Python 版本、解释器路径、缺失模块与 PyInstaller 版本。

    .PARAMETER Python
        要探测的 python.exe 路径。

    .OUTPUTS
        PSCustomObject：Version / Exe / Missing / PyInstaller 四个字段。
    #>
    function Get-PyEnvInfo {
        param([string]$Python)
        $probeFile = Join-Path $env:TEMP 'dut_env_probe.py'
        Set-Content -Path $probeFile -Value $probeCode -Encoding UTF8
        $out = & $Python $probeFile 2>&1
        $code = $LASTEXITCODE
        Remove-Item $probeFile -Force -ErrorAction SilentlyContinue
        if ($code -ne 0) { Fail "无法执行 Python（$Python）：`n$($out -join "`n")" }
        $fields = ($out | Select-Object -Last 1) -split '\|'
        return [pscustomobject]@{
            Version     = $fields[0]
            Exe         = $fields[1]
            Missing     = $fields[2]
            PyInstaller = $fields[3]
        }
    }

    $envInfo = Get-PyEnvInfo -Python $PythonPath
    Write-Host "Python: $($envInfo.Version)  ($($envInfo.Exe))"
    if ($envInfo.Version -notlike '3.10.*') {
        Write-Host "[WARN] 建议使用 Python 3.10（与 CI 一致），当前为 $($envInfo.Version)" -ForegroundColor Yellow
    }
    if ($envInfo.Missing) {
        if ($SkipDeps) {
            Fail @"
当前解释器缺少依赖：$($envInfo.Missing)
请先执行 conda activate DeviceUsageTime 再重试，或显式指定解释器：
.\build.ps1 -PythonPath "$env:USERPROFILE\.conda\envs\DeviceUsageTime\python.exe"
（不加 -SkipDeps 时脚本会自动 pip install -r package.txt）
"@
        }
        Write-Host "[WARN] 缺少依赖：$($envInfo.Missing)，将在安装依赖步骤补齐" -ForegroundColor Yellow
    }
    else {
        Write-Host "依赖检查通过（PyInstaller $($envInfo.PyInstaller)）"
    }

    # ---------- 2. spec 防覆盖检查 ----------
    Write-Step '检查 main.spec 配置'
    if (-not (Test-Path $SpecPath)) { Fail "找不到 $SpecPath" }
    $specText = Get-Content -Path $SpecPath -Raw -Encoding UTF8
    $missingMarkers = $ExpectedMarkers | Where-Object { $specText -notlike "*$_*" }
    if ($missingMarkers) {
        Fail @"
main.spec 已被 PyInstaller 默认模板覆盖（缺少：$($missingMarkers -join '、')）。
常见原因：执行过 pyinstaller main.py，PyInstaller 会顺手重写同名 spec。
恢复命令：git checkout HEAD -- client_webview/main.spec
请务必使用 pyinstaller main.spec，不要用 pyinstaller main.py。
"@
    }
    Write-Host 'main.spec 关键字校验通过（web 资源 / 产物名 / 图标）'

    # ---------- 3. 结束运行中的客户端 ----------
    Write-Step '结束正在运行的客户端'
    $running = Get-Process -Name 'DeviceUsageTime' -ErrorAction SilentlyContinue
    if ($running) {
        $running | ForEach-Object {
            Write-Host "结束进程 PID=$($_.Id)"
            $_.Kill()
            $_.WaitForExit(5000) | Out-Null
        }
        Start-Sleep -Milliseconds 800
    }
    else {
        Write-Host '没有正在运行的实例'
    }

    # ---------- 4. 依赖安装 ----------
    if (-not $SkipDeps) {
        Write-Step '安装依赖（package.txt）'
        & $PythonPath -m pip install -r (Join-Path $ProjectDir 'package.txt') --disable-pip-version-check
        if ($LASTEXITCODE -ne 0) { Fail 'pip install 失败' }
    }
    else {
        Write-Host "`n=== 跳过依赖安装（-SkipDeps）===" -ForegroundColor DarkGray
    }

    # 安装后复检：确认依赖真的齐了，并与 package.txt 锁定的 PyInstaller 版本对齐
    $envInfo = Get-PyEnvInfo -Python $PythonPath
    if ($envInfo.Missing) {
        Fail "依赖仍缺失：$($envInfo.Missing)（解释器 $($envInfo.Exe)），pip install 可能失败或被环境隔离"
    }
    $pinnedVer = (Select-String -Path (Join-Path $ProjectDir 'package.txt') -Pattern '^pyinstaller==(.+)$').Matches.Groups[1].Value
    Write-Host "PyInstaller: $($envInfo.PyInstaller)（package.txt 锁定 $pinnedVer）"
    if ($pinnedVer -and $envInfo.PyInstaller -ne $pinnedVer) {
        Write-Host "[WARN] PyInstaller 版本与 package.txt 不一致，产物可能与 CI 不同" -ForegroundColor Yellow
    }

    # ---------- 5. 清理旧产物 ----------
    if (-not $NoClean) {
        Write-Step '清理 dist / build'
        foreach ($dir in @('dist', 'build')) {
            $target = Join-Path $ProjectDir $dir
            if (Test-Path $target) { Remove-Item -Path $target -Recurse -Force }
        }
        Write-Host '已清理'
    }

    # ---------- 6. 执行打包 ----------
    Write-Step 'PyInstaller 打包'
    $pyArgs = @('-m', 'PyInstaller', 'main.spec', '--noconfirm')
    if (-not $NoClean) { $pyArgs += '--clean' }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $PythonPath @pyArgs
    $buildExit = $LASTEXITCODE
    $sw.Stop()
    if ($buildExit -ne 0) { Fail "PyInstaller 退出码 $buildExit，打包失败（详见上方日志）" }
    Write-Host ("打包耗时 {0:N1} 秒" -f $sw.Elapsed.TotalSeconds)

    # ---------- 7. 校验产物 ----------
    Write-Step '校验产物'
    if (-not (Test-Path $ExePath)) { Fail "没有生成 $ExePath" }
    $exe = Get-Item $ExePath
    $sizeMB = [math]::Round($exe.Length / 1MB, 2)
    Write-Host "产物: $($exe.FullName)"
    Write-Host "体积: $sizeMB MB"
    Write-Host "时间: $($exe.LastWriteTime)"
    if ($sizeMB -lt 5) { Fail "exe 只有 $sizeMB MB，疑似依赖没打全" }

    if (-not (Test-Path $TocPath)) { Fail "找不到 $TocPath，无法确认前端资源是否入包" }
    # toc 里路径是转义写法（web\\index.html），统一还原成单反斜杠再比对
    $tocText = (Get-Content -Path $TocPath -Raw -Encoding UTF8) -replace '\\\\', '\'
    $missingEntries = $ExpectedTocEntries | Where-Object { $tocText -notlike "*$_*" }
    if ($missingEntries) {
        Fail "前端资源未入包（缺少：$($missingEntries -join '、')），运行会白屏，请检查 spec 的 datas 配置"
    }
    Write-Host '前端资源已入包（index.html / main-view.js / css）' -ForegroundColor Green

    # ---------- 8. 冒烟测试 ----------
    if ($SmokeTest) {
        Write-Step '启动冒烟测试'
        # 清除“来自 Internet”标记，避免被 SmartScreen 拦下
        Unblock-File -Path $ExePath -ErrorAction SilentlyContinue

        # 用 UseShellExecute=$false 走 CreateProcess：
        # 走 ShellExecute 时可能弹出“打开文件 - 安全警告”对话框，把脚本卡死在无人应答的等待上
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $ExePath
        $psi.WorkingDirectory = $ProjectDir
        $psi.UseShellExecute = $false
        $proc = [System.Diagnostics.Process]::Start($psi)

        Start-Sleep -Seconds 6
        if ($proc.HasExited) { Fail "exe 启动后立即退出（退出码 $($proc.ExitCode)），请手动运行查看报错" }
        Write-Host "进程存活 PID=$($proc.Id)，窗口应已显示（托盘图标可能出现）" -ForegroundColor Green
        Stop-Process -Id $proc.Id -Force
        Write-Host '已结束冒烟测试进程'
    }

    Write-Step '完成'
    Write-Host "单文件 exe 已就绪：$ExePath" -ForegroundColor Green
    Write-Host "上传/分发用这一个文件即可；config/ 与 history_data/ 会在首次运行时于 exe 同级目录自动生成。"
}
finally {
    Pop-Location
}
