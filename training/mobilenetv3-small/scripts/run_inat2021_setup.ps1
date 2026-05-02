param(
    [string]$Config = "config.local.json"
)

$ErrorActionPreference = "Stop"

$repoDir = Split-Path -Parent $PSScriptRoot
Set-Location $repoDir
$repoDir = (Resolve-Path $repoDir).Path

$configPath = Join-Path $repoDir $Config
if (-not (Test-Path $configPath)) {
    throw "Config not found: $configPath"
}

$config = Get-Content $configPath -Raw | ConvertFrom-Json
$inatRoot = Join-Path $repoDir $config.dataset.inat_root
$trainArchive = Join-Path $inatRoot "2021_train_mini.tgz"
$validArchive = Join-Path $inatRoot "2021_valid.tgz"
$trainExtractedDir = Join-Path $inatRoot "train_mini"
$validExtractedDir = Join-Path $inatRoot "val"
$trainFinalDir = Join-Path $inatRoot "2021_train_mini"
$validFinalDir = Join-Path $inatRoot "2021_valid"

$trainUrl = "https://ml-inat-competition-datasets.s3.amazonaws.com/2021/train_mini.tar.gz"
$validUrl = "https://ml-inat-competition-datasets.s3.amazonaws.com/2021/val.tar.gz"
$trainExpectedBytes = 44636137542L
$validExpectedBytes = 8210359772L

function Download-Archive {
    param(
        [string]$Url,
        [string]$ArchivePath,
        [Int64]$ExpectedBytes
    )

    if (Test-Path $ArchivePath) {
        $existingSize = (Get-Item $ArchivePath).Length
        if ($existingSize -eq $ExpectedBytes) {
            Write-Output "Archive already verified: $ArchivePath"
            return
        }

        Write-Output "Resuming partial archive: $ArchivePath ($existingSize bytes)"
    }

    Write-Output "Downloading $Url"
    & curl.exe -L -C - -o $ArchivePath $Url
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed for $Url"
    }

    $downloadedSize = (Get-Item $ArchivePath).Length
    if ($downloadedSize -ne $ExpectedBytes) {
        throw "Downloaded size mismatch for $ArchivePath. Expected $ExpectedBytes, got $downloadedSize."
    }
}

function Expand-And-Rename {
    param(
        [string]$ArchivePath,
        [string]$ExtractedDir,
        [string]$FinalDir
    )

    if (Test-Path $FinalDir) {
        Write-Output "Dataset already extracted: $FinalDir"
        if (Test-Path $ArchivePath) {
            Remove-Item -LiteralPath $ArchivePath -Force
        }
        return
    }

    if (Test-Path $ExtractedDir) {
        Write-Output "Removing partial directory: $ExtractedDir"
        Remove-Item -LiteralPath $ExtractedDir -Recurse -Force
    }

    Write-Output "Extracting $ArchivePath"
    $archiveResolved = (Resolve-Path $ArchivePath).Path
    $rootResolved = (Resolve-Path $inatRoot).Path
    & tar -xf $archiveResolved -C $rootResolved
    if ($LASTEXITCODE -ne 0) {
        throw "tar extraction failed for $ArchivePath"
    }

    if (-not (Test-Path $ExtractedDir)) {
        throw "Expected extracted directory not found: $ExtractedDir"
    }

    Rename-Item -LiteralPath $ExtractedDir -NewName (Split-Path $FinalDir -Leaf)
    Remove-Item -LiteralPath $ArchivePath -Force
}

New-Item -ItemType Directory -Force -Path $inatRoot | Out-Null

Download-Archive -Url $trainUrl -ArchivePath $trainArchive -ExpectedBytes $trainExpectedBytes
Expand-And-Rename -ArchivePath $trainArchive -ExtractedDir $trainExtractedDir -FinalDir $trainFinalDir

Download-Archive -Url $validUrl -ArchivePath $validArchive -ExpectedBytes $validExpectedBytes
Expand-And-Rename -ArchivePath $validArchive -ExtractedDir $validExtractedDir -FinalDir $validFinalDir

Write-Output "Generating CSV splits"
& python .\scripts\prepare_inat2021_splits.py --config $Config
if ($LASTEXITCODE -ne 0) {
    throw "prepare_inat2021_splits.py failed"
}

Write-Output "iNaturalist 2021 setup finished successfully."
