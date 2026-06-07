# Handoff — Módulo "Gestión de turnos online" (PadelAdmin)

> **Para:** una nueva sesión de Claude Code que va a arrancar este módulo.
> **Fecha:** 2026-06-07
> **Repo:** https://github.com/claudiocarmusciano/PadelAdmin (rama base: `main`)
> **Constraint del usuario:** evaluación honesta y crítica. Diferenciar hechos, inferencias y opiniones. Sin halagos ni complacencia. Commits en español, y commitear+pushear cada cambio concreto.

---

## 0. Qué hay que construir (resumen de una línea)

Un sistema de **reserva de turnos de canchas online**, como **módulo nuevo dentro del mismo proyecto PadelAdmin** (mismo backend Spring Boot + frontend React + misma base PostgreSQL), reutilizando las canchas/complejos/horarios que ya existen. Se desarrolla en la rama `feature/court-booking` y se mergea a `main` cuando esté estable.

---

## 1. Contexto del proyecto existente (PadelAdmin)

PadelAdmin es un sistema de **gestión de torneos de pádel**, ya **deployado y en uso real** en producción.

- **URL producción:** https://padeladmin-production.up.railway.app
- **Hosting:** Railway. **Una sola imagen Docker**: el frontend (Vite) se buildea y queda **servido por el backend** (Spring Boot). Postgres es un servicio gestionado de Railway.
- **Deploy automático:** cada `git push` a `main` dispara build+deploy en Railway (~2-4 min).
- ⚠️ **El sistema está vivo y en uso** (cientos de jugadores, torneos reales corriendo). Por eso este módulo se hace en **rama aparte** y no se mergea a `main` hasta estar probado.

### Stack
- **Backend:** Spring Boot **4.0.6**, Java **21**, Spring Security 6, JWT. Paquete base: `com.padeladmin.padeladmin`.
- **Frontend:** React + TypeScript + Vite + Tailwind + shadcn/ui (Radix) + recharts. Axios con baseURL relativa `/api`.
- **DB:** PostgreSQL (local: PG 18.3). `ddl-auto=update` (Hibernate crea/actualiza tablas y columnas solo al arrancar; **no hay Flyway**).

### Correr local
- Backend: `./mvnw spring-boot:run` (puerto 8080). **No recarga cambios `.java` solo** → hay que Stop+Run en IntelliJ.
- Frontend: `npm run dev` (puerto 5173, con `.env.development` apuntando a `localhost:8080/api`).
- **No hay `psql` instalado** ni Postgres accesible por CLI. Para tocar la DB se usa Docker: `docker run --rm postgres:18 psql '<conn>'`. Conn local: `postgresql://postgres:postgres@host.docker.internal:5432/padeladmin`.

### Estructura de carpetas
```
backend/src/main/java/com/padeladmin/padeladmin/
  ├─ config/        (SecurityConfig, CorsConfig, seeders)
  ├─ controller/    (REST controllers)
  ├─ dto/           (request/response DTOs por dominio)
  ├─ entity/        (JPA entities)
  ├─ enums/
  ├─ exception/     (BusinessException, ResourceNotFoundException, handler global)
  ├─ repository/    (Spring Data JPA)
  ├─ security/      (JWT)
  └─ service/
frontend/src/
  ├─ api/           (clients axios por dominio)
  ├─ components/    (ui/ = shadcn; courts/ ; etc.)
  ├─ contexts/      (AuthContext)
  ├─ pages/         (TournamentsPage, TournamentDetailPage, tournament/*Tab.tsx, …)
  ├─ types/index.ts (tipos compartidos)
  └─ lib/axios.ts
```

### Convenciones
- **Commits en español**, terminando con: `Co-Authored-By: Claude <noreply@anthropic.com>`.
- El usuario quiere **commit + push de cada cambio concreto**.
- Excepciones de negocio: lanzar `BusinessException("mensaje")` (se mapea a 400 con el mensaje). `ResourceNotFoundException("Entidad", id)` para 404.
- Errores no controlados → 500 "Error interno del servidor" (hay un handler global).

---

## 2. Decisiones ya tomadas con el usuario (NO volver a preguntarlas)

1. **Mismas canchas/complejos** que ya están en PadelAdmin → reutilizar `Complex`, `Court`, `CourtAvailability`. **No duplicar** ese modelo.
2. **Reservan ambos:** clientes finales (público) **y** el admin del club.
3. **MVP sin pago.** Primero validar el flujo de reserva. Pagos (Mercado Pago) es fase 3.
4. **Desarrollo en rama `feature/court-booking`**, mergeo a `main` cuando esté probado.

### Decisiones de MVP recomendadas (confirmar con el usuario al arrancar, pero son los defaults acordados)
- **Sin cuenta de cliente al inicio:** el cliente reserva como **invitado, con nombre + teléfono** (sin registro). Las cuentas de cliente y "mis turnos" quedan para **fase 2**.
- **La disponibilidad de turnos resta los partidos de torneo** programados en esa cancha/fecha → nunca se pisa un turno con un partido (gran ventaja de tenerlo todo junto).
- **Duración del turno configurable por cancha** (default 90 min).

### Fases
1. **MVP reservas** (esto): grilla admin + reserva pública sin pago, con anti-superposición (turnos + partidos de torneo).
2. Cuentas de cliente + "mis turnos" + cancelación por el cliente.
3. Pagos / seña con Mercado Pago (modelo recomendado: split/marketplace, cada club conecta su cuenta MP; la app cobra fee; la plata NO pasa por la cuenta de la app).

---

## 3. Código existente a REUTILIZAR (rutas exactas)

### Entidades (NO modificar su semántica; sí se pueden leer)
- `entity/Complex.java` — `id, name, address, phone, courts[]`.
- `entity/Court.java` — `id, complex, name, active (boolean), availabilities[]`.
- `entity/CourtAvailability.java` — **clave para los slots**:
  - `court`, `dayOfWeek` (**0=Lunes … 6=Domingo**), `openTime`, `closeTime`, `breakStart`, `breakEnd` (pulmón opcional, ambos null = sin pulmón).
- `entity/Match.java` — partidos de torneo. Tienen `court`, `scheduledStart`, `scheduledEnd` (LocalDateTime), `status` (`MatchStatus`). **Se usan para restar de la disponibilidad de turnos.**
- `entity/User.java` + `enums/UserRole.java` (`ADMIN`, `VIEWER`).

### Repositorios útiles
- `repository/CourtRepository.java` → `findByComplexIdAndActiveTrue(complexId)`.
- `repository/CourtAvailabilityRepository.java` → `findByCourtIdOrderByDayOfWeek(courtId)`, `findByCourtIdAndDayOfWeek(courtId, dow)`.
- `repository/ComplexRepository.java`.
- `repository/MatchRepository.java` → tiene métodos por torneo; quizás haya que **agregar** un `findByCourtIdAndScheduledStartBetween(...)` o similar para traer partidos por cancha/fecha (ver punto 6).

### Lógica de generación de slots a IMITAR (referencia algorítmica)
- `service/FixtureService.java`:
  - `generateTimeSlots(...)` y **`generateSlotsForDay(date, availability, buffers, dow, courtId, matchDuration)`** (aprox. líneas 229-260): recorre de `openTime` a `closeTime`, paso `SLOT_STEP_MINUTES`, y **saltea los slots que solapan el pulmón** (`breakStart/breakEnd`) y los buffers. Es exactamente el patrón que necesita el módulo de turnos (adaptado: paso = duración del turno, y restar reservas + partidos).
  - Helper `timesOverlap(start1, end1, start2, end2)` (aprox. línea 540) — reutilizable.
  - **Conversión de día:** `CourtAvailability.dayOfWeek` usa 0=Lun..6=Dom. `LocalDate.getDayOfWeek().getValue()` da 1=Lun..7=Dom → `dow0 = getValue() - 1`.

### Frontend a reutilizar
- `components/courts/CourtAvailabilityDialog.tsx` — diálogo de horarios por cancha (incluye el pulmón). Buen ejemplo de UI de horarios.
- `api/courtAvailability.ts`, `api/complexes.ts`, `api/matches.ts` — clientes axios de referencia.
- `pages/tournament/CalendarTab.tsx` y `FixtureTab.tsx` — ejemplos de grilla/calendario de partidos (referencia para la grilla de turnos del admin).
- `components/ui/*` — shadcn (Dialog, Select, Input, Button, Card, Badge, etc.).

---

## 4. ⚠️ Seguridad — punto crítico para el flujo PÚBLICO

`config/SecurityConfig.java` hoy tiene estas reglas (en orden):
```
OPTIONS /**                        → permitAll
/api/auth/login|register|guest     → permitAll
GET    /api/**                     → authenticated   (cualquier usuario logueado, incl. VIEWER)
POST   /api/**                     → hasRole("ADMIN")
PUT    /api/**                     → hasRole("ADMIN")
PATCH  /api/**                     → hasRole("ADMIN")
DELETE /api/**                     → hasRole("ADMIN")
anyRequest                         → permitAll        (sirve la SPA / estáticos)
```

**Implicancia:** un cliente final **no logueado** no puede hacer `POST /api/...` (lo bloquea la regla ADMIN). Para la reserva pública del MVP (invitado, sin cuenta) hay que **agregar matchers explícitos `permitAll` ANTES de las reglas genéricas**, por ejemplo:
```
GET  /api/public/**   → permitAll   (ver complejos/canchas/slots disponibles)
POST /api/public/bookings → permitAll   (crear reserva como invitado)
```
Y dejar la **gestión de turnos del admin** bajo `/api/bookings/**` (que cae en las reglas ADMIN existentes). El orden importa: los matchers más específicos van primero.

> Inferencia: conviene namespace `/api/public/...` para todo lo accesible sin login, así la regla `permitAll` es acotada y no abre de más. Validar que CORS (`config/CorsConfig.java`) permita el origen del front público (hoy permite `localhost` y `*.up.railway.app`).

---

## 5. Modelo de datos propuesto (MVP)

> `ddl-auto=update` crea las tablas nuevas solas. No hay migraciones.

### Nueva entidad `CourtBooking` (tabla `court_bookings`)
- `id` (PK)
- `court` (`@ManyToOne` → Court, not null)
- `bookingDate` (`LocalDate`, not null)
- `startTime` (`LocalTime`, not null)
- `endTime` (`LocalTime`, not null)
- `status` (enum `BookingStatus`: `CONFIRMED`, `CANCELLED`) — default CONFIRMED
- `customerName` (String, not null)
- `customerPhone` (String, not null)
- `notes` (String, opcional)
- `source` (enum `BookingSource`: `PUBLIC`, `ADMIN`) — quién lo creó
- `createdAt` (`@CreationTimestamp`)

### Nuevo enum `BookingStatus` { CONFIRMED, CANCELLED }
### Nuevo enum `BookingSource` { PUBLIC, ADMIN }

### Duración del turno
- Agregar `slotDurationMinutes` (Integer, default 90) a **`Court`** (columna nueva, nullable con default en código). Permite que cada cancha tenga su duración.

### Anti-doble-reserva (concurrencia)
- Hay un riesgo TOCTOU: dos clientes reservando el mismo slot a la vez. PadelAdmin **no usa optimistic locking** hoy.
- MVP mínimo: **re-chequear disponibilidad dentro de la transacción** justo antes de insertar, y lanzar `BusinessException` si el slot ya no está libre.
- Mejor (recomendado): **unique constraint** parcial `(court_id, booking_date, start_time)` para `status = CONFIRMED`. Postgres permite índice único parcial: `CREATE UNIQUE INDEX ... WHERE status = 'CONFIRMED'`. Como no hay Flyway, se puede crear vía `SchemaPatcher` (ver `config/SchemaPatcher.java`, ya se usa para parches de schema) o documentarlo para correr a mano.

---

## 6. Endpoints propuestos (MVP)

### Públicos (sin login) — namespace `/api/public`
- `GET /api/public/complexes` → complejos con sus canchas activas (para el selector). Puede reusar lógica existente de complejos.
- `GET /api/public/courts/{courtId}/slots?date=YYYY-MM-DD` → **lista de slots disponibles** ese día (ver algoritmo abajo).
- `POST /api/public/bookings` → crear reserva. Body: `{ courtId, date, startTime, customerName, customerPhone, notes? }`. Valida disponibilidad y crea `CourtBooking(source=PUBLIC, status=CONFIRMED)`.

### Admin (rol ADMIN) — namespace `/api/bookings`
- `GET /api/bookings?complexId=&date=YYYY-MM-DD` → turnos del día (para la grilla).
- `POST /api/bookings` → alta manual por el admin (source=ADMIN).
- `DELETE /api/bookings/{id}` → cancelar (o `PATCH` a CANCELLED).

### Algoritmo de slots disponibles (`getAvailableSlots(courtId, date)`)
1. `dow0 = date.getDayOfWeek().getValue() - 1` (0=Lun..6=Dom).
2. Buscar `CourtAvailability` de esa cancha y `dow0`. Si no hay → no hay slots ese día.
3. Generar candidatos: desde `openTime`, paso = `court.slotDurationMinutes`, mientras `cursor + duracion <= closeTime`.
4. Descartar candidatos que **solapan el pulmón** (`breakStart/breakEnd`) — usar patrón `timesOverlap`.
5. Descartar candidatos que **solapan una reserva existente** (`CourtBooking` CONFIRMED de esa cancha/fecha).
6. Descartar candidatos que **solapan un partido de torneo** programado en esa cancha/fecha (`Match` con `court_id = courtId`, misma fecha, `status` en {SCHEDULED, CONFIRMED, PLAYED}). → Puede requerir un método nuevo en `MatchRepository` tipo `findByCourtIdAndScheduledStartBetween(courtId, startOfDay, endOfDay)`.
7. Devolver los slots libres (lista de `{ startTime, endTime }`).

> ⚠️ **Punto 6 es la integración clave** que justifica tener todo junto. Inferencia: en fase 2 convendría también que el generador de fixture de torneos **evite** slots ya reservados como turno (hoy `FixtureService` no sabe de `court_bookings`). Para el MVP alcanza con que los turnos resten partidos; avisar al usuario de la asimetría.

---

## 7. Frontend propuesto (MVP)

### Vista admin (dentro de la app autenticada)
- Una sección/página nueva "Turnos" (ej. ruta `/bookings` o un tab dentro de Complejos): selector de complejo + fecha → grilla por cancha mostrando turnos del día + alta/baja manual. Reutilizar patrones de `CalendarTab.tsx`/`FixtureTab.tsx` y componentes `ui/`.

### Página pública (sin login)
- Ruta pública nueva, ej. `/reservas` (la SPA usa BrowserRouter; `SpaForwardController` ya reenvía deep links a `index.html`, así que una ruta nueva funciona).
- Flujo: elegir **complejo → cancha → fecha → ver slots disponibles → completar nombre + teléfono → reservar**.
- ⚠️ Esta página debe funcionar **sin token**. Revisar `contexts/AuthContext.tsx` y `lib/axios.ts`: el interceptor no debe exigir login ni redirigir al login en estas rutas. Los endpoints `/api/public/**` no requieren `Authorization`.
- Confirmación de reserva simple (toast / pantalla de "turno confirmado" con los datos).

---

## 8. Cómo arrancar (pasos concretos)

```bash
# Desde la raíz del repo PadelAdmin, partiendo de main actualizado
git checkout main
git pull origin main
git checkout -b feature/court-booking
```

> **Estado de `main` al momento de este handoff:** último commit relevante `fix(bracket): error 500 al regenerar un bracket con resultados cargados`. Todo lo de torneos está deployado y andando. Las features recientes incluyen: pulmón horario por cancha/día, intervalo de inicio-a-inicio, bracket auto-generado + aviso de desactualizado.

### Orden sugerido de trabajo (commits chicos)
1. **Backend modelo:** enum `BookingStatus`, `BookingSource`, entidad `CourtBooking`, repo `CourtBookingRepository`, columna `slotDurationMinutes` en `Court`. (Arranca la app → Hibernate crea la tabla.)
2. **Backend slots:** `BookingService.getAvailableSlots(courtId, date)` con el algoritmo del punto 6 (reusar patrón de `generateSlotsForDay` + `timesOverlap`). Método nuevo en `MatchRepository` para partidos por cancha/fecha.
3. **Backend endpoints públicos** (`/api/public/...`) + **admin** (`/api/bookings`) + DTOs. Ajustar `SecurityConfig` (matchers `permitAll` para `/api/public/**`, **antes** de las reglas genéricas).
4. **Anti-doble-reserva:** re-chequeo en transacción + (opcional) índice único parcial.
5. **Frontend admin:** cliente axios + grilla de turnos + alta/baja.
6. **Frontend público:** ruta `/reservas` sin login + flujo de reserva.
7. Pruebas manuales (no hay tests automatizados en el repo). Verificar conflicto turno↔partido.

### Verificación / pruebas
- **No hay tests automatizados**; todo se prueba manual / curl.
- Para probar el flujo público, recordar que `POST /api/public/bookings` debe andar **sin token**.
- Hay tooling MCP de "Preview"/Chrome en algunas sesiones para ver la UI; si no, pedir al usuario que pruebe en su iPhone/desktop tras el deploy.

---

## 9. Gotchas / notas importantes

1. **Rama aparte:** NO mergear a `main` hasta que el usuario lo apruebe (producción está en uso).
2. **`ddl-auto=update`** crea tablas/columnas nuevas solo. Para índices/constraints especiales (ej. unique parcial) ver `config/SchemaPatcher.java` o documentarlo.
3. **Reiniciar backend** tras cambios `.java` en local (Stop+Run IntelliJ). En Railway redeploya solo con push (pero ojo: Railway deploya **lo que esté en `main`**; mientras se trabaje en la rama, **no se deploya** — probar en local o pushear la rama y configurar un entorno aparte si se quiere preview).
4. **Día de la semana:** `CourtAvailability` usa **0=Lunes..6=Domingo** (no 1..7). Cuidado con la conversión desde `LocalDate`/frontend.
5. **Concurrencia:** el sistema no tiene optimistic locking; el alta de turno debe re-validar disponibilidad en la transacción.
6. **Conflicto turno↔torneo:** el MVP hace que los turnos resten partidos de torneo. La inversa (que el fixture evite turnos) NO está en el MVP → avisarlo.
7. **CORS:** `config/CorsConfig.java` permite `localhost` y `https://*.up.railway.app` (+ env `APP_CORS_EXTRA_ORIGINS`). Si el front público vive en otro dominio, agregarlo.
8. **Auth en el front público:** asegurarse de que `AuthContext`/interceptor de axios no fuerce login ni rompa en rutas públicas.

---

## 10. Wireframes textuales de las pantallas (MVP)

> Son una guía de layout y contenido, **no** un diseño cerrado. Usar los componentes `ui/` (shadcn) y el estilo dark/naranja ya existente. Mobile-first (la mayoría de los clientes reservan desde el celular).

### 10.1 Página PÚBLICA de reserva — ruta `/reservas` (sin login)

**Paso 1 — Elegir complejo y fecha**
```
┌───────────────────────────────────────────────┐
│  🎾 Reservá tu cancha                          │
│  ───────────────────────────────────────────  │
│                                                │
│  Complejo                                      │
│  [ Lagartos                            ▼ ]     │
│                                                │
│  Fecha                                         │
│  [ ‹ ]   Sáb 07 Jun 2026   [ › ]               │   ← flechas día -/+ ; o date picker
│                                                │
│  Cancha                                        │
│  ( Cancha 1 ) ( Cancha 2 ) ( Cancha 3 )        │   ← chips; default: todas / primera
│                                                │
└───────────────────────────────────────────────┘
```

**Paso 2 — Slots disponibles del día (para la cancha elegida)**
```
┌───────────────────────────────────────────────┐
│  Cancha 1 · Sáb 07 Jun                         │
│  ───────────────────────────────────────────  │
│  Turnos disponibles (90 min)                   │
│                                                │
│   [ 09:00 ]  [ 10:30 ]  [ 12:00 ]              │   ← botón verde = libre
│   [ 13:30 ]  [ 16:00 ]  [ 19:30 ]              │
│                                                │
│   ✕ 17:30  (ocupado)     ← se muestran grises  │   (opcional: mostrar ocupados deshabilitados)
│                                                │
│  * Pulmón 17:00–18:00 no disponible            │   ← si la cancha tiene pulmón ese día
│                                                │
│  Sin turnos → "No hay turnos disponibles para  │
│  esta cancha en esta fecha."                   │
└───────────────────────────────────────────────┘
```

**Paso 3 — Confirmar datos (modal o pantalla)**
```
┌───────────────────────────────────────────────┐
│  Confirmar turno                          ✕    │
│  ───────────────────────────────────────────  │
│  Lagartos · Cancha 1                           │
│  Sáb 07 Jun · 16:00 – 17:30                    │
│                                                │
│  Nombre y apellido *                           │
│  [ Juan Pérez                            ]     │
│                                                │
│  Teléfono *                                    │
│  [ 2284 55-1234                          ]     │
│                                                │
│  Nota (opcional)                               │
│  [                                       ]     │
│                                                │
│           [ Cancelar ]   [ Reservar turno ]    │
└───────────────────────────────────────────────┘
```

**Paso 4 — Confirmación**
```
┌───────────────────────────────────────────────┐
│            ✓ ¡Turno reservado!                 │
│  ───────────────────────────────────────────  │
│  Lagartos · Cancha 1                           │
│  Sáb 07 Jun · 16:00 – 17:30                    │
│  A nombre de: Juan Pérez                       │
│                                                │
│  Guardá estos datos. Si necesitás cancelar,    │
│  comunicate con el complejo.                   │   ← en fase 2: link "cancelar mi turno"
│                                                │
│            [ Reservar otro turno ]             │
└───────────────────────────────────────────────┘
```

> Notas de implementación:
> - Validar disponibilidad **otra vez** al apretar "Reservar" (otro cliente pudo tomar el slot). Si falló, mostrar "Ese turno ya fue reservado, elegí otro" y refrescar la lista.
> - No requiere token. El interceptor de axios no debe redirigir a login en `/reservas`.

### 10.2 Vista ADMIN — gestión de turnos (dentro de la app autenticada)

Ubicación sugerida: ítem nuevo en el menú ("Turnos") → ruta `/bookings`. Reutiliza patrones de `CalendarTab.tsx`.

**Grilla por día (todas las canchas del complejo en columnas)**
```
┌──────────────────────────────────────────────────────────────────┐
│  Turnos        Complejo:[ Lagartos ▼]   [‹] Sáb 07 Jun [›]  [+ Turno]│
│  ────────────────────────────────────────────────────────────────  │
│  Hora    │ Cancha 1        │ Cancha 2        │ Cancha 3            │
│  ───────────────────────────────────────────────────────────────  │
│  09:00   │ Juan Pérez      │ —  (libre)      │ Torneo 6ta Damas ▒ │  ← partido de torneo (read-only, distinto color)
│  10:30   │ —               │ Ana López       │ Torneo 6ta Damas ▒ │
│  12:00   │ Pedro G. ✕      │ —               │ —                  │  ← ✕ = botón cancelar
│  16:00   │ ▒ Pulmón ▒      │ —               │ —                  │  ← franja pulmón, no reservable
│  17:30   │ —               │ —               │ Carla M.           │
│  ───────────────────────────────────────────────────────────────  │
│  Leyenda: ░ libre   █ turno   ▒ pulmón/torneo (no disponible)      │
└──────────────────────────────────────────────────────────────────┘
```
- Cada celda **turno** muestra nombre del cliente; al tocar → ver detalle / **cancelar**.
- Las celdas de **partido de torneo** se muestran ocupadas (color distinto, sin acción) — vienen de `Match` por cancha/fecha.
- Botón **[+ Turno]** abre el mismo modal de "Confirmar turno" pero el admin elige cancha/fecha/hora libre y carga nombre+teléfono (source=ADMIN).

**Alternativa simple (lista en vez de grilla)** — útil para arrancar rápido y en mobile:
```
┌───────────────────────────────────────────────┐
│  Turnos · Lagartos · Sáb 07 Jun        [+ Turno]│
│  ───────────────────────────────────────────  │
│  16:00–17:30 · Cancha 1 · Juan Pérez   [ ✕ ]   │
│  17:30–19:00 · Cancha 3 · Carla M.     [ ✕ ]   │
│  10:30–12:00 · Cancha 2 · Ana López    [ ✕ ]   │
│  ───────────────────────────────────────────  │
│  (vacío) "No hay turnos para este día"         │
└───────────────────────────────────────────────┘
```
> Recomendación: **arrancar por la lista** (más simple y mobile-friendly) y dejar la grilla visual para una segunda iteración.

---

## 11. Fase 2 — Cuentas de cliente y autogestión

Objetivo: que el cliente tenga identidad y pueda gestionar sus turnos solo. Construir **sobre el MVP**, sin romperlo.

- **Registro/login de clientes:**
  - Agregar rol `CUSTOMER` a `enums/UserRole` (hoy `ADMIN`, `VIEWER`).
  - Reusar el flujo JWT existente (`/api/auth/register` ya es `permitAll`; revisar que asigne rol `CUSTOMER` por defecto en el registro público y NO `ADMIN`). **Hecho a verificar:** hoy `register` existe y es público — confirmar qué rol asigna antes de exponerlo a clientes.
  - Vincular `CourtBooking` opcionalmente a un `user_id` (nullable): turnos de invitado (fase 1) quedan sin user; los de clientes logueados, con user.
- **"Mis turnos":** página del cliente con sus reservas (próximas/pasadas), con opción de **cancelar** (según política del complejo: hasta X horas antes).
- **Cancelación por el cliente:** endpoint `PATCH /api/bookings/{id}/cancel` validando que el turno sea del usuario logueado y que esté dentro de la ventana permitida.
- **Política de cancelación / no-show:** configurable por complejo (ej. cancelar hasta 12 h antes).
- **Notificaciones (opcional):** recordatorio por WhatsApp/email del turno. (El proyecto hermano "FuturaTecno" ya usa WhatsApp; evaluar reutilizar enfoque, no código.)
- **Turnos fijos / recurrentes (opcional):** "todos los martes 20:00" — reserva recurrente que genera turnos a futuro.
- **Vista admin ampliada:** ver datos del cliente registrado, historial, marcar no-show.

> Decisión a tomar con el usuario al entrar en fase 2: ¿el cliente se registra con email+password, o alcanza con teléfono + código (OTP por WhatsApp)? OTP baja fricción pero suma complejidad.

---

## 12. Fase 3 — Pagos / seña con Mercado Pago

Objetivo: cobrar (o señar) el turno online. **No construir antes de validar que la gente reserva** (fase 1) y, idealmente, que hay cuentas (fase 2).

- **Modelo recomendado (del análisis previo de monetización):** **marketplace / split de Mercado Pago**. Cada club **conecta su propia cuenta de MP** (OAuth); el cobro del turno va a la cuenta del club y la app retiene un **fee/comisión**. **Evitar** que la plata pase por la cuenta de la app (carga regulatoria e impositiva).
- **Flujo de reserva con pago:**
  1. Cliente elige turno → se crea `CourtBooking` con estado nuevo `PENDING_PAYMENT` y un **hold temporal** (ej. 10 min) para que no lo tome otro.
  2. Redirección a **Checkout Pro** de MP (o brick embebido) con el `preference` del split.
  3. **Webhook** de MP (`/api/public/payments/webhook`) confirma el pago → turno pasa a `CONFIRMED`. Si expira el hold sin pago → se libera el slot (`CANCELLED`/borrado).
  4. Manejar idempotencia del webhook y reintentos de MP.
- **Seña vs pago total:** configurable por complejo (% de seña o monto fijo).
- **Conciliación / reportes:** panel admin con turnos pagados, comisiones, estado de cada pago.
- **Política de reembolso** ante cancelación: definir con el usuario (reembolso total/parcial/none según ventana).
- **Estados nuevos de `CourtBooking`:** `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.
- **Seguridad:** el webhook es público pero debe **validar la firma/credencial** de MP. Las credenciales de cada club se guardan asociadas al `Complex` (token de MP por complejo).

> Secuencia recomendada global: **1) MVP gestión/reserva (ahora) → 2) inscripción/cuenta → 3) pago + comisión.** No invertir el orden.

---

## 13. Responsable
**CODE SOLUTIONS.ar** — Claudio Carmusciano

*Documento de handoff generado para iniciar el módulo de turnos en una sesión nueva. El objetivo del MVP: reservar turnos (admin + público, sin pago) reutilizando canchas/horarios existentes y sin pisar partidos de torneo.*
