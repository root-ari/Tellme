param(
  [string]$TokenFile = "D:\Projects\Apps\Tellme\tools\idtoken.txt",
  [string]$Uid = "PDE9EWjxT1TUSEfAHI6oUhKwpb73",
  [string]$OtherUid = "",
  [string]$OtherTokenFile = "D:\Projects\Apps\Tellme\tools\idtoken2.txt"
)
$ErrorActionPreference = "Continue"
$db = 'https://tellme-74999-default-rtdb.us-central1.firebasedatabase.app'
$tok = (Get-Content $TokenFile -Raw).Trim()
$otherTok = $null
if (Test-Path $OtherTokenFile) { $otherTok = (Get-Content $OtherTokenFile -Raw).Trim() }

function Probe($path, $method, $payload, $useOther) {
  $t = if ($useOther) { $otherTok } else { $tok }
  $u = "$db/$path.json?auth=$t"
  $body = $null
  if ($payload -ne $null) { $body = $payload }
  $who = if ($useOther) { 'B' } else { 'A' }
  try {
    if ($method -eq 'GET') {
      $r = Invoke-WebRequest -Uri $u -Method GET -TimeoutSec 20 -UseBasicParsing
      "[$who] GET  $path => $($r.StatusCode)"
    } else {
      $r = Invoke-WebRequest -Uri $u -Method $method -Body $body -ContentType 'application/json' -TimeoutSec 20 -UseBasicParsing
      "[$who] $($method.ToUpper())  $path => $($r.StatusCode)"
    }
  } catch {
    $code = $_.Exception.Response.StatusCode.value__
    "[$who] $($method.ToUpper())  $path => $code"
  }
}

$A = $Uid
$B = $OtherUid
$j = @{ uid = 'X' } | ConvertTo-Json -Compress

Write-Output "### A = $A"
Write-Output "### B = $B"
Write-Output ""
Write-Output "--- READS by A ---"
Probe 'users' 'GET' $null $false
Probe 'usernames' 'GET' $null $false
Probe "users/$A" 'GET' $null $false
Probe "users/$B" 'GET' $null $false
Probe "users/$B/friends" 'GET' $null $false
Probe "users/$A/friends" 'GET' $null $false
Probe "users/$A/friendRequests" 'GET' $null $false
Probe "users/$A/sentRequests" 'GET' $null $false
Probe "usernames/probe_user" 'GET' $null $false
Write-Output ""
Write-Output "--- WRITES by A (own subtree) ---"
Probe "users/$A" 'PUT' $j $false
Probe "users/$A/friends/$B" 'PUT' $j $false
Probe "users/$A/friendRequests/$B" 'PUT' $j $false
Probe "users/$A/sentRequests/$B" 'PUT' $j $false
Write-Output ""
Write-Output "--- WRITES by A (other user's subtree) ---"
Probe "users/$B" 'PUT' $j $false
Probe "users/$B/friends/$A" 'PUT' $j $false
Probe "users/$B/friendRequests/$A" 'PUT' $j $false
Probe "users/$B/sentRequests/$A" 'PUT' $j $false
Write-Output ""
Write-Output "--- WRITES by A to usernames ---"
Probe 'usernames/probe_user' 'PUT' $j $false
Probe 'usernames' 'PUT' $j $false
