# Nexo — Protocolo de Orquestación Multi-Agente y Guía del Proyecto

Este archivo aplica automáticamente a todas las conversaciones en este repositorio (`deadlylord/Nexo`). Su objetivo es optimizar el consumo de tokens, mantener limpio el contexto del hilo principal y estandarizar la verificación y entrega.

## 1. Protocolo de Orquestación Multi-Agente (Antigravity)

El agente principal actúa como **Arquitecto / Orquestador**. Usa umbrales claros para evitar desperdiciar tokens creando subagentes en cambios triviales y, a la vez, aislar exploraciones grandes o implementaciones multi-archivo:

1. **Regla de umbral (Directo vs. Subagente):**
   - **Cambios pequeños o puntuales (1–3 archivos ya conocidos, bugfixes rápidos):** leer y editar directamente en el hilo principal. Es más rápido y consume menos tokens que arrancar un subagente.
   - **Exploración amplia (4+ archivos, rastreo entre módulos o dependencias):** delegar al subagente `research` siguiendo las reglas de `.agents/skills/explore-codebase/SKILL.md` (solo lectura, resumen técnico de máximo 10–15 líneas con rutas exactas, símbolos y rangos de líneas).
   - **Features nuevas, refactors grandes o cambios paralelos (3+ archivos grandes):**
     1. Explorar con `research` (si falta contexto).
     2. **Planificación:** presentar arquitectura, archivos a tocar y casos borde; **esperar aprobación del usuario** antes de modificar código. (Para bugfixes pequeños y evidentes, se puede corregir directamente sin bloquear).
     3. **Ejecución:** delegar a un subagente `implementer` (`define_subagent` con `enable_write_tools: true`, siguiendo `.agents/skills/code-implementer/SKILL.md`) para no ensuciar el hilo principal con diffs extensos.
2. **Verificación obligatoria antes de terminar:**
   - Todo cambio en código Android/Kotlin debe compilar localmente antes de hacer push.
   - Si una ejecución de verificación o análisis de logs es pesada, puede delegarse a un subagente ligero para no llenar el contexto principal.

## 2. Modelos Autorizados por Rol (Autorización Explícita del Usuario)

El usuario autoriza explícitamente seleccionar estos niveles de modelo al invocar subagentes con `invoke_subagent`:

| Rol | Subagente / Skill | Modelo autorizado | Uso |
|-----|-------------------|-------------------|-----|
| **Orquestador / Arquitecto** | Hilo principal | `pro` (modelo principal) | Decisiones de diseño, planificación, revisión final y fixes pequeños. |
| **Explorador** | `research` (`explore-codebase`) | `flash` (o `flash_lite` en búsquedas simples) | Lectura de múltiples archivos, búsqueda de símbolos y mapeo de dependencias. |
| **Implementador (UI / mecánico)** | `implementer` (`code-implementer`) | `flash` | Implementación de planes bien detallados en UI Compose, prefs, wiring y refactors directos. |
| **Implementador (sistema Android)** | `implementer` (`code-implementer`) | `pro` (`inherit`) | Lógica delicada: `WindowManager`, `AccessibilityService`, Canvas, gestos táctiles, concurrencia o ciclo de vida. |
| **Verificador (opcional)** | subagente con comandos | `flash_lite` o `flash` | Ejecutar compilación Gradle y revisar logs de CI si el output es extenso. |

## 3. Mapa Compacto de Arquitectura de Nexo

App Android en Kotlin + Jetpack Compose (`com.stree.nexo`, base en `app/src/main/java/com/stree/nexo/`):

- **`panel/EdgePanelService.kt`**: Servicio foreground central — maneja el tirador lateral, gestos, Isla Dinámica, burbujas/widgets flotantes y observadores del sistema (sonido, batería, desbloqueo, portapapeles, capturas).
- **`panel/DynamicIslandView.kt`**: Vista Canvas OLED de la Isla Dinámica (reposo/oculta tras cámara, compacta, reproductor expandido con seekbar y paleta del álbum, notificaciones, hub rápido y guía de calibración).
- **`panel/SplitScreenService.kt`**: `AccessibilityService` opcional (`Nexo · Parejas de apps`) — pantalla dividida, acciones globales (captura, notificaciones, navegación), host `TYPE_ACCESSIBILITY_OVERLAY` para la Isla Dinámica y seguimiento de ventanas flotantes.
- **`media/MediaControlService.kt`**: `NotificationListenerService` — sesiones activas de `MediaController`, detección/filtrado de llamadas, capturas y pulsos de notificación.
- **`floating/FloatingAppBubbleController.kt` y `floating/FloatingWindowManager.kt`**: Burbuja lateral de apps minimizadas (1 toque reabre flotante, doble toque abre menú, detección de minimizado nativo) y motor de geometría multi-ventana.
- **`ui/SpotlightSearch.kt`, `ui/SpotlightAutoOpen.kt`, `ui/SpotlightWidgetTabs.kt`, `ui/VoiceDictation.kt`**: Spotlight (app principal y `SpotlightSearchActivity`), pestañas de widgets, dictado por voz, auto-apertura por coincidencia y ficha rápida de contacto.
- **`ui/SearchScreen.kt` y `ui/ControlPanels.kt`**: Panel lateral (`SearchActivity`), cuadrícula fijada, gesto deslizar abajo para notificaciones, `QuickSettingsPage` y controles rápidos (`ActivatorControlsActivity`).
- **`ui/SettingsScreen.kt`**: Pantalla de ajustes (calibración en vivo de Isla Dinámica, gestos, Spotlight, burbujas flotantes, apertura automática).
- **`data/Prefs.kt`, `data/Models.kt`, `data/SearchRepository.kt`**: Preferencias persistentes, enums (`EdgeGesture`, `EdgeGestureAction`, `SearchItem`) e indexación de apps/contactos/atajos.
- **`search/ItemLauncher.kt`, `search/SearchEngine.kt`, `search/SearchViewModel.kt`**: Búsqueda y lanzamiento de apps en pantalla completa, ventana flotante o desde notificaciones.

## 4. Rutina Estándar de Verificación y Entrega

Para cualquier cambio de código en la app:
1. **Compilar localmente:** `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug`
2. **Commit y push:** subir a `origin/main` con mensaje claro y atómico.
3. **CI / APK:** vigilar el workflow `APK` en GitHub Actions con `gh run watch` hasta confirmar `success`.
4. **Entrega al usuario:** responder de forma concisa en **español**, incluir enlaces clicables `file://` a los archivos modificados y entregar el enlace directo del APK cuando haya compilación nueva:
   `https://github.com/deadlylord/Nexo/releases/download/dev-latest/Nexo-debug.apk`

## 5. Política de Skills Instalados (Ahorro de Tokens)

No cargues skills con `lookup_skill` salvo que apliquen directamente a la tarea actual:

- **Activos en el flujo de Nexo:** `explore-codebase`, `code-implementer`, `github`.
- **Útiles puntualmente (bajo costo):**
  - `work-unit-commits`: cuando una feature toque muchos archivos y convenga dividirla en commits atómicos/reversibles.
  - `cognitive-doc-design`: al escribir o resumir docs, README o planes largos para mantenerlos cortos y escaneables.
- **Solo bajo petición explícita del usuario (consumen muchos tokens):**
  - `judgment-day`: revisión dual con 2 jueces únicamente si el usuario pide *"juzgar"* o *"dual review"* en cambios críticos de sistema Android.
  - `unslop`: únicamente sus principios visuales adaptados a Compose si el usuario pide pulir/rediseñar estética de una pantalla.
- **Ignorar siempre en Nexo (no cargar):** skills de Web/Vercel/React/Go o herramientas externas (`vercel-*`, `deploy-to-vercel`, `web-app-development`, `go-testing`, `chained-pr`, `skill-registry`).
