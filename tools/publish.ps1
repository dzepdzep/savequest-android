# One-step LOCAL publisher for SaveQuest (matches the project's release policy: the APK is built on
# THIS PC with your persistent debug certificate, so phones update without losing data).
# CI never publishes APKs, and neither does this script from a server.
#
#   Double-click tools\publish.bat, or:  powershell -File tools\publish.ps1 -Version 0.1.2-alpha -Notes "Items by region"
param([string]$Version,[string]$Notes,[switch]$AllowSprites)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
function Fail($m){ Write-Host ""; Write-Host "STOPPED: $m" -ForegroundColor Red; exit 1 }
function Run($label,[scriptblock]$b){ Write-Host "== $label" -ForegroundColor Cyan; & $b; if($LASTEXITCODE -ne 0){ Fail "$label failed (see messages above). Nothing was published." } }

if(-not (Get-Command git -ErrorAction SilentlyContinue)){ Fail "Git is not installed: https://git-scm.com/download/win" }
if(-not (Test-Path (Join-Path $root ".git"))){ Fail "This folder is not a git repository." }
$ErrorActionPreference = "Continue"; $remote = (git remote get-url origin 2>$null); $ErrorActionPreference = "Stop"
if(-not $remote){ Fail "No GitHub remote. Run once: git remote add origin https://github.com/dzepdzep/savequest-android.git" }
$slug = ($remote -replace '^.*github\.com[:/]','' -replace '\.git$','').Trim()

if(-not $Version){
  # automatic: raise the last number of the current version, e.g. 0.1.1-alpha -> 0.1.2-alpha
  $cur = [regex]::Match((Get-Content "app\build.gradle.kts" -Raw),'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)(-[A-Za-z0-9.]+)?"')
  if(-not $cur.Success){ Fail "Could not read the current version from app\build.gradle.kts" }
  $Version = "$($cur.Groups[1].Value).$($cur.Groups[2].Value).$([int]$cur.Groups[3].Value + 1)$($cur.Groups[4].Value)"
  Write-Host "New version (automatic): $Version" -ForegroundColor Cyan
}
if($Version -notmatch '^\d+\.\d+\.\d+(-[A-Za-z0-9.]+)?$'){ Fail "Version must look like 0.1.2 or 0.1.2-alpha" }
if(-not $Notes){
  $ErrorActionPreference = "Continue"; $last = (git describe --tags --abbrev=0 2>$null); $ErrorActionPreference = "Stop"; if($LASTEXITCODE -ne 0){ $last = $null }
  $log = if($last){ git log "$last..HEAD" --pretty=format:"- %s" } else { "" }
  $files = (git status --short | Measure-Object).Count
  $Notes = if($log){ "Changes since ${last}:`n$log" } else { "SaveQuest $Version ($files files changed since the last release)" }
}
$tag = "v$Version"
if(git tag -l $tag){ Fail "Tag $tag already exists. Choose a higher version." }
if(((git rev-parse --abbrev-ref HEAD).Trim()) -ne "main"){ Fail "Switch to the main branch first." }

if((Test-Path "app\src\main\assets\sprites") -and -not $AllowSprites){
  Fail "Generated sprites (Nintendo art) are in app\src\main\assets\sprites. The project promises not to publish art. Delete that folder, or add -AllowSprites if you accept that."
}
if(Get-ChildItem -Recurse -Include *.srm,*.sav,*.jks,*.keystore -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch '\\(build|\.gradle)\\' } | Select-Object -First 1){
  Fail "A save file or keystore is inside the project folder. Move it out first."
}

# Gradle here needs JDK 17 (or 21). Your PC default is Java 25, which the build cannot use.
function JavaMajor($h){ $r = Join-Path $h "release"; if(Test-Path $r){ $l = Select-String -Path $r -Pattern 'JAVA_VERSION="?(\d+)' | Select-Object -First 1; if($l){ return [int]$l.Matches[0].Groups[1].Value } }; return 0 }
function FindJava {
  $cands = @()
  foreach($base in @("$env:ProgramFiles\Eclipse Adoptium","$env:ProgramFiles\Java","$env:ProgramFiles\Microsoft","$env:ProgramFiles\Amazon Corretto","$env:ProgramFiles\Zulu")){ if(Test-Path $base){ $cands += (Get-ChildItem $base -Directory | ForEach-Object { $_.FullName }) } }
  $cands += @("$env:ProgramFiles\Android\Android Studio\jbr","$env:LOCALAPPDATA\Programs\Android Studio\jbr")
  foreach($want in @(17,21)){ foreach($h in $cands){ if((JavaMajor $h) -eq $want){ return $h } } }
  return $null
}
$best = FindJava
if(-not $best){
  Write-Host "No JDK 17 found. Installing it automatically (one time, about 1-2 minutes)..." -ForegroundColor Yellow
  if(-not (Get-Command winget -ErrorAction SilentlyContinue)){ Fail "winget is missing. Install Temurin 17 from https://adoptium.net/temurin/releases/?version=17 and run this again." }
  winget install --id EclipseAdoptium.Temurin.17.JDK -e --silent --accept-package-agreements --accept-source-agreements
  $best = FindJava
  if(-not $best){ Fail "Java 17 was installed but not found. Close this window and run publish.bat again." }
}
$env:JAVA_HOME = $best; $env:Path = "$best\bin;$env:Path"
Write-Host "Using Java from $best" -ForegroundColor Cyan
$py = $null
foreach($c in @("py","python")){ if(Get-Command $c -ErrorAction SilentlyContinue){ $out = (& $c -c "print(12345)" 2>$null); if("$out".Trim() -eq "12345"){ $py = $c; break } } }
if($py){ Run "Catalog tests" { & $py tools\test_catalog.py } }
else { Write-Host "(Working Python not found, skipping catalog tests; GitHub runs them after you push.)" -ForegroundColor Yellow }
if(-not (Test-Path "local.properties") -and -not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT){
  $sdk = "$env:LOCALAPPDATA\Android\Sdk"
  if(Test-Path $sdk){ Set-Content "local.properties" ("sdk.dir=" + ($sdk -replace '\\','/')) }
  else { Fail "Android SDK not found. Install Android Studio (https://developer.android.com/studio), open it once and let it finish the setup, then run this again." }
}
Run "Unit tests and debug APK" { .\gradlew.bat :core:test :games:silver:test :app:assembleDebug }
$apkSrc = "app\build\outputs\apk\debug\app-debug.apk"
if(-not (Test-Path $apkSrc)){ $apkSrc = (Get-ChildItem -Recurse "app\build\outputs\apk" -Filter *.apk | Select-Object -First 1).FullName }
if(-not $apkSrc){ Fail "APK not found after the build." }

# bump version
$g = "app\build.gradle.kts"; $t = Get-Content $g -Raw
$t = [regex]::Replace($t,'versionName\s*=\s*"[^"]*"',"versionName = `"$Version`"")
$m = [regex]::Match($t,'versionCode\s*=\s*(\d+)')
if($m.Success){ $t = $t.Remove($m.Index,$m.Length).Insert($m.Index,"versionCode = $([int]$m.Groups[1].Value + 1)") }
Set-Content $g $t -NoNewline
Run "Rebuilding with the new version number" { .\gradlew.bat :app:assembleDebug }
New-Item -ItemType Directory -Force release | Out-Null
$apk = "release\SaveQuest-$Version.apk"
Copy-Item $apkSrc $apk -Force

git add -A
Write-Host ""; Write-Host "These changes will be published as $tag to ${slug}:" -ForegroundColor Cyan
git status --short
if((Read-Host "Type YES to publish") -ne "YES"){ Fail "Cancelled. Nothing was pushed (your version number edit in app\build.gradle.kts is still there)." }
git commit -m "Release $Version" -m $Notes
git push origin main;      if($LASTEXITCODE -ne 0){ Fail "Push failed. Sign in to GitHub if asked, then run again." }
git tag -a $tag -m $Notes; git push origin $tag; if($LASTEXITCODE -ne 0){ Fail "Pushing the tag failed." }

if(Get-Command gh -ErrorAction SilentlyContinue){
  gh release create $tag $apk --repo $slug --prerelease --title "SaveQuest $Version" --notes $Notes
  if($LASTEXITCODE -eq 0){ Write-Host "Published: https://github.com/$slug/releases/tag/$tag" -ForegroundColor Green; exit 0 }
  Write-Host "gh could not create the release (run 'gh auth login' once). Falling back to the website." -ForegroundColor Yellow
}
Write-Host ""; Write-Host "Last step by hand (30 seconds): the page opens, drag $apk into it, tick 'pre-release', press Publish." -ForegroundColor Green
Start-Process "https://github.com/$slug/releases/new?tag=$tag&title=SaveQuest%20$Version&prerelease=1"
Start-Process explorer.exe (Join-Path $root "release")
