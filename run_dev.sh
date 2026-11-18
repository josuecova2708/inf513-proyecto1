#!/bin/bash
# Desde una PC (modo DEV): lee la casilla por POP3 y responde por SMTP.
# OJO: para que salgan las respuestas, la IP publica de la PC debe estar habilitada por el docente.
cd "$(dirname "$0")"
java -DMODE=DEV -jar target/FederCorreo-1.0.jar
