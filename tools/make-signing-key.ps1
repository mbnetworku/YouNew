# YouTv Plus: create one signing key for all future APKs and store it in the GitHub secrets by itself.
# Sends ONLY the four secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) to
# the repository below, through the official GitHub CLI. Nothing else leaves this computer.
# Keep the .jks file and the password safe: losing them means users must uninstall before the next update.
$ErrorActionPreference = 'Stop'
$repo = 'mbnetworku/YouNew'

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
function Ensure-Tool($command, $wingetId, $label) {
    if (Get-Command $command -ErrorAction SilentlyContinue) { return }
    Write-Host "$label not found. Installing it with winget..." -ForegroundColor Yellow
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Host "winget is missing. Install $label by hand, then run this again." -ForegroundColor Red; exit 1
    }
    winget install --id $wingetId -e --silent --accept-package-agreements --accept-source-agreements
    Refresh-Path
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        Write-Host "$label was installed but is not found yet. Close this window and run the file again." -ForegroundColor Red; exit 1
    }
}

Ensure-Tool 'keytool' 'EclipseAdoptium.Temurin.17.JDK' 'Java JDK 17'
Ensure-Tool 'gh' 'GitHub.cli' 'GitHub CLI'
$keytoolPath = (Get-Command keytool).Source

$folder = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'YouTvPlus-key'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$jks = Join-Path $folder 'youtvplus.jks'
$alias = 'youtvplus'

function Read-Secret($prompt) {
    $s = Read-Host $prompt -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

if (Test-Path $jks) {
    Write-Host "A key already exists: $jks (it is kept, not replaced)." -ForegroundColor Yellow
    $pass = Read-Secret 'Its password'
} else {
    $pass = Read-Secret 'Choose a password for the key (at least 6 characters)'
    if ($pass.Length -lt 6) { Write-Host 'Too short.' -ForegroundColor Red; exit 1 }
    & $keytoolPath -genkeypair -v -keystore $jks -alias $alias -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pass -keypass $pass -dname 'CN=YouTv Plus'
    if ($LASTEXITCODE -ne 0) { Write-Host 'Key creation failed.' -ForegroundColor Red; exit 1 }
    Write-Host "Key created: $jks  (back up this file and the password)" -ForegroundColor Green
}

# Log in to GitHub once, in the browser, if not already logged in.
gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Log in to GitHub: a browser page opens. Follow it, then come back here.' -ForegroundColor Cyan
    gh auth login --web --git-protocol https --scopes repo
    if ($LASTEXITCODE -ne 0) { Write-Host 'GitHub login failed.' -ForegroundColor Red; exit 1 }
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
$secrets = [ordered]@{
    KEYSTORE_BASE64   = $b64
    KEYSTORE_PASSWORD = $pass
    KEY_ALIAS         = $alias
    KEY_PASSWORD      = $pass
}
foreach ($name in $secrets.Keys) {
    gh secret set $name --repo $repo --body $secrets[$name]
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Could not save $name. Your GitHub account needs write access to $repo." -ForegroundColor Red
        Write-Host 'Fallback: add the four secrets by hand on the page that opens now.'
        Set-Clipboard -Value $b64
        Start-Process "https://github.com/$repo/settings/secrets/actions"
        exit 1
    }
    Write-Host "Saved secret $name" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Done. The four secrets are in GitHub. Tell Claude, and the next release will be signed with your key.' -ForegroundColor Cyan
