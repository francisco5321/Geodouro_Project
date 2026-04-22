param(
    [string]$Config = "config.local.json"
)

$configObject = Get-Content -Raw -Path $Config | ConvertFrom-Json
$sshHost = $configObject.server.ssh_host
$remoteImagesPath = $configObject.server.remote_images_path.TrimEnd("/")
$rawDir = $configObject.dataset.raw_dir

New-Item -ItemType Directory -Force -Path $rawDir | Out-Null

if (Get-Command rsync -ErrorAction SilentlyContinue) {
    rsync -avz --progress "${sshHost}:${remoteImagesPath}/" "$rawDir/"
} else {
    scp -r "${sshHost}:${remoteImagesPath}/*" "$rawDir/"
}
