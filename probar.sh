#!/bin/bash
# Prueba de comandos SIN correo (modo TEST). No requiere IP habilitada.
#   ./probar.sh cliente.ana@example.com 'LISPRO["*"]'
#   ./probar.sh -f doc/pruebas.txt
cd "$(dirname "$0")"
java -Dstdout.encoding=UTF-8 -DMODE=TEST -jar target/FederCorreo-1.0.jar "$@"
