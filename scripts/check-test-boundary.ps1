param(
    [int]$Baseline = 456,
    [switch]$Strict
)

$testRoot = Join-Path $PSScriptRoot '..\src\test\java'
$rows = [System.Collections.Generic.List[object]]::new()

Get-ChildItem $testRoot -Recurse -Filter *.java | ForEach-Object {
    $relative = $_.FullName.Substring((Resolve-Path $testRoot).Path.Length + 1)
    $consumerMatch = [regex]::Match($relative, 'modules[\\/]([^\\/]+)')
    if (-not $consumerMatch.Success) { return }
    $consumer = $consumerMatch.Groups[1].Value

    Select-String -Path $_.FullName -Pattern '^import com\.fptu\.exe\.skillswap\.modules\.([^\.]+)\.(domain|repository|service|controller|dto)' | ForEach-Object {
        $match = [regex]::Match($_.Line, 'modules\.([^\.]+)\.([^\.]+)')
        if ($match.Success -and $match.Groups[1].Value -ne $consumer) {
            $rows.Add([pscustomobject]@{
                Consumer = $consumer
                Owner = $match.Groups[1].Value
                Layer = $match.Groups[2].Value
                File = $relative
                Import = $_.Line.Trim()
            })
        }
    }
}

$distinct = @($rows | Sort-Object Consumer, Owner, Import -Unique)
Write-Output "cross-module internal test imports: $($rows.Count)"
Write-Output "distinct consumer/owner/type edges: $($distinct.Count)"
$rows | Group-Object Consumer, Owner, Layer | Sort-Object Count -Descending | Select-Object -First 20 |
    ForEach-Object { Write-Output ("{0}: {1}" -f $_.Name, $_.Count) }

if ($Strict -and $distinct.Count -gt $Baseline) {
    throw "Test boundary regressed: $($distinct.Count) distinct edges > baseline $Baseline"
}
