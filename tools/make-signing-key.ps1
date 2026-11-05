# YouTv Plus: create one signing key for all future APKs and put it into GitHub secrets.
# Needs a JDK (keytool). Keep the .jks file and the password safe: losing them means users
# must uninstall before they can install the next version.
$ErrorActionPreference = 'Stop'

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool -and $env:JAVA_HOME) { $keytool = Get-Item (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -ErrorAction SilentlyContinue }
if (-not $keytool) { Write-Host 'keytool not found. Install JDK 17 (for example Temurin) and run this again.' -ForegroundColor Red; exit 1 }
$keytoolPath = if ($keytool.Source) { $keytool.Source } else { $keytool.FullName }

$folder = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'YouTvPlus-key'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$jks = Join-Path $folder 'youtvplus.jks'
$alias = 'youtvplus'

if (Test-Path $jks) {
    Write-Host "A key already exists: $jks (it is kept, not replaced)." -ForegroundColor Yellow
    $pass = Read-Host 'Its password'
} else {
    $pass = Read-Host 'Choose a password for the key (at least 6 characters)'
    if ($pass.Length -lt 6) { Write-Host 'Too short.' -ForegroundColor Red; exit 1 }
    & $keytoolPath -genkeypair -v -keystore $jks -alias $alias -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pass -keypass $pass -dname 'CN=YouTv Plus'
    Write-Host "Key created: $jks  (back up this file and the password)" -ForegroundColor Green
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
Set-Clipboard -Value $b64

Write-Host ''
Write-Host 'The key (base64) is now in the clipboard. On the GitHub page that opens, click "New repository secret" four times:' -ForegroundColor Cyan
Write-Host '  KEYSTORE_BASE64   = paste (Ctrl+V)'
Write-Host "  KEYSTORE_PASSWORD = the password"
Write-Host "  KEY_ALIAS         = $alias"
Write-Host "  KEY_PASSWORD      = the password"
Start-Process 'https://github.com/mbnetworku/YouNew/settings/secrets/actions'
