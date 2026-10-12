# Requires only Windows PowerShell 5.1+ and psql, included with PostgreSQL for Windows.
# Reads PostgreSQL in READ ONLY transactions. It never changes the database or Spring Boot files.
[CmdletBinding()]
param(
    [string]$ProjectPath = 'D:\Commitment_tracker\reality',
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$OutputPath = (Join-Path $env:USERPROFILE 'Downloads\Reality-localhost-backup.json'),
    [string]$TimeZone = 'Asia/Kolkata',
    [string]$PsqlPath,
    [Nullable[long]]$OwnerId,
    [switch]$Unowned,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Read-Secret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
function Resolve-Property([string]$Value) {
    if ($Value -match '^\$\{([^}:]+)(?::([^}]*))?\}$') {
        $environmentValue = [Environment]::GetEnvironmentVariable($Matches[1])
        if ($null -ne $environmentValue) { return $environmentValue }
        if ($Matches.ContainsKey(2)) { return $Matches[2] }
        return $null
    }
    return $Value.Replace('\:', ':').Replace('\=', '=')
}
function Quote-ProcessArgument([string]$Value) {
    # ProcessStartInfo.ArgumentList is unavailable in Windows PowerShell 5.1.
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
function Invoke-ReadOnlySql([string]$Sql) {
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $PsqlPath
    $arguments = @('--no-psqlrc', '--no-password', '--quiet', '--tuples-only', '--no-align',
        '--set=ON_ERROR_STOP=1', '--host', $databaseHost, '--port', $databasePort,
        '--username', $databaseUser, '--dbname', $databaseName)
    $start.Arguments = ($arguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = New-Object Text.UTF8Encoding $false
    $start.StandardErrorEncoding = New-Object Text.UTF8Encoding $false
    $start.EnvironmentVariables['PGPASSWORD'] = $databasePassword
    $start.EnvironmentVariables['PGCLIENTENCODING'] = 'UTF8'
    $start.EnvironmentVariables['PGCONNECT_TIMEOUT'] = '10'
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.WriteLine($Sql)
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(120000)) {
            $process.Kill()
            throw 'The read-only export exceeded two minutes. No database records were changed.'
        }
        $result = $outputTask.GetAwaiter().GetResult()
        $detail = $errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw ('PostgreSQL could not export this database. Check the database credentials and required Reality tables. ' + $detail.Trim())
        }
        return $result.Trim()
    } finally { $process.Dispose() }
}
function Get-Api([string]$Path) {
    try {
        $response = Invoke-WebRequest -Uri ($BaseUrl.TrimEnd('/') + $Path) -Headers $apiHeaders -UseBasicParsing -TimeoutSec 30
        return ConvertFrom-Json -InputObject $response.Content
    } catch {
        throw ('Unable to read ' + $Path + ' from your running localhost application. Keep Spring Boot running during export. For an authenticated backend, set REALITY_EXPORT_TOKEN privately in this PowerShell session. No database data was changed.')
    }
}
function Id-List($Rows) { (@($Rows) | ForEach-Object { [long]$_.id } | Sort-Object) -join ',' }

if (($null -ne $OwnerId) -and $Unowned) { throw 'Choose OwnerId or Unowned, not both.' }
if (($null -ne $OwnerId) -and ([long]$OwnerId) -le 0) { throw 'OwnerId must be positive.' }
if ($TimeZone -notmatch '^[A-Za-z0-9_+./-]{1,100}$') { throw 'Enter the source JVM timezone, such as Asia/Kolkata.' }
$backendUri = [Uri]$BaseUrl
if (-not $backendUri.IsLoopback -or $backendUri.Scheme -notin @('http', 'https') -or $backendUri.UserInfo) {
    throw 'This exporter is for your own localhost backend. BaseUrl must use localhost or a loopback IP without embedded credentials.'
}
if ([IO.File]::Exists($OutputPath) -and -not $Force) { throw 'The output file already exists. Choose another OutputPath or supply -Force.' }
$propertiesPath = Join-Path $ProjectPath 'src\main\resources\application.properties'
if (-not [IO.File]::Exists($propertiesPath)) { throw 'ProjectPath must point to the Spring Boot project containing src/main/resources/application.properties.' }
$properties = @{}
foreach ($line in [IO.File]::ReadAllLines($propertiesPath)) {
    if ($line -match '^\s*([^#!][^=]*?)\s*=\s*(.*?)\s*$') { $properties[$Matches[1].Trim()] = Resolve-Property $Matches[2] }
}
$jdbc = $properties['spring.datasource.url']
if (-not $jdbc -or $jdbc -notmatch '^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)(?:\?.*)?$') {
    throw 'The project must contain a PostgreSQL JDBC URL in spring.datasource.url.'
}
$databaseHost = $Matches[1]
$databasePort = if ($Matches[2]) { $Matches[2] } else { '5432' }
$databaseName = [Uri]::UnescapeDataString($Matches[3])
$databaseUser = $properties['spring.datasource.username']
$databasePassword = $properties['spring.datasource.password']
if (-not $databaseUser) { $databaseUser = Read-Host 'PostgreSQL username' }
if (-not $databasePassword) { $databasePassword = Read-Secret 'PostgreSQL password (hidden)' }
if (-not $PsqlPath) {
    $command = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($command) { $PsqlPath = $command.Source }
    else {
        $candidate = Get-ChildItem -Path (Join-Path $env:ProgramFiles 'PostgreSQL\*\bin\psql.exe') -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($candidate) { $PsqlPath = $candidate.FullName }
    }
}
if (-not $PsqlPath -or -not [IO.File]::Exists($PsqlPath)) { throw 'psql.exe was not found. Supply -PsqlPath pointing to your PostgreSQL installation bin/psql.exe.' }
$apiHeaders = @{}
if ($env:REALITY_EXPORT_TOKEN) { $apiHeaders['Authorization'] = 'Bearer ' + $env:REALITY_EXPORT_TOKEN }
$apiActivities = @(Get-Api '/activities')
$apiSessions = @(Get-Api '/api/sessions')
$metadataSql = @'
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '90s';
SELECT jsonb_build_object(
 'sessionTable', to_regclass('public.session') IS NOT NULL,
 'breakTable', to_regclass('public.session_break') IS NOT NULL,
 'scheduleTable', to_regclass('public.activity_scheduled_days') IS NOT NULL,
 'owned', EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='activities' AND column_name='owner_id'),
 'owners', COALESCE((SELECT jsonb_agg(DISTINCT to_jsonb(a)->'owner_id') FROM public.activities a WHERE to_jsonb(a)->>'owner_id' IS NOT NULL),'[]'::jsonb)
);
COMMIT;
'@
$metadata = ConvertFrom-Json -InputObject (Invoke-ReadOnlySql $metadataSql)
if (-not $metadata.sessionTable -or -not $metadata.breakTable -or -not $metadata.scheduleTable) {
    throw 'This database is missing session, break or scheduling tables. It does not match the complete application currently running on localhost. Point ProjectPath to that application; incomplete history will not be exported.'
}
if ($metadata.owned -and -not ($null -ne $OwnerId) -and -not $Unowned) {
    throw 'This database supports separate accounts. Re-run with -OwnerId <your user ID> or -Unowned for the original shared history. Exporting everyone''s private data together is not permitted.'
}
if (-not $metadata.owned -and (($null -ne $OwnerId) -or $Unowned)) { throw 'This original shared database has no owner column. Omit OwnerId and Unowned.' }
$ownerCondition = 'TRUE'
if (($null -ne $OwnerId)) { $ownerCondition = "(to_jsonb(a)->>'owner_id')::bigint = " + ([long]$OwnerId) }
if ($Unowned) { $ownerCondition = "to_jsonb(a)->>'owner_id' IS NULL" }
$queryPath = Join-Path $PSScriptRoot 'reality-export.sql'
if (-not [IO.File]::Exists($queryPath)) { throw 'Keep reality-export.sql beside this PowerShell file.' }
$query = [IO.File]::ReadAllText($queryPath).Replace('__OWNER_CONDITION__', $ownerCondition).Replace('__SOURCE_TIMEZONE__', $TimeZone)
$jsonText = Invoke-ReadOnlySql $query
if ([Text.Encoding]::UTF8.GetByteCount($jsonText) -gt 20971520) { throw 'The snapshot exceeds the Android 20 MB import limit. No file was written.' }
$backup = ConvertFrom-Json -InputObject $jsonText
$selectedActivities = @($backup.activities)
$selectedSessions = @($backup.sessions)
$activeDatabaseIds = Id-List @($selectedActivities | Where-Object { $_.active })
$activeApiIds = Id-List @($apiActivities | Where-Object { $_.active })
if ($activeDatabaseIds -ne $activeApiIds -or (Id-List $selectedSessions) -ne (Id-List $apiSessions)) {
    throw 'The database snapshot does not match the active activities and session IDs returned by localhost. Check ProjectPath, owner selection and the running server. No backup was written.'
}
foreach ($apiActivity in $apiActivities) {
    $match = @($selectedActivities | Where-Object { $_.id -eq $apiActivity.id })
    if ($match.Count -ne 1 -or $match[0].name -cne $apiActivity.name -or $match[0].minimumDuration -ne $apiActivity.minimumDuration -or $match[0].category -cne $apiActivity.category) {
        throw 'The localhost activities do not match this database. No backup was written.'
    }
}
foreach ($apiSession in $apiSessions) {
    $match = @($selectedSessions | Where-Object { $_.id -eq $apiSession.id })
    if ($match.Count -ne 1 -or $match[0].activityId -ne $apiSession.activityId -or ([DateTime]$match[0].startTime) -ne ([DateTime]$apiSession.startTime)) {
        throw 'The localhost session history does not match this database. No backup was written.'
    }
}
# Streaks are calculated from the consistent database snapshot, then compared with the actual server.
foreach ($streak in @($backup.streaks)) {
    $current = Get-Api ('/api/streaks/activity/' + $streak.activityId)
    if ($current.currentStreak -ne $streak.currentStreak -or $current.lastEvaluatedDate -ne $streak.lastEvaluatedDate) {
        throw 'The server streak does not match the exported history. Check source TimeZone and wait until any running changes finish before exporting. No backup was written.'
    }
}
$absoluteOutput = [IO.Path]::GetFullPath($OutputPath)
$directory = [IO.Path]::GetDirectoryName($absoluteOutput)
if (-not [IO.Directory]::Exists($directory)) { [void][IO.Directory]::CreateDirectory($directory) }
[IO.File]::WriteAllText($absoluteOutput, $jsonText, (New-Object Text.UTF8Encoding $false))
$databasePassword = $null
$apiHeaders.Clear()
Write-Host ('Saved ' + $selectedActivities.Count + ' activities (including archived), ' + $selectedSessions.Count + ' sessions, ' + @($backup.breaks).Count + ' breaks, and current streak snapshots.')
Write-Host ('Backup: ' + $absoluteOutput)
Write-Host 'Transfer this JSON file to your phone, then open Reality Offline > Settings > Backup and restore > Choose backup file.'
