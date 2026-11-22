# Cel-claude

Repositorio pensado para hacer cosas en el cel con Claude.

## Optimizador (APK para Android / Samsung)

App sin dependencias externas que muestra:

- **Batería, RAM y almacenamiento** en tiempo real.
- **Liberar RAM**: cierra procesos en segundo plano de apps de usuario.
- **Archivos grandes, duplicados y descargas viejas**, con borrado solo tras confirmar.
- **Apps**: más usadas (7 días), sin usar (30+ días) y más pesadas, con acceso directo a Ajustes/Desinstalar.

### Cómo obtener la APK

1. Cada push compila la APK con GitHub Actions (`.github/workflows/build-apk.yml`).
2. Abre la pestaña **Actions** del repositorio, entra a la última ejecución y descarga el artefacto `optimizador-apk`.
3. Descomprime el .zip e instala `app-debug.apk` (permite "instalar apps desconocidas" para el navegador o el gestor de archivos).

### Permisos

Se piden solo cuando hacen falta: acceso a todos los archivos (buscador de archivos) y acceso a datos de uso (pantalla de Apps). Nada sale del teléfono.
