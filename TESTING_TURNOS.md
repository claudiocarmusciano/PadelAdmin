# Guía de Pruebas Manuales: MVP Gestión de Turnos Online

**Fecha:** 2026-06-07  
**Rama:** `feature/court-booking`  
**Estado:** MVP completado - 10 commits

---

## 1. SETUP PARA PRUEBAS

### Backend
```bash
cd PadelAdmin/backend
./mvnw spring-boot:run
# Escucha en http://localhost:8080
```

**Hibernate creará automáticamente las tablas nuevas:**
- `court_bookings` (reservas de turnos)
- Columna nueva `slot_duration_minutes` en `courts`

### Frontend
```bash
cd PadelAdmin/frontend
npm run dev
# Escucha en http://localhost:5173
```

**Variables de entorno** (ya configuradas en `.env.development`):
```
VITE_API_BASE_URL=/api
```

---

## 2. ENDPOINTS A PROBAR

### 2.1 Públicos (sin autenticación)

#### GET `/api/public/complexes`
**Sin token. Retorna lista de complejos con canchas activas.**

```bash
curl -X GET http://localhost:8080/api/public/complexes
```

**Respuesta esperada:**
```json
[
  {
    "id": 1,
    "name": "Lagartos",
    "address": "...",
    "phone": "...",
    "courts": [
      {
        "id": 1,
        "name": "Cancha 1",
        "active": true,
        "slotDurationMinutes": 90
      }
    ]
  }
]
```

#### GET `/api/public/courts/{courtId}/slots?date=YYYY-MM-DD`
**Sin token. Retorna slots disponibles para una cancha en una fecha.**

```bash
curl -X GET "http://localhost:8080/api/public/courts/1/slots?date=2026-06-10"
```

**Respuesta esperada:**
```json
{
  "courtId": 1,
  "courtName": "Cancha 1",
  "date": "2026-06-10",
  "slots": [
    {
      "startTime": "09:00",
      "endTime": "10:30",
      "available": true,
      "reason": null
    },
    {
      "startTime": "10:30",
      "endTime": "12:00",
      "available": false,
      "reason": "Ocupado por reserva confirmada"
    }
  ]
}
```

#### POST `/api/public/bookings`
**Sin token. Crea reserva como invitado.**

```bash
curl -X POST http://localhost:8080/api/public/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "courtId": 1,
    "bookingDate": "2026-06-10",
    "startTime": "09:00",
    "customerName": "Juan Pérez",
    "customerPhone": "2284 55-1234",
    "notes": "Llamar 10 min antes"
  }'
```

**Respuesta esperada (201):**
```json
{
  "id": 1,
  "courtId": 1,
  "courtName": "Cancha 1",
  "complexName": "Lagartos",
  "bookingDate": "2026-06-10",
  "startTime": "09:00",
  "endTime": "10:30",
  "status": "CONFIRMED",
  "source": "PUBLIC",
  "customerName": "Juan Pérez",
  "customerPhone": "2284 55-1234",
  "notes": "Llamar 10 min antes",
  "createdAt": "2026-06-07T15:30:00"
}
```

### 2.2 Admin (requieren token + rol ADMIN)

#### Obtener token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "password": "password123"
  }'
```

Guarda el `token` de la respuesta.

#### GET `/api/bookings?complexId=1&date=2026-06-10`
**Con token ADMIN. Retorna reservas de un complejo en una fecha.**

```bash
curl -X GET "http://localhost:8080/api/bookings?complexId=1&date=2026-06-10" \
  -H "Authorization: Bearer <TOKEN>"
```

#### POST `/api/bookings`
**Con token ADMIN. Crea reserva manual (source=ADMIN).**

```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "courtId": 1,
    "bookingDate": "2026-06-10",
    "startTime": "12:00",
    "customerName": "Carlos López",
    "customerPhone": "2284 66-5678"
  }'
```

#### DELETE `/api/bookings/{id}`
**Con token ADMIN. Cancela (soft delete) una reserva.**

```bash
curl -X DELETE http://localhost:8080/api/bookings/1 \
  -H "Authorization: Bearer <TOKEN>"
```

---

## 3. ESCENARIOS DE PRUEBA

### 3.1 Reserva pública exitosa
1. Abre http://localhost:5173/reservas (sin login)
2. Selecciona complejo → Lagartos
3. Selecciona cancha → Cancha 1, fecha → hoy+3 días
4. Selecciona slot disponible (ej: 09:00)
5. Completa nombre + teléfono
6. Confirma → Debe mostrar éxito

**Validar:** Reserva creada en BD, status=CONFIRMED, source=PUBLIC

### 3.2 Intento de doble reserva (race condition)
1. Haz dos requests POST `/api/public/bookings` **simultáneamente** con mismo slot
2. Solo UNO debe ser exitoso (201)
3. El otro debe fallar con 400 o 409

```bash
# Terminal 1
curl -X POST http://localhost:8080/api/public/bookings -H "Content-Type: application/json" -d '{"courtId":1,"bookingDate":"2026-06-10","startTime":"09:00","customerName":"User1","customerPhone":"111"}'

# Terminal 2 (al mismo tiempo)
curl -X POST http://localhost:8080/api/public/bookings -H "Content-Type: application/json" -d '{"courtId":1,"bookingDate":"2026-06-10","startTime":"09:00","customerName":"User2","customerPhone":"222"}'
```

### 3.3 Conflicto turno ↔ partido de torneo
1. Crea un partido de torneo en Cancha 1 para hoy+3 días a las 15:00-16:30
2. Intenta reservar slot 15:00-16:30 en /api/public/bookings
3. Debe rechazarse con mensaje "Ocupado por partido de torneo"

### 3.4 Conflicto con pulmón horario
1. Asegúrate que Cancha 1 tenga pulmón (ej: 16:00-17:00)
2. Intenta reservar slot que solape pulmón (ej: 15:00-17:30)
3. Debe aparecer en slots como "no disponible" con razón "Ocupado por pulmón horario"

### 3.5 Gestión admin
1. Loguéate con rol ADMIN
2. Ve a http://localhost:5173/bookings
3. Selecciona complejo + fecha
4. Ver lista de reservas (públicas + admin)
5. Crear nueva reserva → formulario
6. Cancelar reserva → botón [X] → cambiar status a CANCELLED

---

## 4. CHECKLIST DE VALIDACIÓN

### Backend

- [ ] `GET /api/public/complexes` retorna datos (sin auth)
- [ ] `GET /api/public/courts/{id}/slots?date=...` retorna slots (sin auth)
- [ ] `POST /api/public/bookings` crea reserva (sin auth) → 201
- [ ] `POST /api/public/bookings` con slot ocupado → 400/409
- [ ] `POST /api/public/bookings` doble request simultáneo → solo 1 gana
- [ ] `GET /api/bookings?...` sin auth → 401/403
- [ ] `GET /api/bookings?...` con token ADMIN → 200, lista correcta
- [ ] `POST /api/bookings` con token ADMIN → 201, source=ADMIN
- [ ] `DELETE /api/bookings/{id}` con token ADMIN → 200, status=CANCELLED
- [ ] Slot con partido de torneo → no aparece como disponible
- [ ] Slot con pulmón → no aparece como disponible
- [ ] `slotDurationMinutes` personalizado por cancha (default 90)

### Frontend

- [ ] Página `/reservas` carga sin auth
- [ ] Flujo multi-step: complex → date+court → slots → confirm → success
- [ ] Slots se muestran con colores: verdes (disponibles), grises (ocupados) con razón
- [ ] Formulario valida nombre + teléfono obligatorios
- [ ] Mensaje de éxito y auto-redirect después de 3s
- [ ] Error handling: muestra mensaje si la reserva falla
- [ ] Página `/bookings` solo accessible con login
- [ ] Admin puede crear turno manual
- [ ] Admin puede cancelar turno (requiere confirmación)
- [ ] Filtros complejo + fecha funcionan correctamente
- [ ] Responsive en mobile

---

## 5. PUNTOS A REVISAR ANTES DE MERGEAR A MAIN

1. **Base de datos migró correctamente:**
   - Tabla `court_bookings` existe
   - Columna `slot_duration_minutes` en `courts`

2. **Seguridad:**
   - `/api/public/**` accesible sin token ✅
   - `/api/bookings` requiere token + ADMIN ✅
   - GET `/api/**` requiere token (para admin) ✅

3. **Anti-doble-reserva:**
   - Validación pre-insert ✅
   - Re-validación en transacción ✅
   - Índice único parcial en PostgreSQL (opcional pero recomendado)

4. **Integración con torneos:**
   - Partidos de torneo restan slots ✅
   - Pulmones respetados ✅
   - Nota: inversa (que fixture evite turnos) NO está en MVP ⚠️

---

## 6. NOTAS CONOCIDAS / LIMITACIONES MVP

1. **Sin cuentas de cliente:** Solo invitados + admin. Fase 2.
2. **Sin pagos:** MVP valida flujo. Pagos en Fase 3.
3. **Sin email de confirmación:** Puede agregarse en fase 2.
4. **Sin WhatsApp/SMS:** Puede agregarse en fase 2.
5. **Turnos NO evitan fixture:** Admin debe ser cuidadoso al programar partidos. Se planea en fase 2.

---

## 7. COMANDOS ÚTILES PARA DEBUGGING

### Ver reservas en DB
```bash
# Conectar a PostgreSQL
psql postgresql://postgres:postgres@localhost:5432/padeladmin

# Ver all bookings
SELECT * FROM court_bookings;

# Ver por fecha y cancha
SELECT * FROM court_bookings WHERE court_id=1 AND booking_date='2026-06-10' ORDER BY start_time;

# Ver soft deletes
SELECT * FROM court_bookings WHERE status='CANCELLED';
```

### Ver logs del backend
```bash
# Terminal donde está corriendo Spring Boot
# Buscar por "BookingService" o "PublicBookingController"
```

### Limpiar datos de prueba
```bash
# En psql
DELETE FROM court_bookings WHERE booking_date > CURRENT_DATE;
```

---

**Fin de Guía de Pruebas**  
Para preguntas o issues, referirse al plan en `/plans/crystalline-crafting-peach.md`
