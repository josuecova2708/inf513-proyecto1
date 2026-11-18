#!/bin/bash
# Produccion en el servidor tecnoweb (usuario grupo28sa). Lo llama el cron cada 5 minutos
# como "vigilante": si FederCorreo ya esta corriendo no hace nada; si se cayo, lo levanta.
cd "$HOME" || exit 1
JAVA="$HOME/.local/jdk-21/bin/java"
[ -x "$JAVA" ] || JAVA="java"
JAR="$HOME/FederCorreo-1.0.jar"
LOG="$HOME/feder.log"

if pgrep -f "FederCorreo-1.0.jar" > /dev/null; then
    exit 0
fi
echo "=== $(date '+%Y-%m-%d %H:%M:%S') arrancando FederCorreo ===" >> "$LOG"
nohup "$JAVA" -DMODE=SERVER -jar "$JAR" >> "$LOG" 2>&1 &
