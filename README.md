# Nexo

Buscador universal y panel lateral para Android, inspirado en la experiencia de
Sesame Shortcuts pero con implementación propia, Jetpack Compose y APIs modernas.

## Funciones

- **Búsqueda universal**: aplicaciones, ajustes del sistema, contactos (opcional),
  atajos personalizados y búsqueda web.
- **Coincidencias inteligentes**: prefijos, iniciales, subsecuencias y ranking por
  uso y recencia (aprendizaje local).
- **Panel lateral flotante, estrecho y translúcido**: ocupa aproximadamente el
  58 % del ancho (máximo 372 dp) y 72 % del alto de forma predeterminada, con
  bordes redondeados, y deja visible la app que hay detrás. Su altura se puede
  ajustar entre 55 % y 85 %.
- **Estilos del panel**: desde Ajustes se puede elegir Nexo, Menta, Océano, Ámbar
  o Monocromo. Cada estilo cambia fondo, superficies, texto y color de acento.
- **Tres páginas deslizables**: **Buscar** contiene búsqueda, recientes,
  frecuentes, contactos y atajos; **Ajustes** presenta accesos del sistema con
  iconos circulares mint; **Apps** muestra todas las aplicaciones en una
  cuadrícula vertical de tres columnas y orden alfabético. También se puede
  cambiar de página tocando sus pestañas superiores.
  La página Apps incorpora un índice alfabético lateral: se puede tocar una letra
  o deslizar el dedo continuamente por el índice para saltar entre aplicaciones.
  La cuadrícula aprovecha toda la altura disponible.
- **Favoritos**: mantén pulsada una app, atajo o contacto y elige **Anclar al
  panel**. Las ancladas aparecen en la sección **Ancladas**, en la misma
  cuadrícula, reciben prioridad en los resultados y se marcan con una estrella.
- **Menú de aplicaciones**: al mantener pulsada una app se puede abrir, intentar
  ventana flotante, consultar su información en Ajustes, solicitar su
  desinstalación y anclarla o desanclarla del panel.
- **Atajos del launcher**: al mantener pulsado el icono de Nexo aparecen
  **Abrir panel** y **Abrir Nexo**. También están disponibles para launchers y
  automatizadores que permiten seleccionar atajos publicados por aplicaciones.
- **Abrir en ventana flotante**: al mantener pulsado un elemento, el menú ofrece
  **Abrir** o **Abrir en ventana flotante**. Esta última usa el modo freeform de
  Android y depende del fabricante. Si el móvil no lo admite, el sistema abre la
  app con normalidad.
  También se solicita con doble toque sobre un resultado compatible; un toque
  abre normal y mantener pulsado conserva el menú de acciones. La burbuja que
  aparece al minimizar una ventana es del sistema Motorola, no de Nexo.
- **Teclado y búsqueda**: el panel no abre el teclado al aparecer; se muestra al
  tocar el campo de búsqueda o el botón de teclado. El panel se ajusta por encima
  del teclado. Enter o la
  tecla Buscar del teclado abre la primera coincidencia real del teléfono; si el
  texto no existe en el dispositivo, abre directamente Play Store con esa consulta
  (`market://search?...&c=apps`, con respaldo en la tienda web). En los resultados
  aparece la fila **Buscar "…" en Play Store** con icono de tienda, además de la
  búsqueda web. Con consulta vacía no abre nada.
  En el panel lateral, la búsqueda permanece arriba y los resultados crecen desde
  abajo: las primeras coincidencias quedan junto al teclado. La cuadrícula y sus
  títulos se alinean al lado del panel (derecha o izquierda). Debajo hay botones
  para volver a mostrar el teclado y abrir directamente los ajustes de Nexo.
- **Contraste del panel**: tema oscuro en el panel lateral, texto claro y fondo
  translúcido al 94 %, independientemente del tema de la pantalla principal.
- **Posición configurable**: borde izquierdo o derecho; la altura del tirador se
  recuerda entre sesiones. Un toque abre el panel con 200 ms de espera (para
  distinguir el doble toque) y el velo oscuro entra progresivamente con una
  animación de 130 ms; arriba abre brillo, abajo
  volumen y doble toque muestra Silencio, Normal y ChatGPT. Los arrastres
  horizontales y Atrás no abren el panel.
  En Ajustes → Panel lateral → Zona de activación se pueden ajustar el ancho
  (12–48 dp), alto (48–200 dp) y posición vertical, con vista previa. Los cambios
  se guardan al soltar el control y se aplican sin reiniciar el servicio.
  El área de acción es independiente del indicador visible. El área se puede
  separar del borde (0–64 dp; 24 dp por defecto) para dejar libre el gesto Atrás.
  El indicador tiene ancho (3–24 dp; 6 por defecto) y alto (16–120 dp; 64 por
  defecto) propios, limitados visualmente al área de acción. Una vista previa
  distingue ambos tamaños y "Mostrar área de acción" permite ver la zona táctil
  completa en el teléfono sin cambiar el indicador.
  Un deslizamiento desde el borde no abre Nexo, evitando activaciones al usar el
  gesto Atrás.
- **Primera página**: anclados, sugerencias por hora, recientes, frecuentes y
  ajustes rápidos, sin repetir elementos. Las etiquetas son configurables.
- **Atajos personalizados**: enlaces, `tel:`, `mailto:`, `sms:` y URLs.
- **Menú Añadir con iconos**: permite crear accesos de aplicaciones, páginas
  web, pares de aplicaciones, carpetas, contactos para llamar y WhatsApp.
  En el panel principal, mantener una zona vacía muestra espacios **+**. Tocar
  uno abre **Añadir al panel** y lo elegido se guarda en esa posición. Mantener
  otra vez una zona vacía oculta los signos **+**.
- **Carpetas**: se seleccionan varias aplicaciones y se guardan como un único
  elemento. Al abrirlo aparece una cuadrícula flotante con sus iconos.
  Desde el panel se expanden sin abrir otra actividad de Nexo; el icono muestra
  una miniatura de hasta cuatro aplicaciones de la carpeta.
- **Orden manual persistente**: mantén pulsado un elemento anclado y arrástralo
  sobre otro para reorganizarlo. El icono sigue el dedo, el destino se ilumina
  y el orden cambia al soltar. Mantener inmóvil abre el menú sin soltar.
  El orden se conserva al cerrar o reiniciar Nexo.
- **Doble toque**: abre aplicaciones en ventana flotante desde los anclados
  y la página Apps, si el dispositivo admite ese modo.
- **Gestos por icono**: el menú prolongado incluye **Gestos** para asociar un
  widget instalado al deslizamiento hacia arriba. Android autoriza el vínculo
  y, si corresponde, abre la configuración del proveedor. El widget se muestra
  en una ventana overlay real, con asa para mover, cierre y esquina inferior
  derecha para redimensionar arrastrando. El tamaño y la asociación se conservan por icono. Solo se muestran
  widgets que las aplicaciones instaladas publican; algunos tienen límites propios.
- **Selector visual de widgets**: tarjetas con icono de la aplicación, nombre y
  vista previa cuando el proveedor la incluye. Permite buscar por app o widget y
  ofrece el selector del sistema si el teléfono lo admite.
- **Panel Widgets**: nueva página para añadir varios widgets integrados en el
  panel, eliminarlos y cambiar su tamaño desde la esquina. Su posición entre los
  paneles se configura en Ajustes → Orden de los paneles.
- **Selectores de aplicaciones**: muestran iconos al añadir apps, seleccionar
  el contenido de una carpeta o crear una pareja.
- **Sugerencias aprendidas**: registra aperturas y hora local en Nexo. Con acceso
  al uso autorizado en Ajustes, importa los eventos disponibles de Android
  (hasta 14 días en la primera carga) y continúa desde el último evento importado.
  **Para esta hora** pondera la hora actual y las horas cercanas; **Frecuentes**
  usa el historial general y **Recientes** la última apertura. Todo es local.
- **Navegación compacta**: sin selector de pestañas; desliza horizontalmente
  para cambiar de panel con transición de desplazamiento, escala y opacidad.
  Ajustes permite ordenar los paneles y elegir cuál se abre primero.
- **Principal**: búsqueda minimalista de una línea con lupa y accesos rápidos
  del sistema integrados junto a los elementos del panel.
- **Cuadrícula personalizable**: desde Ajustes se puede cambiar la escala de
  iconos, usar entre 3 y 6 columnas, ajustar el espaciado y mostrar etiquetas.
  El tamaño del icono se adapta al ancho de la columna: menos columnas, iconos
  más grandes.
- **Vista previa animada**: los cambios de estilo, columnas, espaciado y
  etiquetas se muestran al instante en una miniatura del panel.
- **Contactos anclados**: un contacto para llamar o un atajo de WhatsApp se
  muestra como un único icono de persona; al tocarlo se llama o se abre el chat.
- **Página Contactos**: lista completa de la agenda con filtro. Al tocar un
  contacto se puede llamar, abrir WhatsApp, crear un atajo o anclarlo al panel.
  Incluye índice alfabético táctil/por arrastre y lupa inferior para el teclado.
  Cada fila tiene botones directos de chat y llamada y un menú de tres puntos.
- **Atajos de contacto en un paso**: elegir WhatsApp o teléfono abre la agenda;
  seleccionar una persona guarda nombre, número y foto sin volver a pedirlos.
  Un distintivo pequeño diferencia los accesos de llamada y WhatsApp.
- **Carga optimizada**: índice anterior en memoria mientras se actualiza en
  segundo plano y fotos de contactos cargadas solo al mostrarse, con caché.
- **Activador personalizable**: línea, píldora, círculo o icono; colores y
  opacidad ajustables, independientes de las dimensiones del área táctil.
- **Fondo claro**: opción en Apariencia para usar superficies claras y texto
  oscuro, conservando el color de acento del estilo elegido.
- **Controles del activador**: arriba muestra brillo global (requiere permiso
  Modificar ajustes del sistema y cambia a brillo manual); abajo muestra volumen
  de multimedia, tono y alarmas. Doble toque ofrece silencio de tono/notificaciones,
  sonido normal y ChatGPT Voz/Live. Silencio requiere acceso a No molestar.
  El toque simple espera a descartar el doble toque antes de abrir el panel.
  ChatGPT intenta el enlace de voz de la app y, si no se puede resolver, abre la
  aplicación. Nexo no inicia el micrófono ni garantiza Live en todas las versiones.
- **Jalar y sostener el activador**: tira unos 30 dp hacia el interior de la
  pantalla y mantén el dedo quieto 380 ms hasta la segunda vibración para intentar
  volver a la app anterior. Requiere acceso al uso y no garantiza recuperar la
  tarea o pantalla exacta de Android; no usa Accesibilidad.
- **Teclado de una mano**: depende del propio teclado, no de Nexo. Ajustes incluye
  un acceso a la configuración del teclado, pero la app no puede forzar ese modo.
- **Contactos para llamar**: se puede elegir cualquier número indexado y crear
  un acceso que utiliza el mismo flujo de permiso y llamada directa de Nexo.
- **WhatsApp a contacto**: el botón visible en cada contacto abre el chat
  directamente, sin formulario y sin mensaje preparado. Si el número local no
  trae prefijo, Nexo calcula E.164 usando el país de la SIM, red o región del
  teléfono. Los atajos guardados tampoco incluyen mensajes. WhatsApp valida al
  abrir si el número está registrado.
- **Contactos por número**: cada número distinto de una persona tiene su propia
  fila. Solo se eliminan repeticiones del mismo nombre y número normalizado.
- **Panel de tamaño fijo**: al aparecer el teclado el fondo no cambia de tamaño
  ni posición; los resultados se anclan al espacio visible sobre el teclado.
  La lista del panel es continua, sin títulos Recientes/Frecuentes/Atajos, y los
  elementos anclados aparecen primero.
- **Llamadas desde el panel**: se solicita CALL_PHONE desde el propio panel;
  si se deniega se abre el marcador, sin pasar por la pantalla principal de Nexo.
- **Llamada directa desde contactos**: cada resultado muestra avatar, nombre,
  número y dos botones visibles, **WhatsApp** y **llamar**, como en la referencia.
  El botón de llamada (o Enter sobre el contacto) solicita CALL_PHONE y realiza la
  llamada al autorizar. Si se deniega, abre el marcador con el número. Mantener
  pulsado muestra "Llamar al contacto", su número y la creación de un atajo de
  WhatsApp. Las llamadas no ofrecen apertura flotante.
- **Parejas de apps**: en Atajos → Pareja de apps, elegir dos apps distintas y
  guardar. Tanto la selección como la pareja muestran los iconos de las apps.
  Nexo conserva paquete y clase de ambas actividades. Un coordinador
  abre la primera y, tras 1000 ms, abre la segunda con
  `NEW_TASK | LAUNCH_ADJACENT | TASK_ON_HOME`. No requiere Accesibilidad ni lee
  la pantalla. Android y ambas apps deciden finalmente si admiten multiventana;
  si no, las actividades pueden abrirse normalmente a pantalla completa.
  Cada pareja utiliza un icono combinado con los iconos de sus dos aplicaciones.
- **Material 3** con color dinámico (Android 12+), modo claro/oscuro y respuesta
  háptica opcional mediante el vibrador del sistema.

## Requisitos

- Android 8.0 (API 26) o superior.
- Permiso **Mostrar sobre otras apps** para el panel lateral.
- Android 13+: permiso de notificaciones para el servicio en primer plano.
- Permiso de contactos solo si se activa la búsqueda de contactos.
- Permiso de llamadas solo al intentar llamar directamente; no es necesario
  para buscar contactos ni para los atajos de WhatsApp.

## Compilar

```powershell
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.2.0-bin\11i5gvueggl8a5cioxuftxrik\gradle-9.2.0\bin\gradle.bat"
& $gradle -p "C:\Users\stree\workspace\Nexo" :app:assembleDebug --no-daemon
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura

```
app/src/main/java/com/stree/nexo/
├── MainActivity.kt          # Pantalla principal, permisos y preferencias
├── SearchActivity.kt        # Panel translúcido sobre otras apps
├── panel/                   # Servicio en primer plano y tirador del borde
├── data/                    # Índice de apps, contactos, ajustes y atajos
├── search/                  # Motor de búsqueda, ranking y lanzador
└── ui/                      # Compose: buscador, panel, atajos y ajustes
```

## Privacidad

Todo el índice y el aprendizaje de uso se procesan en el dispositivo. Nexo no
envía aplicaciones, contactos ni búsquedas a ningún servidor.
Al abrir un atajo web/WhatsApp se entrega el enlace seleccionado a la app externa
o navegador; el enlace wa.me contiene únicamente el número del contacto.

## Limitaciones conocidas

- Las parejas usan únicamente las banderas públicas de lanzamiento. Estas no
  fuerzan el modo multiventana: en Android 16 o en launchers que las ignoren, la
  segunda app puede quedar a pantalla completa. Pendiente de validar el resultado
  en el Motorola Edge 50 Pro.

- Android solo permite leer los atajos internos de otras apps si la app es el
  lanzador predeterminado. Nexo lo detecta y ofrece atajos personalizados.
- El panel usa una ventana superpuesta y un servicio en primer plano; algunos
  fabricantes pueden requerir desactivar la optimización de batería.
- La ventana flotante depende del fabricante: usa el modo freeform de Android.
  Motorola Edge/Hello UI incluye ventanas freeform en su panel nativo, pero eso
  no garantiza que una app externa pueda solicitarlas. Si el sistema lo ignora,
  la app se abre a pantalla completa. Se corrigió la clave del bundle a
  `android.activity.windowingMode`; pendiente de validar en Motorola Edge 50 Pro.
- En Android 10+ el gesto de "atrás" del sistema compite con el borde. Nexo
  excluye su zona de toque (limitada por Android a ~200 dp de alto). La protección
  requiere mantener 350 ms antes de abrir; para usar Atrás se recomienda iniciar
  el gesto fuera del área de acción.
