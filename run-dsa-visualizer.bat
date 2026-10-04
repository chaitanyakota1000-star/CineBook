@echo off
title CineBook DSA Visualizer
echo ===================================================
echo     CineBook DSA Visualizer - Launching...
echo ===================================================
echo.
cd /d "%~dp0"

echo Compiling CineBookDsaVisualizer.java...
javac visualizer\CineBookDsaVisualizer.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed. Please ensure JDK 17+ is installed and on PATH.
    pause
    exit /b %errorlevel%
)

echo Starting CineBook DSA Visualizer (Swing GUI)...
start "" javaw visualizer.CineBookDsaVisualizer
echo [OK] Visualizer launched!
exit /b 0
