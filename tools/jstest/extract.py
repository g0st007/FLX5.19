"""Extrae los scripts JS (SNIFF_JS, SHIELD_JS) de BrowserController.kt a archivos .js para probarlos con node."""
import os, re
here = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(here, '../../app/src/main/java/app/flux/downloader/browser/BrowserController.kt'), encoding='utf-8').read()
for name in ['SHIELD_JS', 'SNIFF_JS']:
    m = re.search(r'private val ' + name + r' = """(.*?)"""', src, re.S)
    js = m.group(1).replace("${'$'}", "$")
    open(os.path.join(here, name + '.js'), 'w', encoding='utf-8').write(js)
    print(name, len(js))
