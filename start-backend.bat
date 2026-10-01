@echo off
title CineBook — Java DSA Movie Booking System
echo ===================================================================
echo   CineBook - Smart Movie Ticket Booking System (Java DSA)
echo   Backend: Spring Boot 3.2  ^|  Port: 8080
echo ===================================================================
echo.

set MVN=C:\Users\chait\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd

IF NOT EXIST "%MVN%" (
    set MVN=mvn
)

echo Starting Spring Boot server...
echo.
echo Once you see "Started MainApplication", open your browser:
echo.
echo    >>> http://localhost:8080 <<<
echo.
echo Press Ctrl+C in this window when you want to stop the server.
echo ===================================================================
echo.

cd /d "%~dp0backend"
"%MVN%" spring-boot:run

pause
