# 全量测试入口（Windows 用）。Linux / macOS / CI 用 deploy/tools/test-all.sh。
#
#   pwsh -File deploy/tools/test-all.ps1
#
# 顺序：后端编译 → 后端回归测试 → 前端页面测试 → 契约校验 → 数据资产接线检查。
# 注意：后端 Store/Reminder/Sensor 测试会写 backend/var/，DSH 沙箱下需要
#       danger-full-access；普通终端（管理员或对仓库有写权限）直接跑即可。
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

function Invoke-Step([string]$Name, [scriptblock]$Block) {
    Write-Host "== $Name =="
    & $Block
    if ($LASTEXITCODE -ne 0) {
        throw "$Name 失败（exit $LASTEXITCODE）"
    }
}

Invoke-Step "1/5 后端编译" {
    New-Item -ItemType Directory -Force -Path "backend\build\classes" | Out-Null
    $src = Get-ChildItem backend\src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    javac -encoding UTF-8 -d backend\build\classes $src
}

Invoke-Step "2/5 后端回归测试" {
    # Windows 上直接跑 Java，逻辑与 backend/test/run.sh 一致
    $out = "backend\build\test-classes"
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $tests = Get-ChildItem backend\test\com -Recurse -Filter *Test.java | ForEach-Object { $_.FullName }
    javac -encoding UTF-8 -cp "backend\build\classes" -d $out $tests
    $cp = "backend\build\classes;$out"
    foreach ($t in $tests) {
        $cls = ($t -replace [regex]::Escape("$Root\backend\test\"), "") -replace "\\", "." -replace "\.java$", ""
        Write-Host "--- $cls ---"
        java -cp $cp $cls
        if ($LASTEXITCODE -ne 0) { throw "$cls 失败" }
    }
}

Invoke-Step "3/5 前端页面测试" {
    node frontend/tests/pages.test.js
}

Invoke-Step "4/5 契约校验" {
    node shared/constants/validate-contracts.js
}

Invoke-Step "5/5 数据资产接线检查" {
    python deploy/tools/check-data-assets.py
}

Write-Host "全部通过。"
