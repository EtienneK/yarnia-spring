# Test scripts

Headless end-to-end tests that drive the real server over REST + STOMP websockets.
They need the UI's node_modules (`cd src/main/ui && pnpm install`) and Node 18+.

## Start a test server first

Run against a throwaway server on a spare port with fast timers, a fresh database,
and the LLM disabled (so bots use canned lines and no API credits are spent):

```sh
SERVER_PORT=3002 \
SPRING_JPA_SHOW_SQL=false \
SPRING_AI_DEEPSEEK_API_KEY=unset \
YARNIA_DB_PATH=/tmp/yarnia-test.db \
YARNIA_GAME_TOTALROUNDS=3 \
YARNIA_GAME_SUBMITSECONDS=15 \
YARNIA_GAME_VOTESECONDS=8 \
YARNIA_GAME_REVEALSECONDS=2 \
YARNIA_GAME_EARLYADVANCEDELAYSECONDS=1 \
./mvnw spring-boot:run
```

Delete `/tmp/yarnia-test.db*` between runs that assume a fresh database.

## Scripts

All scripts read `YARNIA_PORT` (default 3001) and exit non-zero on failure.

| Script | What it verifies | Notes |
|---|---|---|
| `e2e-game.mjs` | A complete game: lobby, 2 bots, ready-up, all rounds of submit/vote/reveal, moral round, scoring, winner. Also asserts unique `light-dark()` player colors and chat echo/history. | `YARNIA_PORT=3002 node scripts/e2e-game.mjs` |
| `e2e-public.mjs` | Public matchmaking: 404 with no/only-private games, match + join code, snapshot `publicGame` flag, fullest-lobby preference. | **Needs a fresh DB.** |
| `stress.mjs` | N concurrent games (default 100): completion rate, websocket errors, create latency, game-duration tail. | `GAMES=100 YARNIA_PORT=3002 node scripts/stress.mjs` — keep `SPRING_AI_DEEPSEEK_API_KEY=unset` on the server or bots will make real LLM calls. |

Baseline (2026-07-18, home server): 100/100 games, 0 errors, create p50 13ms / p95 45ms.
