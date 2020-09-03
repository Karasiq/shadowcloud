@echo off
cd /d "%~dp0"
set FOUND=
for %%e in (%PATHEXT%) do (
  for %%X in (pip%%e) do (
    if not defined FOUND (
      set "FOUND=%%~$PATH:X"
    )
  )
  for %%X in (pip3%%e) do (
    if not defined FOUND (
      set "FOUND=%%~$PATH:X"
    )
  )
)

set "PIP=%FOUND%"
"%PIP%" install -r requirements.txt
py telegram_create_session.py
