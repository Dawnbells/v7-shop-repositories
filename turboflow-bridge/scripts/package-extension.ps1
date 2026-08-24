$ErrorActionPreference = 'Stop'

$extensionRoot = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $extensionRoot 'sidepanel.zip'
$temporaryPath = Join-Path $extensionRoot 'sidepanel.tmp.zip'

# 显式列出运行时文件，避免把测试、开发文件或旧 ZIP 再嵌进安装包。
$packageEntries = @(
  'background.js'
  'bridge-stats.js'
  'content-isolated.js'
  'content-main.js'
  'flow-api.js'
  'flow-dom-method.js'
  'icons'
  'image-digest.js'
  'manifest.json'
  'policy-fallback-state.js'
  'sidepanel.css'
  'sidepanel.html'
  'sidepanel.js'
  'task-error-policy.js'
  'translated-image-cache.js'
)

$requiredArchiveEntries = @(
  'background.js'
  'bridge-stats.js'
  'image-digest.js'
  'manifest.json'
  'sidepanel.html'
  'sidepanel.js'
  'translated-image-cache.js'
)

foreach ($entry in $packageEntries) {
  $sourcePath = Join-Path $extensionRoot $entry
  if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Missing extension package entry: $entry"
  }
}

if (Test-Path -LiteralPath $temporaryPath) {
  Remove-Item -LiteralPath $temporaryPath -Force
}

Push-Location $extensionRoot
try {
  Compress-Archive -Path $packageEntries -DestinationPath $temporaryPath -CompressionLevel Optimal
} finally {
  Pop-Location
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($temporaryPath)
try {
  $archivedNames = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
  foreach ($requiredEntry in $requiredArchiveEntries) {
    if ($requiredEntry -notin $archivedNames) {
      throw "Generated extension package is missing: $requiredEntry"
    }
  }
} finally {
  $archive.Dispose()
}

Move-Item -LiteralPath $temporaryPath -Destination $outputPath -Force
Write-Output "Created $outputPath"
