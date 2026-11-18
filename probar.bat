@echo off
REM Prueba de comandos SIN correo (modo TEST). No requiere IP habilitada.
REM   probar.bat cliente.ana@example.com "LISPRO[\"*\"]"
REM   probar.bat -f doc\pruebas.txt
cd /d "%~dp0"
chcp 65001 > nul
java -Dstdout.encoding=UTF-8 -DMODE=TEST -jar target\FederCorreo-1.0.jar %*
