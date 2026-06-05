# PadelAdmin — Contexto de sesión 3

**Fecha:** 2026-06-05
**Proyecto:** Gestión de torneos de pádel (Spring Boot 4 + React/Vite + PostgreSQL)
**Sesiones previas:** `SESSION_CONTEXT.md`, `SESSION_CONTEXT_2.md`
**Repo:** https://github.com/claudiocarmusciano/PadelAdmin (rama `main`)

> Constraint del usuario: evaluación honesta y crítica. Diferenciar hechos, inferencias y opiniones. Sin halagos ni complacencia.

---

## 🚀 HITO PRINCIPAL DE LA SESIÓN: la app está DEPLOYADA y EN USO

- **URL producción:** https://padeladmin-production.up.railway.app
- **Hosting:** Railway (proyecto "distinguished-enjoyment", entorno "production")
- **Arquitectura de deploy:** **una sola imagen Docker** — el frontend (Vite) se buildea y queda **servido por el backend** (Spring Boot). Postgres es un servicio gestionado de Railway.
- **Admin:** `admin@padel.com` (la contraseña la puso el usuario en las variables de Railway; **el asistente no la conoce** — se ve/edita en Railway → servicio PadelAdmin → Variables → `ADMIN_PASSWORD`).
- **Deploy automático:** cada `git push` a `main` dispara build + deploy en Railway.
- Guía completa en `DEPLOY.md`.

### Datos en producción
- **~414 jugadores, 10 categorías, complejos/canchas/horarios, point_configs, settings** → migrados de la base local a Railway (vía Docker `postgres:18`, porque **la base local es PG 18.3**).
- **Los torneos NO se migraron** (la base de Railway arrancó con torneos vacíos, a propósito).
- **Local y Railway están sincronizadas** en datos maestros.

---

## 🛠️ Stack y entornos

- **Backend:** Spring Boot **4.0.6**, Java **21**, Spring Security 6, JWT.
- **Frontend:** React + TypeScript + Vite + Tailwind + shadcn/ui (Radix) + recharts.
- **DB:** PostgreSQL. Local: `postgresql://postgres:postgres@localhost:5432/padeladmin` (PG 18.3). Prod: Railway.
- **Config externalizada por env var** (`application.properties` con defaults locales): DB url/user/pass, `PORT`, logging, `JPA_DDL_AUTO`, `JWT_SECRET`, `ADMIN_EMAIL`/`ADMIN_PASSWORD`, `APP_CORS_EXTRA_ORIGINS`.
- **Correr local:** backend `./mvnw spring-boot:run` (8080); frontend `npm run dev` (5173, con `.env.development` apuntando a `localhost:8080/api`).
- **Importante:** el backend NO recarga cambios `.java` solo — hay que Stop+Run en IntelliJ.
- **El usuario NO tiene `psql` instalado** ni Postgres local accesible por CLI: para tocar la DB se usó **Docker `postgres:18`** (`docker run --rm postgres:18 psql '<conn>'`). Docker a veces estaba cerrado.

---

## 📋 TODO lo que se hizo en esta sesión (por tema)

### Commits/limpieza inicial
- Se commitearon y pushearon todos los cambios pendientes de la sesión 2 (auth JWT, stats de jugador, calendario, court availability, fixes de fixture).
- `.gitignore`: ignora `.claude/`, `SESSION_CONTEXT*.md`, `PROGRESS.md`, `INFORME_AVANCES.md`, imágenes/docs locales.

### Deploy a Railway (grande)
- **Dockerfile** multi-stage (build front → embeber en `resources/static` → package Spring Boot → runtime JRE). `.dockerignore`.
- **SecurityConfig:** `.anyRequest().permitAll()` para servir estáticos/SPA (los datos siguen detrás de `/api` con auth). **`SpaForwardController`** reenvía deep links (BrowserRouter) a `index.html`.
- **axios** baseURL relativa `/api` en prod (env `VITE_API_BASE_URL`).
- **Bug destapado:** el build de producción (`tsc -b`) **nunca había corrido** y tenía 7 errores (imports type-only, dead code). Arreglados (sino fallaba el deploy).
- Verificado con build+run de Docker local antes de pushear.

### Bugs de login en producción (resueltos)
- **403 en login → era CORS:** el navegador manda `Origin` con el dominio Railway; `CorsConfig` solo permitía `localhost`. Fix: patrón `https://*.up.railway.app` + env `APP_CORS_EXTRA_ORIGINS`. (Mi curl daba 422 y el navegador 403 → esa fue la pista.)

### Otorgamiento de puntos al finalizar torneo (feature nueva importante)
- **Descubrimiento:** los puntos por etapa de Configuración (`PointConfig`: PARTICIPANT, ZONE_PASS, ROUND_32…CHAMPION) **estaban configurados pero NO se otorgaban nunca** (config muerta).
- Ahora: al pasar torneo a **COMPLETED** se otorgan a cada jugador según la **MEJOR instancia** alcanzada por su pareja (decidido con el usuario: **solo mejor instancia, NO acumulativo**; **acumulativo sobre el puntaje vigente** del jugador en esa categoría).
- **`TournamentPointAward`** (entidad nueva): historial "plano" con snapshots (sin FK dura) que **sobrevive al borrado del torneo y a la limpieza de temporada**.
- **`reorganize`/reset:** `POST /api/settings/points/reset` (botón "Nueva temporada" en Settings con confirmación tipeada) pone todos los puntos en 0 conservando el historial.
- **Caveat:** bajo "solo mejor instancia", ZONE_PASS casi nunca aplica (quien clasifica juega ≥1 ronda eliminatoria que rankea más alto).

### Fix del 500 al finalizar torneo
- El endpoint `PATCH /tournaments/{id}/status` esperaba `@RequestParam` pero el frontend mandaba `{status}` en el **body** → 500. Fix: `@RequestBody StatusUpdateRequest`. (Verificado levantando un backend de prueba en :8081.)

### Datos: reconciliación 6ta. Damas (CSV del usuario vs DB)
- El usuario pasó un CSV con puntajes válidos. Se compararon (normalizando nombres) y se aplicaron en **Railway y local**:
  - **31 puntajes actualizados** (la DB tenía valores redondeados/viejos; el CSV es la fuente de verdad).
  - **Cangran Carolina y Gainza Elisa REMOVIDAS** de 6ta. Damas (ya no juegan ahí; siguen como jugadoras).
  - **3 nombres corregidos:** Melo Fernandez→Fernandez Melo, "Lara, M."→Lara Melisa, "Rodriguez, L."→Rodriguez Lucrecia. (Miccio/Florencia y Sbardolini/Rocio se dejaron con esa grafía a pedido del usuario.)

### Auth / sesiones
- **"Ingresar como invitado"** (modo solo lectura, rol VIEWER): `GuestSeeder` crea `invitado@padel.com`, `POST /api/auth/guest` emite token VIEWER sin password, botón en LoginPage. El VIEWER ve todo pero el backend bloquea escrituras (403) y los botones CUD están ocultos.
- **Auto-logout al vencer el JWT** (TTL 8h): antes la sesión quedaba "logueada pero sin datos". Fix: chequeo de expiración del token del lado del cliente (decodifica `exp`) al abrir la app y antes de cada request + backend devuelve **401** (no 403) para no-autenticado vía `AuthenticationEntryPoint` (el 403 se mantiene para VIEWER sin permiso).
- **Multi-sesión:** sí se pueden tener varias sesiones admin a la vez (JWT stateless). **No hay optimistic locking → "el último que guarda gana"** (lost update silencioso). Hueco menor: el nombre único de torneo es solo a nivel servicio (sin unique en DB) → TOCTOU. El usuario decidió **dejarlo así por ahora**.

### Zonas y fixture (varios)
- **Mínimo de parejas: 9 → 6** (`ZoneService.MIN_PAIRS`). El pipeline ya soporta 6-8 inscriptos (bracket FAP de 4/8 existente).
- **Snake corregido para zonas de 4:** la 3ª posición en adelante se llena **fila por fila en orden de zona (A→B→C)**, no zona por zona. Verificado: N=10 → A[1,6,7,10] B[2,5,8] C[3,4,9]; N=11 → A[1,6,7,10] B[2,5,8,11] C[3,4,9].
- **Cambiar parejas de zona (mover/intercambiar) borra el fixture** → obliga a regenerarlo (solo si no hay resultados).
- **Borrar una pareja** (antes de resultados) **limpia zonas + fixture** para regenerar. Bloqueado si hay resultados.
- **Bug R2 antes que R1 (zona de 4):** la Ronda 2 se programaba ANTES que la Ronda 1 (el check de intervalo miraba distancia, no dirección, y solo mismo día). Fix: `violatesZoneRoundOrder` — R2 debe empezar después del fin de la R1 de sus parejas + intervalo mínimo (fecha+hora completa, vale entre días).
- **"Rehacer fixture":** rehacer ya funcionaba sin resultados (borra sin-jugar + regenera). Ahora el botón dice "Rehacer fixture" cuando ya existe + pide confirmación; mensaje de bloqueo más claro si hay resultados.

### ⭐ Feature grande NUEVA (NECESITA PRUEBA DEL USUARIO): "Reordenar zonas para programar todo"
Cuando quedan partidos sin programar (restricciones que se pisan, o faltan canchas), un botón en Fixture (aparece **solo si hay pendientes y sin resultados**) intenta reacomodar parejas entre zonas. **Algoritmo acordado con el usuario:**
- **Cabezas de zona (posición 1) FIJAS**, nunca se mueven.
- **Intercambios DE A PARES** entre zonas, **independientes** desde la config base (no acumulativo).
- **Escalado desde el fondo:** primero las últimas de cada zona, luego anteúltimas, etc.
- Evaluación **in-memory** (genera matches + scheduler sin persistir) por cada swap.
- Si un swap llega a **0 sin programar** → se aplica. Si no, se aplica el **MEJOR** (menos pendientes = "opción A").
- Si aún quedan pendientes → mensaje "conviene sumar otra cancha/complejo" (la integración real multi-complejo NO se hizo; solo se avisa).
- Persiste el mejor arreglo en `zone_pairs` y regenera el fixture (reutiliza `generateFixture`).
- Backend: `reorganizeZonesToSchedule`, `ReorganizeResultDto`, `POST /tournaments/{id}/fixture/reorganize`.
- **PENDIENTE DE VALIDAR:** el asistente NO pudo testearlo end-to-end (necesita un torneo real con restricciones que se pisen). Revisado por código (determinismo evaluación↔regeneración, terminación, ~C(Z,2) por nivel). **El usuario debe probarlo.**

### UX / varios
- **PlayersPage:** editar puntos por categoría inline (lápiz); botones editar/borrar más grandes (28px); chips con `#ranking`.
- **PairsTab:** filtro de categoría persiste entre altas; diálogo "Nueva pareja" queda abierto tras crear (carga en serie); scroll con rueda del mouse en el combobox de jugadores (fix del scroll-lock del Dialog); **lista de parejas arranca DESPLEGADA** mostrando restricciones/preferencias; horarios de restricciones/preferencias **cada 30 min** (antes cada 1h).
- **Calendario:** los partidos jugados sin horario no aparecen como "pendientes" (sección aparte "Jugados sin horario"); cada bloque muestra **punto de color + etiqueta de estado** (Programado/Jugado/Pendiente) además del color de fondo.
- **Court availability:** botón **"Guardar y copiar a las demás canchas del complejo"** (`POST /api/courts/{id}/availability/copy-to-complex`) — carga una cancha y copia a todas las del complejo.
- **Fix de ancho de diálogos:** el `DialogContent` base de shadcn trae `sm:max-w-sm` (384px) que `tailwind-merge` no fusiona → apretaba diálogos anchos. Arreglado en el de horarios (`sm:max-w-xl`) y el de stats (`sm:max-w-3xl`).
- **Pantalla negra al "Asignar cancha":** Radix prohíbe `<SelectItem value="">` → crasheaba toda la app. Fix: valor centinela `__none__`. Además se agregó un **ErrorBoundary global** (muestra "Algo salió mal / Recargar" en vez de pantalla negra).
- **Favicon:** se reemplazó el ícono violeta por el logo naranja de padel.
- **Contador pendientes/programados** del fixture corregido (PLAYED/CONFIRMED cuentan como programados, no pendientes).

---

## 💰 Discusión de monetización (estrategia, no código)
- Plan del usuario: 3 tiers freemium (gratis/medio/premium por cantidad de torneos/participantes).
- **Evaluación dada:** viable pero el eje "por volumen" es flojo para un mercado **estacional y por evento**. Mejor: **pay-per-tournament** + (a futuro) **comisión por inscripción vía Mercado Pago marketplace/split** (cada club conecta su cuenta MP; la app cobra un fee). Modelo a evitar: que la plata entre a la cuenta de la app (carga regulatoria).
- **Secuencia recomendada:** 1) MVP gestión (ahora), 2) inscripción online de parejas SIN pago, 3) pago + comisión. No construir pagos antes de validar inscripción.
- **Dato de mercado (del usuario):** en su zona **Playtomic no se usa**; algunos usan ATC (Alquila Tu Cancha). Booking local poco monopolizado.
- Factibilidad confirmada de: registro de jugadores en torneos, pago de inscripción por la app, comisión para la app. Todo factible, en ese orden.

---

## ⚠️ Notas / gotchas importantes para la próxima sesión
1. **Reiniciar backend** tras cambios `.java` (Stop+Run IntelliJ). En Railway redeploya solo con push.
2. **DB sin psql local** → usar Docker `postgres:18`. Conn local: `postgresql://postgres:postgres@host.docker.internal:5432/padeladmin`. La pública de Railway está en el servicio Postgres → Variables → `DATABASE_PUBLIC_URL` (el usuario la compartió una vez; conviene **rotarla** porque quedó en el chat).
3. **El "Reordenar zonas" está sin testear end-to-end** — es lo primero a validar.
4. `ddl-auto=update` + sin migraciones (Flyway pendiente). Hibernate crea tablas nuevas al arrancar.
5. **No hay tests automatizados** — todo se prueba manual / curl.
6. El usuario suele pedir cambios concretos y que se **commitee+pushee** cada uno (mensajes de commit en español, `Co-Authored-By: Claude`).

---

## 🚧 Pendientes / ideas anotadas
- **Validar "Reordenar zonas"** con escenario real (prioridad).
- Backtracking real en el scheduler (hoy greedy con ordering por restrictedness).
- Programar Ronda 2 de zona de 4 **upfront** con placeholders (se discutió, se decidió NO hacerlo por ahora; el flujo actual programa la R2 tras cargar R1).
- Integración **multi-complejo** real (hoy un torneo = un complejo; reordenar solo avisa "sumá canchas").
- Endurecer concurrencia: unique constraint en nombre de torneo + optimistic locking (`@Version`). Decidido posponer.
- Botón para **otorgar/recalcular puntos** en torneos ya COMPLETED sin awards (caso t3 quedó COMPLETED sin puntos por un test viejo).
- Monetización fase 2/3 (inscripción online → pagos MP).
- Migrar a Flyway/Liquibase; tests; CI.

---

## 📞 Responsable
**CODE SOLUTIONS.ar** — Claudio Carmusciano

*Contexto generado 2026-06-05 para continuar en otra sesión.*
