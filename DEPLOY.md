# Deploy de PadelAdmin en Railway

App empaquetada como **una sola imagen Docker**: el frontend (React/Vite) se
buildea y queda servido por el backend (Spring Boot). PostgreSQL es un servicio
gestionado de Railway.

---

## 1. Crear el proyecto en Railway

1. Entrá a https://railway.app y logueate con GitHub.
2. **New Project → Deploy from GitHub repo →** elegí `PadelAdmin`.
3. Railway detecta el `Dockerfile` de la raíz y lo usa para buildear. No hace falta config extra de build.

## 2. Agregar PostgreSQL

1. En el proyecto: **New → Database → Add PostgreSQL**.
2. Railway crea un servicio `Postgres` con estas variables internas:
   `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`.

## 3. Variables de entorno del servicio backend

En el servicio de la app (no el de Postgres) → pestaña **Variables** → agregá:

| Variable | Valor | Notas |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | Referencia al servicio Postgres |
| `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` | |
| `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` | |
| `JWT_SECRET` | *(string aleatorio de 40+ caracteres)* | **Crítico.** Generá uno nuevo (ver abajo) |
| `ADMIN_EMAIL` | tu email de admin | Reemplaza el default |
| `ADMIN_PASSWORD` | *(contraseña fuerte)* | **Reemplaza `admin1234`** |
| `JWT_TTL_HOURS` | `8` | Opcional (default 8) |

> La sintaxis `${{Postgres.VARIABLE}}` es de Railway para referenciar variables
> de otro servicio. Ajustá el nombre `Postgres` si tu servicio se llama distinto.

Generar un `JWT_SECRET` seguro (en tu terminal):
```bash
openssl rand -base64 48
```

Railway inyecta `PORT` automáticamente — el backend ya lo lee (`server.port=${PORT:8080}`). **No** setees `PORT` a mano.

## 4. Exponer el dominio

1. En el servicio backend → **Settings → Networking → Generate Domain**.
2. Te da una URL `https://<algo>.up.railway.app` con HTTPS incluido.

## 5. Primer arranque

- Al arrancar, Hibernate (`ddl-auto=update`) crea todas las tablas en la base vacía.
- `AdminSeeder` crea el usuario admin con `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
- Entrá a la URL, logueate con esas credenciales y cambiá la contraseña si querés.

## 6. Deploys siguientes

Cada `git push` a `main` dispara un build + deploy automático. No hay que hacer nada más.

---

## Notas / pendientes

- **Backups:** Railway hace snapshots del Postgres, pero verificá la política del plan. Para datos del club conviene un backup manual periódico (`pg_dump`).
- **Datos iniciales:** los ~414 jugadores de tu base local NO se migran solos. Si los querés en producción, hay que exportarlos (`pg_dump` de las tablas) e importarlos, o recargarlos vía la app/script.
- **Costo:** plan Hobby de Railway ~USD 5/mes (la app + Postgres comparten el crédito). Verificá precios actuales.
- **ddl-auto=update:** sirve para el MVP. Para producción seria conviene migrar a Flyway/Liquibase más adelante.

---

## Probar la imagen localmente (opcional)

```bash
# build
docker build -t padeladmin:test .

# correr (necesita un Postgres accesible)
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://host.docker.internal:5432/padeladmin' \
  -e SPRING_DATASOURCE_USERNAME='postgres' \
  -e SPRING_DATASOURCE_PASSWORD='postgres' \
  -e JWT_SECRET='local-test-secret-de-al-menos-32-bytes-1234567890' \
  -e ADMIN_EMAIL='admin@padel.com' \
  -e ADMIN_PASSWORD='admin1234' \
  padeladmin:test

# luego abrir http://localhost:8080
```
