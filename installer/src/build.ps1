param(
    [switch]$Test,
    [switch]$Admin,
    [switch]$CompileOnly,
    [string]$AppDir = '',
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
$src = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $AppDir) { $AppDir = Join-Path (Split-Path -Parent (Split-Path -Parent $src)) 'build\portable\LostFilmDesktop' }
if (-not $OutDir) { $OutDir = Join-Path (Split-Path -Parent (Split-Path -Parent $src)) 'build\dist' }
$csc = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$compression = 'C:\Windows\Microsoft.NET\assembly\GAC_MSIL\System.IO.Compression\v4.0_4.0.0.0__b77a5c561934e089\System.IO.Compression.dll'
$manifest = if ($Admin) { Join-Path $src 'app.manifest' } else { Join-Path $src 'app.noadmin.manifest' }
$icon = Join-Path $src '..\..\src\main\resources\icons\app.ico'
if (-not (Test-Path -LiteralPath $icon)) { $icon = '' }
$out = Join-Path $src 'out'
New-Item -ItemType Directory -Path $out -Force | Out-Null
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

$common = @(
    '/nologo',
    '/target:winexe',
    '/optimize+',
    '/platform:anycpu',
    "/win32manifest:$manifest",
    '/r:System.dll',
    '/r:System.Drawing.dll',
    '/r:System.Windows.Forms.dll',
    "/r:$compression"
)
if ($icon) {
    $common += "/win32icon:$icon"
    $common += "/resource:$icon,LostFilmInstaller.app.ico"
}

Write-Host 'Compiling Uninstall.exe...'
& $csc @common '/define:UNINSTALLER' "/out:$out\Uninstall.exe" (Join-Path $src 'Installer.cs')
if ($LASTEXITCODE -ne 0) { throw "csc failed for Uninstall.exe ($LASTEXITCODE)" }

Write-Host 'Compiling Setup.exe...'
& $csc @common "/out:$out\Setup.exe" (Join-Path $src 'Installer.cs')
if ($LASTEXITCODE -ne 0) { throw "csc failed for Setup.exe ($LASTEXITCODE)" }

if ($CompileOnly) {
    Write-Host "Compiled: $out\Setup.exe, $out\Uninstall.exe"
    exit 0
}

if (-not (Test-Path -LiteralPath $AppDir)) { throw "Не найдена папка приложения: $AppDir. Сначала выполните .\gradlew.bat portable." }

Write-Host 'Packing payload...'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = Join-Path $out 'payload.zip'
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    $root = (Resolve-Path -LiteralPath $AppDir).Path
    $files = @(Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object { $_.Name -ne 'portable.marker' -and $_.Name -ne '.gitkeep' -and $_.FullName -notmatch '\\data\\' })
    if ($files.Count -eq 0) { throw "В $root нет файлов для установки." }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $file.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal)
    }
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, (Join-Path $out 'Uninstall.exe'), 'Uninstall.exe', [System.IO.Compression.CompressionLevel]::Optimal)
}
finally {
    $zip.Dispose()
}

$stub = [System.IO.File]::ReadAllBytes((Join-Path $out 'Setup.exe'))
$zipBytes = [System.IO.File]::ReadAllBytes($zipPath)
$footer = New-Object byte[] 24
$magic = [System.Text.Encoding]::ASCII.GetBytes('ADBWIFISETUP0001')
[Array]::Copy($magic, $footer, $magic.Length)
$lengthBytes = [BitConverter]::GetBytes([int64]$zipBytes.Length)
[Array]::Copy($lengthBytes, 0, $footer, 16, 8)

$name = if ($Test) { 'LostFilmDesktop-Setup-test.exe' } else { 'LostFilmDesktop-Setup-1.0.0.exe' }
$final = Join-Path $OutDir $name
$stream = [System.IO.File]::Create($final)
try {
    $stream.Write($stub, 0, $stub.Length)
    $stream.Write($zipBytes, 0, $zipBytes.Length)
    $stream.Write($footer, 0, $footer.Length)
}
finally {
    $stream.Close()
}

$size = [math]::Round((Get-Item -LiteralPath $final).Length / 1MB, 1)
Write-Host "Built: $final ($size MB)"
