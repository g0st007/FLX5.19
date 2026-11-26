# Pruebas del JS de detección (sin compilar la app)

El JS de `BrowserController.kt` (SNIFF_JS y SHIELD_JS) se puede comprobar con node, sin compilar Android:

```
cd tools/jstest
python3 extract.py          # saca los .js del Kotlin
node --check SNIFF_JS.js    # sintaxis
node --check SHIELD_JS.js
node t_load.js              # carga en PH (blob), HLS y mp4 directo: no debe tocar el reproductor
node t_kvs.js               # decodificación KVS (function/0/) verificada con la URL real
```

`harness.js` crea un entorno falso (window, document, reproductor, puente `B`) y registra las llamadas al puente.
Esto NO sustituye a compilar y probar en el teléfono: no comprueba el Kotlin ni los sitios reales.
