# PadelAdmin — Plan de implementación de Multi-Tenancy

> Cómo transformar la app de **un club** a **muchos clubes aislados** que comparten un **único padrón de jugadores**.
> Plano de obra **antes de codear**. Se implementa en una **rama aparte** con migración de datos seria.

---

## 0. Decisiones tomadas (base del plan)

| # | Decisión |
|---|---|
| 1 | **Ranking, categorías y puntos = POR CLUB.** Lo único compartido es la **identidad del jugador**. |
| 2 | Cada club gestiona sus propias categorías / puntos / ranking. |
| 3 | **Club = complejo** (un club tiene varias canchas). |
| 4 | Sistema de cuentas nuevo + **email real vía Gmail (SMTP)**. |
| 5 | **DNI = clave única OBLIGATORIA** del jugador (evita duplicados, habilita el perfil compartido). **Email obligatorio**, con función de **activar/cambiar la primera contraseña** y **recuperarla**. Login del jugador = **DNI + contraseña**. |
| 6 | Se mantiene **vista pública sin login** (espectador). |

**Consecuencia clave del #1:** lo único global es el **Player** (identidad). **Todo lo demás es por club**: categorías, puntos, torneos, parejas, partidos, zonas, canchas, turnos, horarios.

---

## 1. Roles

| Rol | Quién | Puede |
|---|---|---|
| **SUPER_ADMIN** | Vos (CODE SOLUTIONS) | Único que **da de alta clubes**. Genera la contraseña inicial del club. Ve/gestiona la plataforma. No gestiona el día a día de un club. |
| **CLUB** | Cada club | Gestiona **solo lo suyo**: categorías, jugadores (sumar del padrón compartido / crear nuevos), parejas, torneos, partidos, zonas, canchas, turnos, horarios, ranking. **Cambio de contraseña obligatorio en el primer login.** |
| **PLAYER** | Jugador | Cuenta propia (DNI + email + contraseña). Se da de alta **él mismo** o lo **crea un club**. Ve su info (ranking en los clubes donde juega, sus torneos, sus turnos), se inscribe, reserva. **Read-only sobre lo demás.** |
| **(público)** | Sin login | Vista de **espectador**: ve torneos, llaves, ranking, cronograma de un club. Sin gestionar. |

---

## 2. Modelo de datos: qué es global vs club-scoped

### 2.1 GLOBAL (compartido entre todos los clubes)
- **`Player`** — la identidad del jugador. Campos: `id, firstName, lastName, dni (ÚNICO, obligatorio en altas nuevas), email (obligatorio en altas nuevas), phone, accountActivated (bool), createdAt`. **No tiene `club_id`.** *(Los jugadores legacy sin DNI/email quedan como excepción — ver §5.)*
- **`User`** — cuentas de acceso (ver §4). También global; se asocia a un club (rol CLUB) o a un player (rol PLAYER).

### 2.2 POR CLUB (club-scoped — llevan `club_id` directa o indirectamente)
| Entidad | Cómo queda scoped |
|---|---|
| **`Club`** (NUEVA, = el ex `Complex`) | Es el **tenant**. Campos: `id, name, address, phone, active, createdAt`. **Club = complejo.** |
| **`Court`** | `club_id` (antes colgaba de `Complex`). |
| **`CourtAvailability`** | vía `Court` (incluye el pulmón). |
| **`Category`** | **agrega `club_id`** (antes era global). Cada club tiene sus propias categorías, aunque se llamen igual ("6ta. Caballeros" de Club A ≠ de Club B). |
| **`PlayerCategoryPoints`** | `(player, category)` → como `Category` ahora es del club, los puntos quedan **separados por club** automáticamente. |
| **`Tournament`** | **agrega `club_id`**. |
| **`Pair`, `PairPlayer`, `PairScheduleConstraint`** | vía `Tournament`. |
| **`Zone`, `ZonePair`, `Match`, `MatchResult`, `MatchSet`** | vía `Tournament`. |
| **`PointConfig`, `TournamentBuffer`, `GlobalSettings`** | pasan a ser **por club** (`club_id`). |
| **`TournamentPointAward`** | `club_id` (historial de puntos por club). |
| **`CourtBooking`** (turnos, rama booking) | vía `Court` → club. |

> **Idea central:** un jugador (global) puede estar en el padrón de varios clubes. En cada club tiene **sus propias categorías y puntos**, totalmente independientes. Ej: "Juan Pérez" tiene 18 pts en *6ta* del Club A y 5 pts en *7ma* del Club B, sin relación entre sí.

### 2.3 Relación Player ↔ Club
- No hace falta una tabla "membresía" obligatoria: un jugador "pertenece" a un club **de hecho** cuando ese club le asigna puntos en una categoría (`PlayerCategoryPoints` con categoría del club) o lo inscribe en un torneo.
- *(opcional)* Se puede agregar una tabla `ClubPlayer (club_id, player_id)` para listar "jugadores de mi club" aunque todavía no tengan puntos. **Recomendado** para que el club arme su listado sin contaminar el de otros.

---

## 3. Aislamiento de datos (lo más importante para que sea seguro)

**Regla de oro:** ningún club puede ver ni tocar datos de otro. Se garantiza así:

1. El **JWT** del usuario CLUB lleva su `clubId`. El de PLAYER lleva su `playerId`. El de SUPER_ADMIN, una marca de plataforma.
2. **Toda consulta/escritura club-scoped filtra por el `clubId` del token** (no por un id que venga del request → si no, un club podría pedir datos de otro cambiando el id en la URL).
3. Implementación recomendada: un **filtro/interceptor de tenant** + chequeo en cada service ("este torneo/categoría/cancha pertenece a mi club, si no → 403/404").
4. El **padrón de jugadores (global)** es accesible para buscar/sumar, pero **editar el perfil** queda restringido (el propio jugador o el super-admin); los **puntos** solo se otorgan por torneos del club.

> Este es el **riesgo de seguridad #1** (fuga entre tenants). Hay que testearlo explícitamente: loguear como Club A e intentar leer/escribir datos de Club B → debe fallar.

---

## 4. Autenticación y cuentas

### 4.1 Modelo de usuarios
- `User`: `id, email (único), passwordHash, role (SUPER_ADMIN|CLUB|PLAYER), active, mustChangePassword (bool), createdAt`.
- `role=CLUB` → `club_id`. `role=PLAYER` → `player_id`.
- El JWT incluye `role` + (`clubId` | `playerId`).

### 4.2 Alta de club (super-admin)
1. El club te pasa su **email**.
2. Vos creás el `Club` + un `User(role=CLUB, mustChangePassword=true)` con una **contraseña generada**.
3. Se la pasás (o se la manda el sistema por email).
4. En el **primer login**, `mustChangePassword=true` → la app **obliga a cambiarla** antes de seguir.

### 4.3 Cuentas de jugador
**Clave única = DNI.** **Email = obligatorio** (canal de activación y recuperación). **Login del jugador = DNI + contraseña.**

Dos estados del jugador:
- **Con cuenta activada** → tiene DNI + email + **contraseña seteada por él**; puede **loguearse** y ver su perfil (sus torneos/turnos/ranking en cada club). Es la identidad compartida "de verdad".
- **Sin activar** → existe con DNI + email (lo creó un club o se está registrando) pero **todavía no seteó su contraseña**; aún no puede entrar hasta activarla por email.

Flujos:
- **Auto-registro:** el jugador se registra con **DNI + email** → recibe un **link por email** para **setear su contraseña** y activar.
- **Alta por el club:** el club lo crea con **DNI + email** → el jugador recibe el email para **activar y poner su contraseña** la primera vez.
- **"Olvidé mi contraseña":** email con link de reset.
- **Dedup por DNI:** el DNI (único) garantiza que "Juan Pérez del Club A" y "del Club B" sean **el mismo registro**. Si un club intenta crear un DNI ya existente → se **vincula** al jugador existente del padrón (no se duplica).

### 4.4 Infraestructura de email (Gmail SMTP)
- `spring-boot-starter-mail` + **Gmail SMTP** (`smtp.gmail.com:587`, STARTTLS, **App Password** de la cuenta de Gmail — no la contraseña normal).
- Variables: `MAIL_USERNAME`, `MAIL_PASSWORD` (app password), remitente.
- Plantillas: activación de cuenta, reset de contraseña, (a futuro) confirmación de inscripción/turno.
- ⚠️ **Límite honesto:** Gmail tiene un tope (~500 mails/día) y puede marcar spam. Sirve para arrancar; si crece, migrar a un proveedor transaccional (Resend/SendGrid/Mailgun).

---

## 5. Migración de los datos actuales → "Club #1"

Los datos de hoy (un solo club) se asignan a un club nuevo, **sin perder nada**:

1. Crear `Club #1` (= el complejo actual, "Lagartos"/"Jardin"/etc.).
2. Asignar a Club #1: las **canchas** actuales, las **categorías** actuales (pasan a ser de Club #1), todos los **torneos** actuales, `PointConfig`, buffers.
3. Los **jugadores** quedan en el **padrón global** (se desacoplan del club). Sus `PlayerCategoryPoints` siguen apuntando a las categorías (ahora de Club #1) → sus puntos pasan a ser de Club #1.
4. Usuarios: `admin@padel.com` se convierte en **SUPER_ADMIN** (vos). Se crea un `User(role=CLUB)` para Club #1 (quien lo opere).
5. **Jugadores legacy (los ~432 actuales) sin DNI/email:** quedan como **excepción grandfathered** — siguen existiendo y jugando en Club #1, pero **no pueden loguearse** hasta que se les complete **DNI + email** y activen su cuenta. Las altas **nuevas** sí exigen DNI + email. Cuando un jugador legacy se registra con su DNI real, se **vincula** a su registro existente (no se duplica).

> La migración se hace con un script idempotente (o un `SchemaPatcher`), probado primero en una copia. **No** a mano sobre producción.

---

## 6. Fases de implementación (orden sugerido, en rama aparte)

```
Fase 1 · Modelo + migración (sin romper nada)
   • Entidad Club; club_id en Category, Tournament, Court (merge Complex→Club),
     PointConfig, buffers, awards.
   • Migración: crear Club #1 y asignar lo existente.
   • Player: agregar dni, email (nullable). User: role ampliado + club_id/player_id + mustChangePassword.

Fase 2 · Aislamiento de tenant (backend)
   • JWT con clubId/playerId. Filtro de tenant. Chequeos "pertenece a mi club".
   • Endpoints de super-admin: alta/baja/listado de clubes.
   • TEST de aislamiento (Club A no ve B).

Fase 3 · Auth de clubes (frontend + backend)
   • Login por club; primer login fuerza cambio de contraseña.
   • Cada club ve solo lo suyo (la UI actual, pero scoped).
   • Panel super-admin para gestionar clubes.

Fase 4 · Cuentas de jugador + email  (la parte más grande)
   • Registro/activación por email (Gmail SMTP), reset de contraseña.
   • Perfil del jugador (ve su ranking por club, sus torneos/turnos).
   • Dedup por DNI (cuando se active obligatorio).

Fase 5 · Vista pública (espectador)
   • Páginas read-only sin login: torneos, llaves, ranking, cronograma de un club.

Fase 6 (después) · Inscripción online + pagos (otro plan aparte)
```

---

## 7. Riesgos y mitigaciones (honesto)

| Riesgo | Mitigación |
|---|---|
| **Fuga de datos entre clubes** (lo más grave) | Filtrar SIEMPRE por el `clubId` del token, nunca por id del request. Test explícito de aislamiento. |
| **Migración rompe datos en producción** | Script idempotente, probado en copia, hecho en rama; backup antes. |
| **El sistema de cuentas de jugador es lo más pesado** | Es donde se va el grueso del trabajo y la superficie de bugs/seguridad. Hacerlo en su fase, con cuidado. |
| **Email (Gmail)**: límite/spam | OK para arrancar; plan B = proveedor transaccional. App Password, SPF razonable. |
| **Cambio grande sobre prod en uso** | Rama `feature/multi-tenant`, no `main`. Merge solo tras probar la migración y el aislamiento. |
| **Concurrencia / "último que guarda gana"** (ya existe) | Sigue pendiente; con más clubes conviene sumar optimistic locking eventualmente. |

---

## 8. Decisiones a confirmar al momento de codear (detalles finos)
- ¿`ClubPlayer` (tabla de "jugadores de mi club") sí o no? *(recomendado sí, para el listado del club sin depender de que tengan puntos)*.
- ¿El club puede **editar el perfil global** de un jugador (nombre, DNI) o solo el propio jugador/super-admin? *(recomendado: no editar identidad global desde el club; sí gestionar sus puntos/categorías)*.
- ¿Multi-usuario por club? (¿un club puede tener varios usuarios CLUB?) *(a futuro; arrancar con uno)*.

---

*Plan generado para PadelAdmin. Próximo paso sugerido: aprobar este plano y arrancar la **Fase 1** en una rama `feature/multi-tenant`.*
