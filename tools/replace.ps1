param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$PatchFile,
    [int]$ExpectedCount = 1
)

# Applies a patch file to a source file, tolerating any mix of CRLF/LF line
# endings by normalising the search text and the file content.
#
# The patch file format is:
#   <<<<<<< OLD
#   ...old text...
#   =======
#   ...new text...
#   >>>>>>> NEW
#
# The markers are stripped, so the old/new blocks may contain any text as long
# as they do not contain the marker lines themselves.

$full = (Resolve-Path -LiteralPath $Path).Path
$raw = [System.IO.File]::ReadAllText($full)

$patch = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $PatchFile).Path)
$patch = $patch -replace "`r`n", "`n"

$oldMarker = '<<<<<<< OLD'
$midMarker  = '======='
$newMarker  = '>>>>>>> NEW'

$oldIdx = $patch.IndexOf($oldMarker)
$midIdx = $patch.IndexOf($midMarker, $oldIdx + $oldMarker.Length)
$newIdx = $patch.IndexOf($newMarker, $midIdx + $midMarker.Length)

if ($oldIdx -lt 0 -or $midIdx -lt 0 -or $newIdx -lt 0) {
    Write-Error "Patch file $PatchFile is missing one of the markers"
    exit 1
}

$oldNorm = $patch.Substring($oldIdx + $oldMarker.Length, $midIdx - ($oldIdx + $oldMarker.Length)).Trim("`n")
$newNorm = $patch.Substring($midIdx + $midMarker.Length, $newIdx - ($midIdx + $midMarker.Length)).Trim("`n")
$rawNorm = $raw -replace "`r`n", "`n"

$count = ([regex]::Matches($rawNorm, [regex]::Escape($oldNorm))).Count
if ($count -ne $ExpectedCount) {
    Write-Error "Expected $ExpectedCount match(es) but found $count in $Path"
    exit 1
}

$result = $rawNorm.Replace($oldNorm, $newNorm)
[System.IO.File]::WriteAllText($full, $result)
Write-Output "OK: replaced $count occurrence(s) in $Path"