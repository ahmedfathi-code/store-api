# Store API

[![CI](https://github.com/ahmedfathi-code/store-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ahmedfathi-code/store-api/actions/workflows/ci.yml)

A Spring Boot REST API for a product catalog. It has JWT authentication with refresh-token rotation and real logout, ADMIN/USER roles, and paginated, sortable product listing. It runs on PostgreSQL (schema managed by Flyway) and Redis, and starts with a single `docker compose up`.

## Problem

A store backend needs more than CRUD:

- **Only staff may change the catalog.** Anyone can browse; only admins create, update or delete products.
- **Catalogs are large.** Clients need pages, a page size and a sort order, and must not be able to pull the whole table in one request.
- **Logout has to mean logout.** A stateless JWT stays valid until it expires. If a token leaks, or a user logs out, the server needs a way to reject it early.
- **The schema has to be reproducible.** Every environment, from a laptop to CI to production, should build the same database from versioned migrations, not from whatever an ORM guessed.
- **It has to be easy to run.** A reviewer should be able to start the whole stack with one command.

This project covers all five.

## Architecture

```mermaid
flowchart LR
    client["Client"]

    subgraph app["Spring Boot app (container: app)"]
        direction TB
        filter["Security filter chain<br/>JwtAuthFilter: verify JWT, check blacklist<br/>URL rules: public / authenticated / ADMIN"]
        controllers["Controllers<br/>AuthController · ProductController"]
        services["Services<br/>ProductService · RefreshTokenService · TokenBlacklistService"]
        repos["Spring Data JPA repositories"]
        errors["GlobalExceptionHandler<br/>uniform JSON errors"]
        filter --> controllers --> services --> repos
        controllers -.-> errors
    end

    pg[("PostgreSQL 16<br/>products, users<br/>schema via Flyway")]
    redis[("Redis 7<br/>blacklist:sha256(access token)<br/>refresh:sha256(refresh token)")]

    client -- "HTTP + Bearer JWT" --> filter
    repos --> pg
    filter -- "is token revoked?" --> redis
    services -- "issue / consume / revoke" --> redis
```

**Token lifecycle:**

```mermaid
sequenceDiagram
    participant C as Client
    participant A as API
    participant R as Redis

    C->>A: POST /api/auth/login (email, password)
    A->>R: SET refresh:hash(RT) = email, TTL 7d
    A-->>C: access token (JWT, 15 min) + refresh token (RT)

    C->>A: POST /api/auth/refresh (RT)
    A->>R: GETDEL refresh:hash(RT) (single use)
    A->>R: SET refresh:hash(RT2), TTL 7d
    A-->>C: new access token + RT2

    C->>A: POST /api/auth/logout (Bearer access token, RT2)
    A->>R: SET blacklist:hash(access token), TTL = time left on the token
    A->>R: DEL refresh:hash(RT2)
    A-->>C: 204

    C->>A: any request with the old access token
    A->>R: EXISTS blacklist:hash(token) → yes
    A-->>C: rejected
```

## Tech stack

Java 21 · Spring Boot 4 (Web MVC, Security, Data JPA, Data Redis, Validation, Actuator) · JJWT · PostgreSQL 16 · Flyway · Redis 7 · JUnit 5, Mockito, Testcontainers · Docker, Docker Compose · GitHub Actions

## How to run

### With Docker (recommended)

You only need Docker.

```bash
cp .env.example .env
# edit .env: set DB_USERNAME, DB_PASSWORD and JWT_SECRET (at least 32 characters)
docker compose up --build
```

- API: `http://localhost:8080` (change the host port with `APP_PORT`)
- Health: `http://localhost:8080/actuator/health`

Compose starts PostgreSQL and Redis first, waits until both are healthy, then starts the app. Flyway creates the schema on first start. Data is kept in the `postgres-data` volume; `docker compose down -v` deletes it. If a required variable is missing, compose stops immediately and names it.

### Without Docker

Requires Java 21 and a running PostgreSQL and Redis. Set the variables below (see `.env.example`), then:

```bash
./mvnw spring-boot:run
```

### Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | yes | none | PostgreSQL credentials |
| `JWT_SECRET` | yes | none | HMAC key for signing JWTs, at least 32 characters |
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/product_store_db` | JDBC URL (compose sets this itself) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | no | `localhost`, `6379`, empty | Redis connection (compose sets this itself) |
| `JWT_ACCESS_TOKEN_EXPIRATION` | no | `15m` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_EXPIRATION` | no | `7d` | Refresh-token lifetime |
| `APP_PORT` | no | `8080` | Host port (docker compose only) |

### Creating an admin

`/api/auth/register` always creates a `ROLE_USER` account. To make a user an admin, update the row directly:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update users set role = '"'"'ROLE_ADMIN'"'"' where email = '"'"'admin@example.com'"'"';"'
```

Then log in again to get a token for the admin account.

### Running the tests

```bash
./mvnw verify            # unit + integration tests (Docker must be running)
./mvnw verify -DskipITs  # unit tests only, no Docker needed
```

## API

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | public | Create a USER account |
| `POST` | `/api/auth/login` | public | Get an access token and a refresh token |
| `POST` | `/api/auth/refresh` | public (needs a refresh token) | Exchange a refresh token for a new pair |
| `POST` | `/api/auth/logout` | authenticated | Revoke the access token, and the refresh token if sent |
| `GET` | `/api/products` | public | Paginated, sortable list |
| `GET` | `/api/products/{id}` | public | One product |
| `GET` | `/api/products/search/name?name=` | public | Paginated, case-insensitive "contains" search |
| `GET` | `/api/products/search/category?category=` | public | Paginated, case-insensitive exact category match |
| `POST` | `/api/products` | **ADMIN** | Create a product |
| `PUT` | `/api/products/{id}` | **ADMIN** | Replace a product |
| `DELETE` | `/api/products/{id}` | **ADMIN** | Delete a product |
| `GET` | `/actuator/health` | public | `{"status":"UP"}`, or `DOWN` if the database or Redis is unreachable |

### Pagination

`GET /api/products` accepts:

| Param | Default | Rules |
|---|---|---|
| `page` | `0` | 0 or greater |
| `size` | `10` | 1 to 100 |
| `sortBy` | `id` | a product field: `id`, `name`, `price`, `category`, `stock` |
| `direction` | `asc` | `asc` or `desc`, case-insensitive |

The search endpoints accept `page` and `size` with the same rules. Any invalid value returns `400` with a message.

## Request examples

These are real responses from the Docker stack. Tokens are shortened, and the page response is pretty-printed with its `pageable` and `sort` metadata collapsed.

**Register and log in**

```bash
curl -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Passw0rd!"}'
# 201 {"message":"تم التسجيل بنجاح"}   ("registered successfully")

curl -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Passw0rd!"}'
# 200 {"token":"eyJhbGciOiJI...","refreshToken":"blFSeywM...","expiresIn":900}
```

**Create a product (ADMIN)**

```bash
curl -X POST localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Desk Lamp","price":24.0,"category":"office","stock":15}'
# 201 {"id":4,"name":"Desk Lamp","price":24.0,"category":"office","stock":15}
```

The same request with a USER's token returns `403`.

**List: page 0, 2 per page, most expensive first**

```bash
curl 'localhost:8080/api/products?page=0&size=2&sortBy=price&direction=desc'
```

```json
{
  "content": [
    {"id": 4, "name": "Desk Lamp", "price": 24.0, "category": "office"},
    {"id": 3, "name": "Coffee Mug", "price": 7.9, "category": "kitchen"}
  ],
  "number": 0,
  "size": 2,
  "numberOfElements": 2,
  "totalElements": 4,
  "totalPages": 2,
  "first": true,
  "last": false,
  "empty": false,
  "pageable": { "...": "..." },
  "sort": { "...": "..." }
}
```

**Search by category**

```bash
curl 'localhost:8080/api/products/search/category?category=office&size=2'
# 200 {"content":[{"id":1,"name":"Notebook",...},{"id":2,"name":"Pen",...}],"totalElements":3,...}
```

**Refresh, then log out**

```bash
curl -X POST localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
# 200 {"token":"eyJhbGciOiJI...","refreshToken":"fmsdNZLG...","expiresIn":900}

# the same refresh token a second time (they are single use)
# 401 {"message":"Invalid or expired refresh token"}

curl -X POST localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $NEW_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$NEW_REFRESH\"}"
# 204, after which $NEW_TOKEN is rejected (403) and $NEW_REFRESH returns 401
```

**Errors**

Errors share one JSON shape:

```bash
curl 'localhost:8080/api/products?size=500'
# 400 {"status":400,"message":"size must be at most 100","timestamp":"2026-09-28T00:14:22.763470682"}

curl localhost:8080/api/products/999
# 404 {"status":404,"message":"Product not found with id: 999","timestamp":"2026-09-28T00:14:22.809429402"}
```

## Design decisions

**Flyway owns the schema; Hibernate only validates it.**
`V1__init.sql` was generated from Hibernate's own DDL, not written by hand, so existing databases match it exactly. With `ddl-auto=validate` the app refuses to start if the entities and the schema drift apart.
*Trade-off:* every entity change needs a migration.

**Short access tokens, rotating opaque refresh tokens.**
Access tokens are JWTs that live 15 minutes. Refresh tokens are random 256-bit strings, not JWTs, stored in Redis for 7 days. Each refresh token works once: `GETDEL` makes consuming it atomic.
*Why:* a refresh token has to be revocable, which means server-side state anyway, so a signed JWT would add nothing.
*Trade-off:* clients must refresh every 15 minutes.

**Logout blacklists the access token until it would expire anyway.**
The Redis key's TTL is the token's remaining lifetime, so the blacklist cleans itself up and never grows beyond the currently valid tokens. Checking it costs one `EXISTS` per authenticated request.

**Only hashes go into Redis.**
Keys are `sha256(token)`, so a Redis dump can't be replayed as credentials.

**Fail closed when Redis is down.**
Requests carrying a token, plus login and refresh, return `503` within 2 seconds rather than skipping the blacklist check. Skipping it would silently make logged-out tokens valid again. Anonymous reads keep working.

**Role rules live in one place.**
All access rules are URL rules in `SecurityConfig`, not scattered `@PreAuthorize` annotations. Writes to `/api/products/**` need `ROLE_ADMIN`.

**Existing responses kept stable.**
Revoked and invalid tokens both get `403`, which is Spring Security's default and what clients already received. Switching unauthenticated responses to `401` would have changed existing behaviour. Contract changes were made only when a milestone required them, and each one was listed in its PR.

**Input errors are `400`, not `500`.**
Bad paging values, unknown sort fields, non-numeric ids and malformed JSON return `400`. The catch-all exception handler passes through Spring MVC's own status codes (`404`, `405`, `415`), and genuinely unexpected errors are logged and returned as `500`.

**Testcontainers instead of an in-memory database.**
Integration tests run against real PostgreSQL and Redis, so they exercise the real Flyway migration, `ddl-auto=validate` and real token revocation. H2 compatibility mode can't prove any of that.
*Trade-off:* `./mvnw verify` needs Docker.

**Layered, non-root Docker image.**
The multi-stage build caches dependencies in their own layer and splits the jar into Spring Boot layers, so a code change rebuilds only the small application layer. The container runs as an unprivileged user, with the heap sized from the container's memory limit.

**Health only, without details.**
Actuator exposes just `/actuator/health`, publicly and without component details, so compose can wait for a truly ready app without revealing anything about the database or Redis.

## Testing

| Suite | Tests | What it covers |
|---|---|---|
| Unit (Mockito) | 25 | `ProductService` (sort and page building, DTO mapping, not-found paths), `UserDetailsServiceImpl` (roles to authorities), `RefreshTokenService` and `TokenBlacklistService` (hashing, TTLs, single-use consumption) |
| `ProductPaginationIT` | 19 | defaults, page and size, totals, sorting by price and name in both directions, sorting across pages, search paging, size limit, `400`s and `404` |
| `ProductSecurityIT` | 13 | USER gets `403` on writes and nothing changes; ADMIN gets `201`/`200`/`204`; anonymous and invalid tokens; public reads |
| `AuthTokensIT` | 9 | register, login, refresh rotation, reuse rejected, logout revokes both tokens, blacklist TTL, only hashes stored |
| `ErrorHandlingIT` | 5 | `404`, `405`, `415`, malformed JSON gives `400` |
| `HealthEndpointIT` | 3 | public `UP` without details; other Actuator endpoints not exposed |
| `ProductStoreApplicationIT` | 1 | context starts on a fresh database and Flyway applied V1 |

CI runs `./mvnw verify` on every push and pull request to `main`. A parallel job validates `docker-compose.yml` and builds the image.

## Project structure

```
src/main/java/com/springtest/product_store/
├── controller/   AuthController, ProductController
├── dto/          request/response DTOs, ErrorResponse
├── entity/       Product, User (JPA)
├── exception/    GlobalExceptionHandler, custom exceptions
├── model/        Role
├── repository/   Spring Data JPA repositories
├── security/     SecurityConfig, JwtAuthFilter, JwtUtil,
│                 RefreshTokenService, TokenBlacklistService, TokenHasher
└── service/      ProductService
src/main/resources/db/migration/   Flyway migrations
src/test/java/...                   *Test (unit), *IT (Testcontainers)
Dockerfile · docker-compose.yml · .github/workflows/ci.yml
```

## Known limitations and next steps

- **No API docs yet.** OpenAPI/Swagger (springdoc) is the next planned addition.
- **No admin seeding.** Admins are promoted with SQL (see above). A startup seed from environment variables would make first-run setup easier.
- **`403` instead of `401`** for missing, invalid and revoked tokens. It is Spring Security's default and was kept for compatibility; a custom entry point would make it `401`.
- **Mixed message languages.** The original messages are Arabic (for example `"تم التسجيل بنجاح"`, "registered successfully"); messages added later are English.
- **Search endpoints return the entity** (including `stock`) while the list endpoint returns the DTO.
- **`spring.jpa.show-sql=true`** logs every SQL statement; a production profile should turn it off.
