# MediaScroll App

Visor de fotos y vídeos a pantalla completa con scroll vertical (estilo TikTok), completamente offline.

---

## 📱 Cómo obtener la APK (sin ordenador)

### Paso 1 — Subir el proyecto a GitHub

1. Ve a [github.com](https://github.com) e inicia sesión (o crea una cuenta gratuita).
2. Haz clic en **"New repository"** (botón verde).
3. Ponle nombre: `MediaScrollApp`, déjalo en **Public** o **Private** (da igual), y haz clic en **"Create repository"**.
4. En la página del repositorio vacío, busca el enlace **"uploading an existing file"** y haz clic.
5. Arrastra **todos los archivos de esta carpeta** a la página web (incluidas las carpetas `.github/`, `app/`, `gradle/`).
6. Haz clic en **"Commit changes"**.

> ⚠️ Asegúrate de incluir la carpeta oculta `.github/` — contiene el workflow que compila la APK.

---

### Paso 2 — Esperar a que GitHub compile la APK

1. Ve a tu repositorio en GitHub.
2. Haz clic en la pestaña **"Actions"** (menú superior).
3. Verás un workflow llamado **"Build APK"** ejecutándose (círculo amarillo = en progreso, verde = terminado).
4. Espera unos **3-5 minutos** hasta que el círculo se ponga verde ✅.

Si algo falla (círculo rojo ❌), abre el workflow y mira los logs — lo más habitual es un error de permisos de Actions (ver abajo).

---

### Paso 3 — Descargar la APK

1. Haz clic en el workflow completado (**"Build APK"**).
2. Baja hasta la sección **"Artifacts"**.
3. Descarga **`MediaScroll-debug-apk`** — este es el fichero `.apk` listo para instalar.

---

### Paso 4 — Instalar en el móvil

1. Transfiere el `.apk` a tu móvil (por email, Google Drive, cable USB…).
2. En el móvil, ve a **Ajustes → Seguridad → Instalar apps desconocidas** y actívalo para tu navegador/gestor de archivos.
3. Abre el `.apk` y toca **Instalar**.

---

## ⚙️ Si el workflow no arranca automáticamente

Ve a **Actions → "Build APK"** y haz clic en **"Run workflow"** (botón azul a la derecha) para lanzarlo manualmente.

Si ves el error `"Workflow must have at least one job"` o similar, comprueba que la carpeta `.github/workflows/build-apk.yml` se subió correctamente.

---

## 📋 Permisos que necesita la app

- `READ_MEDIA_IMAGES` — leer fotos del almacenamiento
- `READ_MEDIA_VIDEO` — leer vídeos del almacenamiento
- `WAKE_LOCK` — mantener la pantalla encendida durante la reproducción

---

## 🔄 Actualizar la app

Para subir cambios:
1. Edita los ficheros directamente en GitHub (icono del lápiz).
2. Al hacer commit, el workflow se vuelve a ejecutar y genera una nueva APK.
