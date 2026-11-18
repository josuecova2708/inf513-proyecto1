@echo off
REM Utilidades: diag | sql db\schema.sql | sql db\datos_prueba.sql | query "SELECT ..." | manual | ciclo
cd /d "%~dp0"
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas %*
