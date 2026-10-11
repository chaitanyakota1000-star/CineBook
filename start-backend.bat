@echo off
title CineBook — Java DSA Movie Booking System
echo ===================================================================
echo   CineBook - Smart Movie Ticket Booking System (Java DSA)
echo   Backend: Spring Boot 3.2  ^|  Port: 8080
echo ===================================================================
echo.

cd /d "%~dp0backend"

:: Verify Java is available
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not found on PATH.
    echo Please install Java 17 or higher from https://adoptium.net
    echo.
    pause
    exit /b 1
)

echo Starting Spring Boot server on port 8080...
echo.
echo Once started, you can access the website at:
echo.
echo    http://localhost:8080
echo.
echo Press Ctrl+C in this window to stop the server.
echo ===================================================================
echo.

:: 1. If compiled JAR exists, launch it directly for instant startup
if exist "target\cinebook-backend-1.0.0.jar" (
    echo Launching pre-built JAR: target\cinebook-backend-1.0.0.jar
    java -jar target\cinebook-backend-1.0.0.jar
    goto :end
)

:: 2. Otherwise use Maven Wrapper (mvnw.cmd)
if exist "mvnw.cmd" (
    echo Launching via Maven Wrapper (mvnw.cmd)...
    call mvnw.cmd spring-boot:run
    goto :end
)

:: 3. Fallback to system Maven
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    echo Launching via system Maven...
    mvn spring-boot:run
    goto :end
)

echo [ERROR] Neither target\cinebook-backend-1.0.0.jar nor mvnw.cmd found.
echo.

:end
if %errorlevel% neq 0 (
    echo.
    echo Server process exited with code %errorlevel%.
    pause
)
