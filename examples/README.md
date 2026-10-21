# Listas de apps para desactivar

Cada carpeta tiene un `debloat.txt`: un nombre de paquete por línea, con comentarios después de `#`. `tools/disable.sh` los desactiva para el usuario 0 (no los desinstala) y `tools/restore.sh` los vuelve a activar.

| Carpeta | TV | Sistema |
| --- | --- | --- |
| [philips-65pud7906](philips-65pud7906/debloat.txt) | Philips 65PUD7906/77 (TP Vision, MediaTek) | Android TV 11 |

Antes de usar una lista, compará con tu propia TV:

```sh
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'pm list packages'
```

Para otras marcas y modelos, [cleanyourtv](https://github.com/Gathin23/cleanyourtv/tree/main/examples) tiene más de cien listas probadas, con notas sobre qué no conviene desactivar.

---

Lists of apps to disable, one package per line. For other TVs, see the lists in [cleanyourtv](https://github.com/Gathin23/cleanyourtv/tree/main/examples).
