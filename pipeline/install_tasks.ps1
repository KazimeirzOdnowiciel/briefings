# Registers the briefing schedule in Windows Task Scheduler under \Briefing\.
# Re-running replaces the tasks. Remove them all with:  .\install_tasks.ps1 -Remove
param([switch]$Remove)

$ErrorActionPreference = 'Stop'
$folder = '\Briefing\'
$code = $PSScriptRoot
$pyw = (Get-Command pythonw.exe -ErrorAction SilentlyContinue).Source
$real = Join-Path $env:LOCALAPPDATA 'Python\pythoncore-3.14-64\pythonw.exe'
if (Test-Path $real) { $pyw = $real }   # the real interpreter, not the App Execution Alias
if (-not $pyw) { throw 'pythonw.exe not found' }

if ($Remove) {
  Get-ScheduledTask -TaskPath $folder -ErrorAction SilentlyContinue | Unregister-ScheduledTask -Confirm:$false
  Write-Output 'removed'
  return
}

$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
function Settings([int]$hours, [switch]$wake) {
  $s = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -ExecutionTimeLimit (New-TimeSpan -Hours $hours) -MultipleInstances IgnoreNew
  if ($wake) { $s.WakeToRun = $true }
  return $s
}
function Add-Briefing([string]$name, [string]$script, $trigger, [int]$hours = 1, [switch]$wake, [string]$desc) {
  $action = New-ScheduledTaskAction -Execute $pyw -Argument "`"$code\task.py`" $script" -WorkingDirectory $code
  Register-ScheduledTask -TaskPath $folder -TaskName $name -Action $action -Trigger $trigger `
    -Principal $principal -Settings (Settings $hours -wake:$wake) -Description $desc -Force | Out-Null
  Write-Output "registered $folder$name"
}

Add-Briefing 'Morning briefing' 'run_daily.py' (New-ScheduledTaskTrigger -Daily -At '07:30') -wake `
  -desc 'Writes, renders and publishes the daily briefing, then sends the Telegram digest.'
Add-Briefing 'Canary' 'canary.py' (New-ScheduledTaskTrigger -Daily -At '08:50') `
  -desc 'Warns on Telegram when today''s edition is not live; pushes to the phone when it is.'
Add-Briefing 'Workout reminder' 'workout_send.py' `
  (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Tuesday,Wednesday,Friday,Saturday -At '17:00') `
  -desc 'Sends today''s Hevy routine to Telegram on training days.'
Add-Briefing 'Weekly review' 'run_weekly.py' (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At '09:00') `
  -desc 'Week-in-review summary of the last seven editions, to Telegram.'
