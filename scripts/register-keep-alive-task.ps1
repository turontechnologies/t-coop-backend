# Registers a Windows Scheduled Task that runs keep-alive.ps1 at log-on and
# then every 5 minutes indefinitely, so the backend stack comes up when the
# machine starts and self-heals if Docker/ngrok crash later.
#
# Run once to set up: powershell -ExecutionPolicy Bypass -File scripts\register-keep-alive-task.ps1
# To remove: Unregister-ScheduledTask -TaskName "TCoopBackendKeepAlive" -Confirm:$false

$TaskName    = "TCoopBackendKeepAlive"
$ScriptPath  = "C:\Users\HomePC\Documents\work\t-coop-backend\scripts\keep-alive.ps1"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$ScriptPath`""

$logonTrigger  = New-ScheduledTaskTrigger -AtLogOn
$repeatTrigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Minutes 5) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $TaskName `
    -Action $action `
    -Trigger @($logonTrigger, $repeatTrigger) `
    -Settings $settings `
    -Description "Keeps the t-coop-backend Docker stack and ngrok tunnel running" `
    -Force

Write-Output "Registered scheduled task '$TaskName' - runs at log-on and every 5 minutes."
