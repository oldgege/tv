@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================
REM   MP4 批量压缩脚本
REM   适用于 Android TV 应用（minSdk 18）
REM ============================================

REM 输出目录
set OUTPUT_DIR=compressed

REM 压缩参数
set SCALE=1280:720
set CRF=28
set PRESET=slow
set AUDIO_BITRATE=96k
set PROFILE=baseline
set LEVEL=3.1

REM 检查 FFmpeg 是否可用
where ffmpeg >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 ffmpeg 命令，请先安装 FFmpeg 并配置环境变量 PATH
    pause
    exit /b 1
)

REM 创建输出目录
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo ============================================
echo   开始批量压缩 MP4 文件
echo   输出目录: %OUTPUT_DIR%
echo   分辨率: %SCALE%
echo   CRF: %CRF%
echo ============================================
echo.

set COUNT=0
set SUCCESS=0
set FAILED=0

REM 遍历所有 mp4 文件
for %%f in (*.mp4) do (
    set /a COUNT+=1
    echo [!COUNT!] 正在压缩: %%f
    
    ffmpeg -y -i "%%f" ^
        -vf "scale=%SCALE%:force_original_aspect_ratio=decrease,pad=%SCALE%:(ow-iw)/2:(oh-ih)/2" ^
        -c:v libx264 ^
        -profile:v %PROFILE% ^
        -level %LEVEL% ^
        -crf %CRF% ^
        -preset %PRESET% ^
        -c:a aac ^
        -b:a %AUDIO_BITRATE% ^
        -ac 1 ^
        -movflags +faststart ^
        "%OUTPUT_DIR%\%%f" >nul 2>&1
    
    if errorlevel 1 (
        echo    [失败] %%f
        set /a FAILED+=1
    ) else (
        REM 获取文件大小
        for %%a in ("%%f") do set ORIG_SIZE=%%~za
        for %%b in ("%OUTPUT_DIR%\%%f") do set NEW_SIZE=%%~zb
        
        REM 转换为 KB
        set /a ORIG_KB=!ORIG_SIZE!/1024
        set /a NEW_KB=!NEW_SIZE!/1024
        
        echo    [成功] !ORIG_KB! KB -^> !NEW_KB! KB
        set /a SUCCESS+=1
    )
)

echo.
echo ============================================
echo   压缩完成
echo   总数: %COUNT%
echo   成功: %SUCCESS%
echo   失败: %FAILED%
echo ============================================
echo.
echo 压缩后的文件在 %OUTPUT_DIR% 目录中
echo 请检查画质后，手动替换原文件
echo.
pause