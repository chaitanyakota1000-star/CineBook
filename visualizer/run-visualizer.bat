@echo off
title CineBook DSA Visualizer
cd /d "%~dp0\.."
javac visualizer\CineBookDsaVisualizer.java
start "" javaw visualizer.CineBookDsaVisualizer
exit /b 0
