@echo off
setlocal
cd /d "%~dp0"

echo Installing build dependencies...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller

echo Building DragonPal.exe...
python -m PyInstaller --noconfirm --clean --onefile --windowed --name DragonPal ^
  --collect-all pyttsx3 --collect-all comtypes --collect-all pillow run.py

echo.
echo Done. Your exe is at: %~dp0dist\DragonPal.exe
pause
