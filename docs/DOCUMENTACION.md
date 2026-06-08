# PadelAdmin — Documentación técnica completa

> Sistema de gestión de torneos de pádel. Backend Spring Boot + frontend React, una sola imagen Docker, deploy en Railway.
> **Producción:** https://padeladmin-production.up.railway.app
> **Repo:** https://github.com/claudiocarmusciano/PadelAdmin (rama `main`)
> **Responsable:** CODE SOLUTIONS.ar — Claudio Carmusciano

---

## Índice

1. [Qué es y para qué sirve](#1-qué-es-y-para-qué-sirve)
2. [Arquitectura general](#2-arquitectura-general)
3. [Stack tecnológico](#3-stack-tecnológico)
4. [Modelo de datos (entidades)](#4-modelo-de-datos-entidades)
5. [Autenticación y roles](#5-autenticación-y-roles)
6. [Flujo principal: ciclo de vida de un torneo](#6-flujo-principal-ciclo-de-vida-de-un-torneo)
7. [Zonas: armado y clasificación](#7-zonas-armado-y-clasificación)
8. [El scheduler del fixture](#8-el-scheduler-del-fixture)
9. [Bracket eliminatorio](#9-bracket-eliminatorio)
10. [Resultados y tabla de posiciones](#10-resultados-y-tabla-de-posiciones)
11. [Puntos y ranking](#11-puntos-y-ranking)
12. [API REST (referencia de endpoints)](#12-api-rest-referencia-de-endpoints)
13. [Frontend](#13-frontend)
14. [Configuración, entornos y deploy](#14-configuración-entornos-y-deploy)
15. [Decisiones de diseño y limitaciones (honesto)](#15-decisiones-de-diseño-y-limitaciones-honesto)
16. [En desarrollo / roadmap](#16-en-desarrollo--roadmap)

---

## 1. Qué es y para qué sirve

PadelAdmin es un **panel de administración de torneos de pádel**. Permite a un club:

- Mantener un **padrón de jugadores** con puntos de ranking por categoría.
- Definir **categorías**, **complejos** y **canchas** (con sus horarios).
- Crear **torneos**, inscribir **parejas**, generar **zonas** (grupos), armar el **fixture** (programación de partidos respetando horarios y restricciones), cargar **resultados**, ver la **tabla de posiciones**, generar el **cuadro eliminatorio (bracket)** y, al finalizar, **otorgar puntos** de ranking.

Tiene dos perfiles de uso: **administrador** (gestiona todo) e **invitado/espectador** (ve todo en modo lectura).

---

## 2. Arquitectura general

**Una sola aplicación desplegable.** El frontend se compila y queda **servido por el backend** como archivos estáticos. Un único contenedor Docker contiene todo. La base de datos PostgreSQL es un servicio gestionado aparte (Railway).

```
┌──────────────────────────────────────────────────────────────┐
│                    Navegador (admin / invitado)                │
│             React SPA  (BrowserRouter, axios → /api)           │
└───────────────────────────────┬──────────────────────────────┘
                                 │ HTTP (mismo origen)
                                 ▼
┌──────────────────────────────────────────────────────────────┐
│                  Imagen Docker única (Railway)                 │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Spring Boot                                            │  │
│  │   • Sirve la SPA (resources/static) + deep links        │  │
│  │   • API REST bajo /api/**  (Spring Security + JWT)       │  │
│  │   • Lógica de negocio (services) + JPA/Hibernate         │  │
│  └───────────────────────────────┬────────────────────────┘  │
└──────────────────────────────────┼───────────────────────────┘
                                    │ JDBC
                                    ▼
                       ┌────────────────────────┐
                       │   PostgreSQL (Railway)   │
                       └────────────────────────┘
```

**Construcción de la imagen (Dockerfile multi-stage):**

1. `node:20-alpine` → `npm ci` + `npm run build` (compila el frontend a `dist/`).
2. `maven:3.9-eclipse-temurin-21` → copia `dist/` a `src/main/resources/static/` y empaqueta el `.jar` de Spring Boot.
3. `eclipse-temurin:21-jre` → runtime liviano que corre el `.jar`. Expone el puerto `8080`.

**Deploy:** cada `git push` a `main` dispara build + deploy automático en Railway (~2–4 min).

**Servir la SPA:** `SecurityConfig` deja pasar los estáticos y un `SpaForwardController` reenvía los deep links de `BrowserRouter` a `index.html`. Los datos siguen detrás de `/api/**` con autenticación.

---

## 3. Stack tecnológico

| Capa | Tecnología |
|---|---|
| **Backend** | Spring Boot **4.0.6**, Java **21**, Spring Security 6, Spring Data JPA / Hibernate |
| **Auth** | JWT (token stateless, TTL configurable) |
| **DB** | PostgreSQL · `ddl-auto=update` (Hibernate crea/actualiza el esquema; **sin Flyway/Liquibase**) |
| **Frontend** | React + TypeScript + **Vite** |
| **UI** | Tailwind CSS + **shadcn/ui** (Radix) + lucide-react (íconos) + **recharts** (gráficos) |
| **Estado/Datos (front)** | **TanStack Query** (React Query) + **axios** |
| **Notificaciones UI** | sonner (toasts) |
| **Build/Deploy** | Docker multi-stage · Railway |

Paquete base backend: `com.padeladmin.padeladmin`.

---

## 4. Modelo de datos (entidades)

Hay ~23 entidades JPA. Agrupadas por dominio:

### 4.1 Maestros (padrón)
- **Category** — categoría de juego (ej: "6ta. Damas"). `id, name, description`.
- **Player** — jugador. `id, firstName, lastName, phone, telegramChatId, createdAt`.
- **PlayerCategoryPoints** — puntos de ranking **vigentes** de un jugador en una categoría. `player, category, points`. (Es el ranking "actual" que se reinicia por temporada.)
- **Complex** — complejo deportivo. `id, name, address, phone, courts[]`.
- **Court** — cancha. `id, complex, name, active, slotDurationMinutes*, availabilities[]`.
- **CourtAvailability** — horario de una cancha por **día de semana** (`0=Lunes … 6=Domingo`). `court, dayOfWeek, openTime, closeTime, breakStart, breakEnd`. El par `breakStart/breakEnd` es el **"pulmón horario"** opcional (franja donde no se programan partidos).
- **User** — usuario del sistema. `id, email, passwordHash, role (ADMIN|VIEWER), active`.

### 4.2 Torneo y parejas
- **Tournament** — `id, name, startDate, endDate, category, complex, matchDurationMinutes, minIntervalMinutes, status (DRAFT|ACTIVE|COMPLETED), zoneDays[], createdAt`.
  - `zoneDays`: días de semana habilitados para jugar (1=Lun…7=Dom). Vacío = todos. Deseleccionar un día lo **bloquea para todas las parejas**.
- **Pair** — pareja inscripta en un torneo. `id, tournament, totalPoints, players[], constraints[]`.
- **PairPlayer** — vincula 2 jugadores a una pareja, cada uno con la **categoría** con la que aporta sus puntos. `pair, player, category`.
- **PairScheduleConstraint** — restricción o preferencia horaria de una pareja. `pair, constraintType (RESTRICTION|PREFERENCE), dayOfWeek (0=Lun…6=Dom), slotStart, slotEnd`.
  - **RESTRICTION** = la pareja **NO** puede jugar en esa franja (hard).
  - **PREFERENCE** = la pareja **prefiere** jugar en esa franja (soft, best-effort).

### 4.3 Zonas y partidos
- **Zone** — grupo del torneo. `id, tournament, name (A, B, C…), zoneSize (3|4), zoneOrder`.
- **ZonePair** — pareja dentro de una zona con su **posición de siembra**. `zone, pair, position`.
- **Match** — partido. `id, tournament, phase (ZONE|ELIMINATION), zone, zoneRound, pair1, pair2, court, scheduledStart, scheduledEnd, status, bye, eliminationRound, bracketSlot, createdAt`.
- **MatchResult** — resultado de un partido jugado. `match, pair1Score, pair2Score (sets), winnerPair, walkover, walkoverId, sets[]`.
- **MatchSet** — games de cada set. `matchResult, setNumber, pair1Games, pair2Games`.

### 4.4 Puntos / config
- **PointConfig** — puntos que otorga cada **etapa** alcanzada (PARTICIPANT, ZONE_PASS, ROUND_32, ROUND_16, ROUND_8/octavos, QUARTERFINAL, SEMIFINAL, FINALIST, CHAMPION).
- **TournamentPointAward** — **historial plano** de puntos otorgados (snapshot con nombres, sin FK dura). Sobrevive al borrado del torneo y a la limpieza de temporada.
- **GlobalSettings** — parámetros globales (legacy; la UI ya no los expone).
- **TournamentBuffer** — "pulmón" a nivel torneo por día de semana (franja global sin partidos). `tournament, dayOfWeek, bufferStart, bufferEnd`.

### 4.5 Otras (parcialmente usadas / legado)
- **AvailabilityWindow**, **PlayerAvailability** — modelo alternativo de ventanas de disponibilidad por jugador (poco usado).
- **NotificationLog** + enums `NotificationType/Status` — andamiaje para notificaciones (no activo).

### Enums
| Enum | Valores |
|---|---|
| `TournamentStatus` | DRAFT, ACTIVE, COMPLETED |
| `MatchPhase` | ZONE, ELIMINATION |
| `MatchStatus` | PENDING, SCHEDULED, CONFIRMED, PLAYED, CANCELLED |
| `ConstraintType` | PREFERENCE, RESTRICTION |
| `UserRole` | ADMIN, VIEWER |
| `TournamentStage` | etapas para puntos (PARTICIPANT … CHAMPION) |
| `WindowStatus`, `NotificationType`, `NotificationStatus` | (modelos secundarios) |

---

## 5. Autenticación y roles

- **JWT stateless.** Al login, el backend emite un token con `exp`. El frontend lo guarda y lo manda en `Authorization: Bearer …`.
- **TTL** configurable (`app.jwt.ttl-hours`, por defecto 8 h). El frontend **chequea la expiración del lado del cliente** (decodifica `exp`) al abrir la app y antes de cada request → **auto-logout** al vencer.
- **No autenticado** → backend responde **401** (vía `AuthenticationEntryPoint`). **Autenticado sin permiso** (VIEWER intentando escribir) → **403**.

### Roles
- **ADMIN** — gestiona todo (crear/editar/borrar).
- **VIEWER (invitado)** — solo lectura. Ve todo, pero el backend bloquea las escrituras (403) y el frontend oculta los botones de admin (y datos sensibles como las restricciones horarias de las parejas).

### Reglas de seguridad (SecurityConfig, en orden)
```
OPTIONS /**                         → permitAll
/api/auth/login | register | guest  → permitAll
GET    /api/**                      → authenticated   (ADMIN o VIEWER)
POST   /api/**                      → hasRole(ADMIN)
PUT    /api/**                      → hasRole(ADMIN)
PATCH  /api/**                      → hasRole(ADMIN)
DELETE /api/**                      → hasRole(ADMIN)
anyRequest                          → permitAll        (SPA / estáticos)
```

### Seeders
- **AdminSeeder** — crea el usuario admin a partir de variables de entorno (`ADMIN_EMAIL` / `ADMIN_PASSWORD`).
- **GuestSeeder** — crea `invitado@padel.com` (rol VIEWER). `POST /api/auth/guest` emite un token VIEWER sin password (botón "Ingresar como invitado" en el login).

---

## 6. Flujo principal: ciclo de vida de un torneo

```
   (Maestros)                         (Por torneo)
 Categorías  ─┐
 Jugadores   ─┤
 Complejos/  ─┤
 Canchas/    ─┤
 Horarios    ─┘
                 1. Crear torneo (DRAFT)
                        │  nombre, fechas, categoría, complejo,
                        │  duración de partido, intervalo mínimo
                        ▼
                 2. Inscribir parejas  (2 jugadores c/u + categoría de aporte)
                        │
                        ▼
                 3. Generar zonas  (snake por puntaje)  ── editable: mover/intercambiar parejas
                        │
                        ▼
                 4. Elegir días de juego (zoneDays) + verificar horarios de canchas
                        │
                        ▼
                 5. Generar fixture  ── scheduler (backtracking → greedy)
                        │             ── el torneo pasa a ACTIVE si llegó la fecha de inicio
                        │             ── "Reordenar zonas" si quedan pendientes
                        ▼
                 6. Cargar resultados de zona (sets / W.O.)
                        │             ── zona de 4: se crea la Ronda 2 al cargar la R1
                        ▼
                 7. Tabla de posiciones (en vivo) → clasificados
                        │
                        ▼
                 8. Bracket: vista previa → (zonas completas) auto-generación con nombres reales
                        │             ── cargar resultados; el ganador avanza
                        │             ── programar cancha/día/hora manual por partido
                        ▼
                 9. Marcar torneo como COMPLETED → otorga puntos de ranking
```

**Estados del torneo:**
- **DRAFT** — borrador; edición libre.
- **ACTIVE** — se alcanza **automáticamente** al generar el fixture si ya llegó la fecha de inicio (no se setea a mano).
- **COMPLETED** — al finalizar; dispara el otorgamiento de puntos.

> Nota de edición: el torneo se puede editar mientras esté en DRAFT, y ciertos campos (como el **intervalo mínimo**) también con el fixture ya generado **siempre que no haya resultados cargados** (se avisa que hay que regenerar el fixture).

---

## 7. Zonas: armado y clasificación

### Generación (snake)
- Las parejas se ordenan por puntaje (`totalPoints`) y se reparten en zonas con **siembra serpiente (snake)** para equilibrar.
- **Mínimo de parejas para generar zonas: 6.** Soporta zonas de **3** y **4**.
- Detalle del snake para zonas de 4: a partir de la 3ª posición se llena **fila por fila en orden de zona (A→B→C)**.
- Edición de zonas (antes de tener fixture/resultados): **mover** una pareja de zona o **intercambiar** dos. Eso **borra el fixture** (hay que regenerarlo).

### Partidos por zona
- **Zona de 3:** todos contra todos → 3 partidos: (1-2), (1-3), (2-3). Todos `zoneRound = null`.
- **Zona de 4:** se juega en 2 rondas.
  - **Ronda 1** (`zoneRound=1`): 1°vs4° y 2°vs3°.
  - **Ronda 2** (`zoneRound=2`): se crea **al cargar los resultados de la R1** → **ganador vs ganador** y **perdedor vs perdedor**. Se programan con "Programar Ronda 2".

### Clasificación (desempates oficiales)
- **Zona de 3** — orden por: 1) victorias, 2) dif. de sets, 3) dif. de games, 4) games a favor, 5) games en contra, 6) head-to-head, 7) sorteo (no automatizable). Clasifican **2**.
- **Zona de 4** — 1°= ganador del partido de ganadores, 2°= perdedor del partido de ganadores, 3°= ganador del partido de perdedores. Clasifican **3**.

La **tabla de posiciones** (`GET /api/zones/{id}/standings`) se calcula **en vivo** desde los resultados (sin cache).

---

## 8. El scheduler del fixture

El corazón del sistema. Asigna a cada partido un **slot** (cancha + fecha + hora) respetando un conjunto de reglas.

### 8.1 Generación de slots disponibles
Para cada cancha activa del complejo y cada día del rango del torneo:
- Se toma la `CourtAvailability` de ese **día de semana** (apertura/cierre).
- Se generan slots desde la apertura, paso **`SLOT_STEP_MINUTES = 30`**, mientras `inicio + duración ≤ cierre`.
- Se **descartan** los slots que:
  - caen fuera de los **días habilitados** del torneo (`zoneDays`),
  - solapan el **pulmón horario** de esa cancha/día (`breakStart`–`breakEnd`),
  - solapan un **buffer** del torneo (`TournamentBuffer`).

### 8.2 Restricciones al ubicar un partido (`isValidSlot`)
Un slot es válido para un partido si cumple **todas** las reglas duras (*hard*):
1. **Cancha libre** en ese slot (no solapa otro partido en la misma cancha).
2. **Parejas libres**: ninguna de las 2 parejas tiene otro partido solapado.
3. **Intervalo mínimo** entre partidos de la **misma pareja**, medido de **INICIO a INICIO** (ej: intervalo 120 → si juega 10:00, el próximo no puede empezar antes de 12:00).
4. **Orden de rondas** en zona de 4: la Ronda 2 debe empezar **después de que termine** la Ronda 1 de sus parejas.
5. **Restricciones horarias** de las parejas (solo fase de zona): el slot no puede solapar una franja `RESTRICTION`.

Las **preferencias** (`PREFERENCE`) son *soft*: se intentan respetar, pero se relajan si no hay forma de ubicar el partido.

### 8.3 Algoritmo: backtracking con fallback a greedy
1. **Ordenamiento MRV** (*most-constrained-first*): los partidos con **menos slots compatibles** se procesan primero.
2. **Backtracking** (con *forward-checking* y tope de **3 segundos**): busca una asignación **completa** (0 pendientes) respetando las hard constraints; prueba primero los slots que cumplen las preferencias.
3. Si el backtracking **encuentra solución completa** → listo (0 pendientes).
4. Si **no** existe solución completa o se agota el tiempo → **fallback a greedy** *best-effort* (asigna a cada partido el primer slot válido, sin deshacer; programa lo que pueda). **Nunca queda peor que el greedy puro.**

### 8.4 "Reordenar zonas para programar todo"
Cuando quedan partidos sin programar, un botón (solo si hay pendientes y sin resultados) intenta **intercambiar parejas entre zonas** para que entren todos:
- Las **cabezas de zona** (posición 1) nunca se mueven.
- Intercambios **de a pares**, independientes desde la config base, escalando desde el fondo.
- Evalúa **in-memory** (genera matches + scheduler sin persistir) cada swap y aplica el **mejor** (menos pendientes). Reporta qué zonas se intercambiaron.

### 8.5 Mover un partido a mano (Calendario)
En el Calendario, "Mover partido" abre una grilla de horarios por cancha/día en **verde** (válidos) / **rojo** (inválidos, con motivo), reutilizando las mismas reglas del scheduler. El backend **re-valida** al persistir (`PATCH /api/matches/{id}/move`) y rechaza destinos inválidos.

---

## 9. Bracket eliminatorio

### 9.1 Siembra (templates FAP)
- `bracketSize = nextPowerOf2(numClasificados)`. Las posiciones sobrantes son **BYE**.
- El **orden de cruces** de la primera ronda sigue **templates oficiales de la Federación Argentina de Pádel**, elegidos según `numClasificados` + `totalInscriptos` (hay implementados muchos casos, de bracket 4 a 32).
- **Resolución de conflictos de zona**: se evita que dos parejas de la misma zona se crucen en primera ronda.
- Los partidos con BYE se marcan **PLAYED** y la pareja avanza automáticamente.

### 9.2 Vista previa vs bracket real
- **Vista previa** (`preview = true`): si las zonas **no están completas**, el cuadro se muestra de forma **estructural** con etiquetas por posición (ej: *"1º Zona A vs 2º Zona D"*, *"Ganador"*, *"BYE"*). No persiste nada.
- **Auto-generación**: cuando **todas las zonas están jugadas**, al abrir la pestaña Bracket se **genera el cuadro real** automáticamente con los nombres de las parejas clasificadas.
- **Desactualizado** (`stale = true`): si se **corrige un resultado de zona** después de generar el cuadro y la clasificación cambia, se **avisa** y el admin decide regenerar. Si el bracket **aún no empezó** (sin partidos reales jugados), se invalida solo y se regenera al verlo.

### 9.3 Carga de resultados y avance
- Al cargar el resultado de un partido de eliminación, el **ganador avanza** automáticamente al siguiente cruce (`advanceWinner`). Editar un resultado **revierte** el avance previo (si el siguiente no se jugó).

### 9.4 Programación manual
- El **admin** puede asignar **cancha, día y horario** a cada partido del cuadro (incluso con parejas "Por definir", para publicar la agenda). El **invitado** solo **ve** esa programación.

---

## 10. Resultados y tabla de posiciones

- **Cargar resultado**: 2 o 3 sets (games por set). Se calcula el ganador. También **W.O.** (walkover): se elige la pareja ausente y se carga derrota 6-0/6-0.
- **Editar resultado**: borra el resultado anterior y registra el nuevo (en eliminación, revierte el avance si corresponde).
- **Visualización**: en el bracket y en el calendario, el marcador muestra los **games de la pareja ganadora primero** (ej: `6-1 7-5`).
- **Tabla de posiciones**: se calcula en vivo por zona con los desempates oficiales (ver §7).

---

## 11. Puntos y ranking

### Otorgamiento
- Cada **etapa** alcanzada tiene un puntaje configurable (`PointConfig`): Participante, Pasa zona, 32avos, 16avos, Octavos, Cuartos, Semifinal, Finalista, Campeón.
- Al marcar el torneo como **COMPLETED**, se otorgan puntos a cada jugador según la **mejor instancia** alcanzada por su pareja (solo la mejor, **no acumulativo entre etapas**), y ese puntaje se **suma** al ranking vigente del jugador en esa categoría (acumulativo sobre el histórico).
- Se guarda un registro en **`TournamentPointAward`** (snapshot plano que sobrevive a borrados).

### Nueva temporada
- "Reiniciar puntos de temporada" pone en **0** los puntos vigentes de todos los jugadores en todas las categorías, **conservando el historial** de `TournamentPointAward`.

---

## 12. API REST (referencia de endpoints)

> Todo bajo `/api`. **GET** = autenticado (ADMIN o VIEWER). **POST/PUT/PATCH/DELETE** = ADMIN. `auth/login|register|guest` = público.

### Auth — `/api/auth`
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/register` | Registro de usuario |
| POST | `/login` | Login (devuelve JWT) |
| POST | `/guest` | Token de invitado (VIEWER) |
| GET | `/me` | Datos del usuario logueado |

### Torneos — `/api/tournaments`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `` | Listar torneos |
| GET | `/{id}` | Detalle |
| POST | `` | Crear |
| PUT | `/{id}` | Editar |
| PATCH | `/{id}/status` | Cambiar estado (ej: COMPLETED) |
| PUT | `/{id}/zone-days` | Días de juego habilitados |
| DELETE | `/{id}` | Eliminar (con todo lo asociado) |

### Parejas — `/api/tournaments/{tid}/pairs`
| Método | Ruta | Descripción |
|---|---|---|
| GET / GET `/{pairId}` | — | Listar / detalle |
| POST | `` | Crear pareja |
| DELETE | `/{pairId}` | Borrar pareja (limpia zonas+fixture) |
| POST | `/{pairId}/constraints` | Agregar restricción/preferencia |
| DELETE | `/{pairId}/constraints/{cid}` | Quitar restricción/preferencia |

### Zonas — `/api/tournaments/{tid}/zones`
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/generate` | Generar zonas (snake) |
| GET / GET `/{zoneId}` | — | Listar / detalle |
| PATCH | `/pairs/{pairId}/move` | Mover pareja de zona |
| PATCH | `/pairs/{pairId}/swap` | Intercambiar parejas |

### Fixture — `/api/tournaments/{tid}/fixture`
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/generate` | Generar/rehacer fixture |
| GET | `` | Obtener fixture |
| POST | `/schedule-pending` | Programar pendientes (Ronda 2) |
| POST | `/reorganize` | Reordenar zonas para programar todo |

### Eliminación — `/api/tournaments/{tid}/elimination`
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/generate` | Generar/regenerar bracket |
| GET | `` | Bracket (real, preview o auto-generado) |

### Partidos — `/api/matches/{mid}`
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/result` | Cargar resultado |
| PUT | `/result` | Editar resultado |
| GET | `/result` | Obtener resultado |
| PATCH | `/court` | Asignar cancha/horario |
| GET | `/placements` | Destinos válidos para mover (verde/rojo) |
| PATCH | `/move` | Mover (valida destino) |
| GET | `/api/zones/{zid}/standings` | Tabla de posiciones de la zona |

### Maestros y config
| Recurso | Rutas |
|---|---|
| **Categorías** `/api/categories` | GET, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}` |
| **Jugadores** `/api/players` | GET, GET `/with-categories`, GET `/{id}`, GET `/{id}/stats`, POST, PUT `/{id}`, DELETE `/{id}`, GET/PUT `/{id}/categories`, DELETE `/{id}/categories/{cid}` |
| **Complejos** `/api/complexes` | GET, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}`, GET/POST `/{cid}/courts`, PUT/DELETE `/{cid}/courts/{courtId}`, PATCH `/{cid}/courts/{courtId}/toggle` |
| **Horarios de cancha** `/api/courts/{courtId}/availability` | GET, POST, DELETE `/{availabilityId}`, POST `/copy-to-complex` |
| **Buffers** `/api/tournaments/{tid}/buffers` | GET, POST, DELETE `/{bufferId}` |
| **Settings** `/api/settings` | GET/PUT `/points`, POST `/points/reset`, GET/PUT `/general` |

---

## 13. Frontend

### Estructura
```
frontend/src/
  api/         → clientes axios por dominio (auth, tournaments, pairs, players,
                  categories, complexes, courtAvailability, matches, settings)
  pages/       → LoginPage, RegisterPage, TournamentsPage, PlayersPage,
                  CategoriesPage, ComplexesPage, SettingsPage, TournamentDetailPage
  pages/tournament/ → tabs del torneo:
                  PairsTab, ZonesTab, FixtureTab, CalendarTab, BracketTab
  components/  → ui/ (shadcn), courts/CourtAvailabilityDialog, etc.
  contexts/    → AuthContext (sesión + rol)
  lib/         → axios (baseURL /api, interceptores), utils
  types/       → tipos TypeScript compartidos
```

### Pestañas del torneo (TournamentDetailPage)
- **Parejas** — alta/baja de parejas, restricciones/preferencias horarias (ocultas al invitado).
- **Zonas** — generar zonas, mover/intercambiar parejas, tabla de posiciones por zona.
- **Fixture** — días de juego, generar/rehacer, reordenar zonas, programar Ronda 2, listado de partidos con **filtros** (jugador con búsqueda insensible a acentos, zona, estado).
- **Calendario** — grilla por día/cancha; "Mover partido" (verde/rojo).
- **Bracket** — cuadro eliminatorio (preview/real), cargar resultados, programar cancha/día/hora.

### Datos y caché
- **React Query** con `staleTime` de 30 s. Las mutaciones **invalidan** las queries relevantes (`fixture`, `bracket`, `standings`, `pairs`, etc.) para refrescar.

---

## 14. Configuración, entornos y deploy

### Variables de entorno (externalizadas)
| Variable | Uso |
|---|---|
| `spring.datasource.url / username / password` | Conexión a PostgreSQL |
| `JPA_DDL_AUTO` (`spring.jpa.hibernate.ddl-auto`) | `update` (crea/actualiza esquema) |
| `PORT` (`server.port`) | Puerto HTTP (8080) |
| `app.jwt.secret` / `app.jwt.ttl-hours` | Firma y duración del JWT |
| `app.admin.email` / `app.admin.password` | Credenciales del admin (seeder) |
| `APP_CORS_EXTRA_ORIGINS` | Orígenes CORS extra |

### Correr en local
- **Backend:** `./mvnw spring-boot:run` (puerto 8080). No recarga cambios `.java` solo → Stop+Run en el IDE.
- **Frontend:** `npm run dev` (puerto 5173, con `.env.development` apuntando a `localhost:8080/api`).
- **DB:** PostgreSQL local. Sin `psql` instalado, se usa Docker: `docker run --rm postgres:18 psql '<conn>'`.

### Deploy
- Push a `main` → build + deploy en Railway (imagen Docker única). La DB es un servicio Postgres gestionado.
- CORS permite `localhost` y `https://*.up.railway.app` (+ `APP_CORS_EXTRA_ORIGINS`).

---

## 15. Decisiones de diseño y limitaciones (honesto)

- **Sin migraciones (Flyway/Liquibase).** `ddl-auto=update` crea tablas/columnas nuevas al arrancar. Para índices/constraints especiales hay un `SchemaPatcher`. → *Pendiente migrar a versionado real.*
- **Sin tests automatizados.** Todo se prueba manual / curl.
- **Concurrencia: "el último que guarda gana".** No hay *optimistic locking* (`@Version`). Dos admins editando el **mismo dato** simultáneamente pueden pisarse (lost update silencioso). El nombre único de torneo se valida a nivel servicio (sin unique en DB) → posible TOCTOU.
- **Scheduler:** el backtracking tiene tope de 3 s y cae a greedy; no garantiza el óptimo global en casos muy grandes, pero nunca queda peor que greedy.
- **Pulmón / horarios** son por **día de semana**, no por fecha puntual.
- **Multi-complejo:** un torneo usa un complejo; "reordenar zonas" solo avisa "sumá canchas", no integra varios complejos.

---

## 16. En desarrollo / roadmap

- **Turnos online (reserva de canchas)** — módulo nuevo en la rama `feature/court-booking` (reserva pública sin pago + gestión admin, reutilizando canchas/horarios; resta los partidos de torneo para no superponer). Ver `HANDOFF_TURNOS_ONLINE.md`. **En stand-by.**
- **Ronda 2 de zona de 4 programada por adelantado** (con placeholders y restricciones de las 4 parejas) — discutido, pendiente.
- **Backtracking + mejoras de scheduler**, multi-complejo real, optimistic locking, migraciones, tests/CI.
- **Monetización** (fases): inscripción online → pago/seña con Mercado Pago (modelo marketplace/split).

---

*Documento generado para PadelAdmin. Para el detalle de implementación, ver el código en `backend/src/main/java/com/padeladmin/padeladmin/` y `frontend/src/`.*
