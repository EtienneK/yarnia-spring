# Yarnia

A web-based, mobile-friendly remake of **Y.A.R.N.**, the 1990s multiplayer word game from the
Mplayer service. 3-8 players collaboratively write a silly story over 10 rounds: every round each
player submits a continuation, everyone votes, the winning line becomes part of the story, and
the final round is a double-points "moral of the story". The host can add up to 2 AI players, so
you can play with a single human.

See [docs/GAME_DESIGN.md](docs/GAME_DESIGN.md) for the full design and rules.

## Run with Docker Compose (recommended)

Requirements: Docker with the compose plugin.

```sh
# 1. (Optional) give the AI players a brain. Without a key the game still
#    works - bots just use canned one-liners instead of an LLM.
echo "DEEPSEEK_API_KEY=sk-your-key-here" > .env

# 2. Build and start
docker compose up --build -d

# 3. Play
#    open http://localhost:3001 in a browser (or on your phone via your
#    machine's LAN address, e.g. http://192.168.1.10:3001)
```

Useful commands:

```sh
docker compose logs -f     # watch the server logs
docker compose down        # stop
docker compose up --build  # rebuild after pulling changes
```

The container serves the frontend, the REST API and the WebSocket endpoint on a single port
(3001), so no reverse proxy is needed for LAN play.

### Configuration

All settings are environment variables (see the commented examples in
[docker-compose.yml](docker-compose.yml)):

| Variable | Default | Purpose |
|---|---|---|
| `DEEPSEEK_API_KEY` | *(empty)* | API key for the AI players' LLM. Empty = canned-line bots. |
| `YARNIA_GAME_TOTALROUNDS` | `10` | Rounds per game. |
| `YARNIA_GAME_SUBMITSECONDS` | `60` | Submission phase timer. |
| `YARNIA_GAME_VOTESECONDS` | `30` | Voting phase timer. |
| `YARNIA_GAME_REVEALSECONDS` | `10` | Results display time between rounds. |
| `YARNIA_GAME_EARLYADVANCEDELAYSECONDS` | `2` | Grace period once everyone has acted. |
| `YARNIA_ADMIN_PASSWORD` | *(unset)* | Enables the read-only admin dashboard at `/admin` (HTTP Basic auth, any username). Unset = dashboard disabled (404). |
| `YARNIA_ALLOWEDORIGINS` | dev origins | Extra allowed WebSocket origins (comma separated). Same-origin is always allowed. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | *(unset)* | Set to `framework` when running behind a reverse proxy (TLS termination). |

The LLM provider is pluggable via Spring AI - to use a different provider, swap
`spring-ai-starter-model-deepseek` in [pom.xml](pom.xml) and the `spring.ai.*` block in
[application.yml](src/main/resources/application.yml).

### Good to know

- **Persistence**: game state lives in a SQLite database on the `yarnia-data` volume, so
  parties and running games survive container restarts and rebuilds — in-flight games resume
  (phase timers are re-armed on startup and clients reconnect automatically). Delete the
  volume (`docker compose down -v`) for a clean slate. Locally the database is `./data/yarnia.db`
  (gitignored); the path is overridable via `YARNIA_DB_PATH`.
- **Bind mounts**: if you replace the named volume with a host path (e.g.
  `./my-data:/data`), the app will fail with `SQLITE_CANTOPEN` unless the directory is
  writable by the container user (uid **1001**): `sudo chown -R 1001:1001 ./my-data`.
  Alternatively set `user: "<your-uid>:<your-gid>"` on the service and create the
  directory yourself. Named volumes don't need this — Docker initializes them from the
  image with the right ownership.
- Behind a reverse proxy with HTTPS, set `SERVER_FORWARD_HEADERS_STRATEGY=framework` and add
  your public origin to `YARNIA_ALLOWEDORIGINS`.

### Prebuilt images

Every push to `main` builds and publishes `ghcr.io/etiennek/yarnia-spring:latest` via
[GitHub Actions](.github/workflows/docker.yml) (plus `sha-<commit>` tags, and semver tags for
`v*` releases). To run the published image instead of building locally, replace `build: .` in
docker-compose.yml with `image: ghcr.io/etiennek/yarnia-spring:latest`.

## Local development

Backend (Java 25, port 3001):

```sh
./mvnw spring-boot:run
```

Frontend dev server with hot reload (port 3000, proxies `/api` and `/ws` to 3001):

```sh
cd src/main/ui
pnpm install
pnpm dev
```

Open http://localhost:3000. For the AI players, put your key in a gitignored
`application-local.yml` in the repo root:

```yaml
spring:
  ai:
    deepseek:
      api-key: sk-your-key-here
```

Fast test games: shrink the rules via env vars, e.g.

```sh
YARNIA_GAME_TOTALROUNDS=2 YARNIA_GAME_SUBMITSECONDS=10 ./mvnw spring-boot:run
```

## Tests

Headless end-to-end and stress tests live in [scripts/](scripts/) — they drive the real
server over REST + websockets (full games with bots, public matchmaking, 100 concurrent
games). See [scripts/README.md](scripts/README.md) for how to run them.
