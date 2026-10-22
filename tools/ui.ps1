param(
  [Parameter(Mandatory=$true)][string]$Action,
  [string]$ResourceId,
  [string]$Text,
  [int]$Index = 0,
  [string]$DumpFile = "D:\Projects\Apps\Tellme\tools\ui.xml"
)

$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Get-Ui {
  & $adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
  & $adb pull /sdcard/ui.xml $DumpFile 2>&1 | Out-Null
  return (Get-Content $DumpFile -Raw)
}

switch ($Action) {
  "dump" {
    $x = Get-Ui
    [regex]::Matches($x, '(?:text|content-desc|resource-id)="([^"]*)"') |
      ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' } | Select-Object -Unique
  }
  "bounds" {
    # Usage: -Action bounds -ResourceId com.ari.tellme:id/button_sign_up
    $x = Get-Ui
    $rx = 'resource-id="' + [regex]::Escape($ResourceId) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($x, $rx)
    if (-not $m.Success) {
      $rx2 = 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="' + [regex]::Escape($ResourceId) + '"'
      $m = [regex]::Match($x, $rx2)
    }
    if ($m.Success) {
      $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
      $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
      "CENTER: $((($x1+$x2)/2)) $((($y1+$y2)/2))"
    } else { "NOTFOUND" }
  }
  "tapid" {
    $x = Get-Ui
    $rx = 'resource-id="' + [regex]::Escape($ResourceId) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($x, $rx)
    if (-not $m.Success) { "NOTFOUND"; exit 1 }
    $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    & $adb shell input tap $cx $cy
    "TAPPED $ResourceId at $cx $cy"
  }
  "taptext" {
    $x = Get-Ui
    $rx = 'text="' + [regex]::Escape($Text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($x, $rx)
    if (-not $m.Success) { "NOTFOUND"; exit 1 }
    $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    & $adb shell input tap $cx $cy
    "TAPPED '$Text' at $cx $cy"
  }
  "type" {
    & $adb shell input text $Text
    "TYPED $Text"
  }
  "fill" {
    # Usage: -Action fill -Text "field1|val1|field2|val2"
    # Each field key is matched against resource-id first, then against the
    # EditText hint / current text.
    $pairs = $Text -split '\|'
    for ($i = 0; $i -lt $pairs.Count - 1; $i += 2) {
      $key = $pairs[$i]
      $val = $pairs[$i + 1]
      $x = Get-Ui
      $rx = 'resource-id="' + [regex]::Escape($key) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
      $m = [regex]::Match($x, $rx)
      if (-not $m.Success) {
        $rx = 'text="' + [regex]::Escape($key) + '"[^>]*class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        $m = [regex]::Match($x, $rx)
      }
      if (-not $m.Success) {
        $rx = 'class="android.widget.EditText"[^>]*text="' + [regex]::Escape($key) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        $m = [regex]::Match($x, $rx)
      }
      if (-not $m.Success) { "NOTFOUND $key"; continue }
      $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
      $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
      & $adb shell input tap $cx $cy
      Start-Sleep -Milliseconds 400
      $esc = $val -replace '&', '\&' -replace ' ', '%s'
      & $adb shell input text $esc
      Start-Sleep -Milliseconds 300
      "FILLED $key = $val"
    }
  }
  "fillseq" {
    # Usage: -Action fillseq -Text "val1|val2|val3"
    # Taps the first EditText, then walks forward with TAB (KEYCODE_TAB),
    # typing each value in turn. Deterministic and immune to the soft
    # keyboard shifting the layout.
    $vals = $Text -split '\|'
    $x = Get-Ui
    $m = [regex]::Match($x, 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { "NOTFOUND first edittext"; exit 1 }
    $cx = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    & $adb shell input tap $cx $cy
    Start-Sleep -Milliseconds 600
    for ($i = 0; $i -lt $vals.Count; $i++) {
      if ($i -gt 0) {
        & $adb shell input keyevent 61   # KEYCODE_TAB
        Start-Sleep -Milliseconds 500
      }
      $esc = $vals[$i] -replace '&', '\&' -replace ' ', '%s'
      & $adb shell input text $esc
      Start-Sleep -Milliseconds 400
      "TYPED [$i] = $($vals[$i])"
    }
  }
  "activity" {
    & $adb shell dumpsys activity activities 2>&1 | Select-String 'topResumedActivity' | Select-Object -First 1 | ForEach-Object { $_.Line.Trim() }
  }
  "toast" {
    & $adb logcat -d 2>&1 | Select-String -Pattern 'Toast|toast' | Select-Object -Last 15 | ForEach-Object { $_.Line }
  }
  "back" {
    & $adb shell input keyevent 4
    "BACK"
  }
  "logclear" {
    & $adb logcat -c
    "CLEARED"
  }
  default { "UNKNOWN ACTION $Action" }
}
