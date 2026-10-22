# Paquete del Canal Mii

Sin paquete, el Canal Mii se dibuja por código. Con el paquete usa las caras, los pelos, las narices y las barbas en 3D, los íconos del editor, la interfaz de la plaza, el ícono del menú y los sonidos del canal original.

Este repositorio no trae ningún archivo de Nintendo. Estas herramientas toman los archivos de sus páginas públicas en The Spriters Resource y arman `miipack.zip` en tu computadora. Cada archivo se comprueba con su SHA-256, así que el paquete sale idéntico al que se probó en la TV.

## Requisitos

Python 3 con numpy, pillow y scipy:

```sh
pip3 install -r tools/miipack/requirements.txt
```

## 1. Bajar los archivos

Los sitios no dejan descargar por script, así que bajá cada uno con el navegador, con el botón de descarga de su página, a una misma carpeta (por ejemplo `~/Downloads`):

| Archivo | Página |
| --- | --- |
| Cuerpos | https://models.spriters-resource.com/wii/miichannel/asset/348210/ |
| Pelos | https://models.spriters-resource.com/wii/miichannel/asset/354343/ |
| Cabezas | https://models.spriters-resource.com/wii/miichannel/asset/354342/ |
| Barbas | https://models.spriters-resource.com/wii/miichannel/asset/354344/ |
| Narices | https://models.spriters-resource.com/wii/miichannel/asset/354345/ |
| Íconos de partes | https://www.spriters-resource.com/wii/miichannel/asset/64626/ |
| Ícono del canal | https://www.spriters-resource.com/wii/miichannel/asset/607367/ |
| Letras | https://www.spriters-resource.com/wii/miichannel/asset/64627/ |
| Plaza Mii | https://www.spriters-resource.com/wii/miichannel/asset/65079/ |
| Banner del menú | https://www.spriters-resource.com/wii/miichannel/asset/64625/ |
| Piso | https://textures.spriters-resource.com/wii/miichannel/asset/378756/ |
| Sonidos | https://sounds.spriters-resource.com/wii/miichannel/asset/423107/ |

Después:

```sh
python3 tools/miipack/fetch.py --from ~/Downloads
```

Reconoce cada archivo por su contenido, no por su nombre. Si falta alguno o cambió en el sitio, te dice cuál.

## 2. Armar el paquete

```sh
tools/miipack/build.sh
```

Tarda unos 3 minutos y deja el paquete en `tools/miipack/build/out/miipack.zip`. Para trabajar en otra carpeta, definí `MIIPACK_DIR` antes de los dos pasos.

## 3. Cargarlo en la TV

Con la depuración por red activada, como en el README principal (cambiá la IP por la de tu TV):

```sh
TV_HOST=192.168.1.50 tools/venv/bin/python tools/b64push.py tools/miipack/build/out/miipack.zip /data/local/tmp/miipack.zip
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'content write --uri content://com.gathin.tvhome.theme/miipack.zip < /data/local/tmp/miipack.zip; rm /data/local/tmp/miipack.zip; am force-stop com.gathin.tvhome'
```

Apretá Home y el canal ya usa el paquete. Para sacarlo:

```sh
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'content delete --uri content://com.gathin.tvhome.theme/miipack.zip; am force-stop com.gathin.tvhome'
```
