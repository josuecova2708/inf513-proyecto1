@echo off
REM Modo DEV desde Windows: lee la casilla y responde (requiere IP habilitada por el docente).
cd /d "%~dp0"
java -DMODE=DEV -jar target\FederCorreo-1.0.jar
