#!/bin/bash
# Instala el cron que vigila FederCorreo cada 5 minutos. Ejecutar UNA vez en el servidor.
chmod +x "$HOME/run.sh"
CRON_LINE="*/5 * * * * $HOME/run.sh"
if crontab -l 2>/dev/null | grep -qF "run.sh"; then
    echo "El cron ya estaba instalado:"
else
    (crontab -l 2>/dev/null; echo "$CRON_LINE") | crontab -
    echo "Cron instalado:"
fi
crontab -l
"$HOME/run.sh"
