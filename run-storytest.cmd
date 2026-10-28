@echo off
rem Double-click: the game with the story-test key (N: the story moves on to its next moment). Otherwise the same as run-game.cmd.
set AICOMPANION_STORYTEST=1
call "%~dp0dev.cmd" runClient
if errorlevel 1 pause
