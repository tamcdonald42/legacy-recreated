$ErrorActionPreference = 'Continue'
Set-Location 'H:\ghidra\septerra_core'

$p = Start-Process -FilePath '.\SR-Septerra.exe' -WorkingDirectory 'H:\ghidra\septerra_core' -PassThru
$exited = $p.WaitForExit(8000)
if (-not $exited) {
    Write-Output 'STILL_RUNNING after 8s'
    Stop-Process -Id $p.Id -Force
} else {
    Write-Output ("EXITED code=" + $p.ExitCode)
}

Write-Output '--- sessions ---'
& qwinsta 2>&1 | Out-String
Write-Output '--- recent app errors mentioning Septerra ---'
Get-WinEvent -FilterHashtable @{LogName='Application'; StartTime=(Get-Date).AddMinutes(-10)} -ErrorAction SilentlyContinue |
  Where-Object { $_.Message -match 'Septerra' } |
  Select-Object -First 3 TimeCreated, ProviderName, @{n='Msg';e={$_.Message.Substring(0,[Math]::Min(600,$_.Message.Length))}} |
  Format-List | Out-String
