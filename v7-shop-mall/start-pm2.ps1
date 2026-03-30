$ProjectDir = "E:\V7Soft\Repositories\v7-shop-repositories\v7-shop-mall"
$EnvFile = "$ProjectDir\.env"

Set-Location $ProjectDir

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()

        if ([string]::IsNullOrWhiteSpace($line)) { return }
        if ($line.StartsWith("#")) { return }

        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            $name = $parts[0].Trim()
            $value = $parts[1].Trim()

            # 去掉包裹的单双引号
            if (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))
            ) {
                $value = $value.Substring(1, $value.Length - 2)
            }

            Set-Item -Path "Env:$name" -Value $value
            Write-Host "Loaded env: $name=$value"
        }
    }
} else {
    Write-Host ".env not found: $EnvFile"
    exit 1
}

pm2 delete v7-shop-mall 2>$null
pm2 start ecosystem.config.cjs --update-env
pm2 status