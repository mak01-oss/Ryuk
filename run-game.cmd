@echo off
rem Double-click: Minecraft 1.20.1 + Forge with the mod. First start takes about a minute.
call "%~dp0dev.cmd" runClient
if errorlevel 1 pause
