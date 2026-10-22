param(
  [string]$TokenFile = "D:\Projects\Apps\Tellme\tools\idtoken.txt",
  [string]$Uid = "PDE9EWjxT1TUSEfAHI6oUhKwpb73",
  [string]$OtherUid = "",
  [string]$OtherTokenFile = "D:\Projects\Apps\Tellme\tools\idtoken2.txt"
)
$ErrorActionPreference = "Continue"
$db = 'https://tellme-74999-default-rtdb.us-central1.firebasedatabase.app'
$tokA = (Get-Content $TokenFile -Raw).Trim()
$tokB = $null
if (Test-Path $OtherTokenFile) { $tokB = (Get-Content $OtherTokenFile -Raw).Trim() }

function Raw($path, $method, $body, $t) {
  $u = "$db/$path.json?auth=$t"
  try {
    if ($method -eq 'GET') {
      $r = Invoke-WebRequest -Uri $u -Method GET -TimeoutSec 20 -UseBasicParsing
      return "$($r.StatusCode)"
    } else {
      $r = Invoke-WebRequest -Uri $u -Method $method -Body $body -ContentType 'application/json' -TimeoutSec 20 -UseBasicParsing
      return "$($r.StatusCode)"
    }
  } catch {
    return "$($_.Exception.Response.StatusCode.value__)"
  }
}

$A = $Uid
$B = $OtherUid

Write-Output "### A = $A"
Write-Output "### B = $B"
Write-Output ""
Write-Output "--- P1: usernames with CORRECT self uid ---"
Write-Output ("  A PUT usernames/probe_user  {uid:A} => " + (Raw 'usernames/probe_user' 'PUT' (@{uid=$A}|ConvertTo-Json -Compress) $tokA))
Write-Output ("  A PUT usernames/probe_user2 {uid:A} => " + (Raw 'usernames/probe_user2' 'PUT' (@{uid=$A}|ConvertTo-Json -Compress) $tokA))
Write-Output ("  A GET usernames/probe_user  => " + (Raw 'usernames/probe_user' 'GET' $null $tokA))
Write-Output ("  A GET usernames/probe_user2 => " + (Raw 'usernames/probe_user2' 'GET' $null $tokA))
Write-Output ("  A GET usernames              => " + (Raw 'usernames' 'GET' $null $tokA))

Write-Output ""
Write-Output "--- P2: cross-user writes with CORRECT self uid ---"
Write-Output ("  A PUT users/B/friendRequests/A {uid:A} => " + (Raw "users/$B/friendRequests/$A" 'PUT' (@{uid=$A;fullName='A';username='a'}|ConvertTo-Json -Compress) $tokA))
Write-Output ("  A PUT users/B/sentRequests/A   {uid:A} => " + (Raw "users/$B/sentRequests/$A" 'PUT' (@{uid=$A}|ConvertTo-Json -Compress) $tokA))
Write-Output ("  A PUT users/B/friends/A         {uid:A} => " + (Raw "users/$B/friends/$A" 'PUT' (@{uid=$A}|ConvertTo-Json -Compress) $tokA))
Write-Output ("  A PUT users/B                   {uid:A} => " + (Raw "users/$B" 'PUT' (@{uid=$A}|ConvertTo-Json -Compress) $tokA))

Write-Output ""
Write-Output "--- P3: multi-path updateChildren (root PATCH) as A ---"
$multi = @{ "users/$A/friends/$B" = @{uid=$B}; "users/$B/friendRequests/$A" = @{uid=$A} } | ConvertTo-Json -Compress
Write-Output ("  A PATCH / => " + (Raw '' 'PATCH' $multi $tokA))
$multi2 = @{ "users/$A/sentRequests/$B" = @{uid=$B}; "users/$B/friendRequests/$A" = @{uid=$A} } | ConvertTo-Json -Compress
Write-Output ("  A PATCH / (2) => " + (Raw '' 'PATCH' $multi2 $tokA))

Write-Output ""
Write-Output "--- P4: read own subtree as A ---"
foreach ($p in @("users/$A", "users/$A/friends", "users/$A/friendRequests", "users/$A/sentRequests", "users/$A/username", "users/$A/friends/$B")) {
  Write-Output ("  A GET $p => " + (Raw $p 'GET' $null $tokA))
}
Write-Output ""
Write-Output "--- P5: read B's subtree as A ---"
foreach ($p in @("users/$B", "users/$B/friends", "users/$B/friendRequests", "users/$B/sentRequests")) {
  Write-Output ("  A GET $p => " + (Raw $p 'GET' $null $tokA))
}

Write-Output ""
Write-Output "--- P6: root PATCH with ONLY own-subtree paths ---"
$own = @{ "users/$A/friends/$B" = @{uid=$B}; "users/$A/sentRequests/$B" = @{uid=$B} } | ConvertTo-Json -Compress
Write-Output ("  A PATCH / (own only) => " + (Raw '' 'PATCH' $own $tokA))
$own2 = @{ "users/$A/friendRequests/$B" = @{uid=$B} } | ConvertTo-Json -Compress
Write-Output ("  A PATCH / (own only 2) => " + (Raw '' 'PATCH' $own2 $tokA))

Write-Output ""
Write-Output "--- P7: PATCH at users/$A level (no root) ---"
Write-Output ("  A PATCH users/A => " + (Raw "users/$A" 'PATCH' (@{fullName='Probe A2'}|ConvertTo-Json -Compress) $tokA))

Write-Output ""
Write-Output "--- P8: other DB instance URLs ---"
foreach ($host in @('tellme-74999-default-rtdb.firebaseio.com','tellme-74999.firebaseio.com','tellme-74999-default-rtdb.europe-west1.firebasedatabase.app','tellme-74999-default-rtdb.asia-southeast1.firebasedatabase.app')) {
  $u = "https://$host/.json"
  try {
    $r = Invoke-WebRequest -Uri $u -Method GET -TimeoutSec 15 -UseBasicParsing
    Write-Output ("  $host => $($r.StatusCode) len=$($r.Content.Length)")
  } catch {
    Write-Output ("  $host => $($_.Exception.Response.StatusCode.value__)")
  }
}
