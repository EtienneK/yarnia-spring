# Yarnia — Game Design Doc

> Living document. Update it as decisions are made. Last updated: 2026-07-17.

## What this is

Yarnia is a web-based, mobile-friendly remake of **Y.A.R.N.**, a multiplayer word game
that originally ran on the **Mplayer** online gaming service in the late 1990s (Mplayer was
later acquired by GameSpy). This is a modern reimagining, not a straight port.

## The game, in one paragraph

Up to 8 players collaboratively write a silly story together over **10 rounds**. Each round,
every player submits a short continuation of the story (a word / phrase / sentence). Once all
submissions are in, everyone **votes** for their favourite continuation. Each vote earns the
author a point; the round winner earns bonus points. **Only the winning submission** becomes the
"canonical" next line of the shared story — the losing entries are discarded, so the story is
authored collectively through voting. The **final round** is a double-points bonus round where
players submit the **"moral of the story"**. After 10 rounds, the player with the most points wins.

## Core rules (as specified by the owner)

- **Players:** minimum **3**, maximum **8**.
- **AI players:** the **host** may add up to **2 AI agent** players. This lets a game run even
  with a single human (1 human + 2 bots = 3 = the minimum). AI players submit continuations and
  vote like humans.
- **Rounds:** **10** total.
  - **Round 1** seeds from a random opening line (e.g. "Long, long ago…"), and players add to it.
  - **Rounds 2–9** continue the story.
  - **Round 10** is a **double-points bonus round**: submit the **"moral of the story"**.
- **Scoring:** each vote your submission receives = **1 point**. Round winner gets a **bonus**
  (amount TBD). Final round scores are **doubled**.
- **Winner:** most points after round 10.
- **Platform:** web, must work well on **mobile**.

## Resolved design decisions

Decided with the owner on 2026-07-17:

1. **Story canon — winner only.** Each round, only the highest-voted submission is appended to
   the shared story. Losing submissions are discarded (they still scored their authors points).
2. **Timed phases with early advance** (early advance added 2026-07-17 at the owner's request —
   "waiting around is not fun"). Submission and voting each run on a countdown timer; when the
   timer expires the round advances regardless of who hasn't acted. Additionally, once every
   player who *can* still act has acted (or disconnected), the deadline collapses to a short
   grace period (`early-advance-delay-seconds`, default 2s) so nobody waits out a dead timer.
3. **Voting is anonymous, no self-vote.** During voting, submissions are shown without author
   names, and a player cannot vote for their own submission. This rewards the writing, not
   popularity. Authors are revealed after the vote tally.
4. **AI provider is abstracted, not hardcoded.** Bot gameplay talks to a generic chat interface
   so the underlying LLM provider is swappable. Use **Spring AI** (`ChatModel` / `ChatClient`),
   which offers a provider-agnostic API with pluggable starters (Anthropic, OpenAI, Ollama, …).
   The concrete provider is chosen via config, not code.

## Current implementation status

### Done — Lobby / party layer

**Backend** (Spring Boot 4, Java 25, H2 + JPA, STOMP-over-SockJS WebSockets):
- `POST /api/party/create` — creates a party, generates a unique 6-char join code, makes the
  creator the host, returns `{partyId, playerId, joinCode, joinToken}`.
- `POST /api/party/join` — join by code, returns credentials.
- WebSocket lobby via `PartyWsController` (`/app/party/{partyId}/…`), broadcasting party
  snapshots to `/topic/party/{partyId}/snapshot`. Messages: `user-snapshot`, `setName`,
  `setReady`, `startGame`, `addBot`.
- Auth: join tokens persisted per player; verified on CONNECT/SUBSCRIBE via
  `WebSocketConfig` interceptor + handshake handler (cookies).
- Party members carry: `name`, `color`, `isHost`, `isReady`, `connected`, `isBot`, `botPersona`.
- Party phase enum: `WAITING → PLAYING → FINISHED`.
- Bot support: `addBot` creates a bot member (max 2). `botPersona` currently hardcoded to
  "You are a witty British man." (`PartyService.addBot`, marked TODO).
- Lifecycle: min 3 to start, all players must be ready, host-only start. Disconnect handling,
  host reassignment, party cleanup when last human leaves.

**Frontend** (React 19, Vite, TypeScript, Tailwind v4, daisyUI):
- Flow: Home → Menu (Host / Join by code) → Game (lobby).
- Lobby UI: join code display, editable name, live player list with ready/host/bot/disconnected
  badges, ready toggle, "Add Bot", host "Start Game".
- STOMP client via `@stomp/rx-stomp`, credentials in cookies, snapshot subscription.

### Done — Game engine (2026-07-17)

The full game loop is implemented and verified end-to-end (scripted 3-player game:
1 human client + 2 bots, all rounds, scoring, moral round, winner).

**Backend** (`com.etiennek.yarnia.game`):
- `GameState` / `StorySegment` / `RoundSubmission` / `RoundVote` entities + repos.
- `GameService` — the state machine: SUBMITTING → VOTING → REVEAL → (next round | FINISHED).
  Starts via `GameStartedEvent` from the party layer; cleans up via `PartyDeletedEvent`.
- `GameTimer` — schedules phase-deadline callbacks; stale timers are no-ops.
- `GameProperties` (`yarnia.game.*`) — all rules tunable via config (rounds, timers, bonuses,
  max submission length). Tests/dev can shrink timers.
- `GameWsController` — `/app/party/{id}/game/{submit|vote|snapshot|playAgain}`; snapshots
  broadcast to `/topic/party/{id}/game`. Authors/vote counts hidden until REVEAL.
- **Bots**: `BotCoordinator` schedules bot moves with random "thinking" delays;
  `BotBrain` interface with `LlmBotBrain` (Spring AI `ChatModel`, provider-agnostic —
  currently the DeepSeek starter with `deepseek-chat`, key via `DEEPSEEK_API_KEY`) falling
  back to `CannedBotBrain` (canned funny lines) when no API key is configured or an LLM call
  fails. Swap providers by changing the starter in `pom.xml` + the `spring.ai.*` block.
  Bot personas randomised from a pool (`Utils.generateBotPersona`).
- "Play Again": host can reset a finished game back to the lobby (scores wiped, bots kept).

**Frontend** (`src/main/ui/src/game/`):
- `Game.tsx` — connection + subscriptions (party & game topics), routes to Lobby or GamePlay.
- `Lobby.tsx` — the extracted pre-game lobby.
- `GamePlay.tsx` — per-phase UI: story-so-far card, submit textarea with countdown +
  live "n/m submitted", anonymous voting cards (own entry disabled), reveal with authors/votes/
  crown/round points, running scoreboard, final results screen with full story + Play Again.
- Countdown bars driven by server `phaseEndsAt` (epoch ms).
- Audio (`audio.ts`, all Web Audio synthesis — no assets): chiptune lobby loop (116 BPM,
  A minor), a quieter mellow in-game loop (92 BPM, triangle lead), and an upbeat victory
  theme on the results screen (132 BPM, C major, four-on-the-floor, opens with a rising
  fanfare). SFX stingers — player-join blip, submit/vote confirmations, phase chimes,
  countdown ticks (last 5s), reveal arpeggio. One global 🔊 toggle (lobby + in-game
  headers), preference in localStorage under "sound".
- Animations (`index.css` keyframes): phase-transition fade, staggered card pop-ins
  (voting/reveal), winner-card glow, newest story line + "+points" rise-in, countdown
  pulse in the final 5s, trophy bounce and CSS confetti on the results screen.
  All disabled under `prefers-reduced-motion`.
- **Party chat (2026-07-18)** (`com.etiennek.yarnia.chat` + `Chat.tsx`): persisted per-party
  chat (last 100 messages, SQLite, deleted with the party) over WS route
  `/app/party/{id}/chat` broadcast to `/topic/party/{id}/chat`; history via a per-player
  reply topic. UI is a floating 💬 button (zero layout space) with an unread badge and a
  slide-in drawer, available in lobby and in game; chat blip SFX. **Bots participate**:
  `BotChatService` reacts to human messages (35% chance, 90% when addressed by name),
  round reveals (35%) and game end (80%) — the LLM gets story + recent chat + event
  context and may reply "PASS" to stay silent; 25s per-bot cooldown, 2-7s thinking delay,
  bots never reply to bots. Replies ≤200 chars (inside the 300 max-token output cap).

Also fixed pre-existing party-layer bugs: join-token mass deletion on member leave, member
overwrite on reconnect (now preserves name/host/ready/score), mid-game join rejection
(`in_progress`), host reassignment on disconnect, bot-only party cleanup, and Lombok not
running in Maven builds (invalid `annotationProcessor` scope).

## Architecture notes

- Backend port **3001**; UI dev server port **3000** (proxies `/api` and `/ws` to 3001).
- Persistence is **H2** (currently in-memory by default — game state does not survive restart).
- Real-time is **STOMP over SockJS**; the server broadcasts full party snapshots on change.
  The game layer will likely follow the same "broadcast a snapshot on every state change" model.
- `Websocket.tsx` is a leftover scaffolding/demo file, not part of the app flow.

## Implemented defaults (owner can veto/tune — all in `yarnia.game.*` config)

These were open questions; sensible defaults were chosen and implemented:

1. **Tie-breaking:** most votes wins; ties go to the **earliest submission** (editing a
   submission resets its timestamp). Deterministic, rewards speed.
2. **Bonus amounts:** round winner gets **+2** bonus points. Round 10 doubles **everything**
   (each vote worth 2, winner bonus worth 4). `winner-bonus`, `final-round-multiplier`.
3. **Timers:** submit **60s**, vote **30s**, reveal **10s** (`submit-seconds`, `vote-seconds`,
   `reveal-seconds`), plus **early advance**: when everyone eligible has submitted/voted, the
   phase ends after a 2s grace period (`early-advance-delay-seconds`). Eligibility ignores
   disconnected humans, and during voting ignores players with nothing to vote for (e.g. the
   author of the only submission, who can't self-vote). A mid-phase disconnect also re-triggers
   the check so a dropped player can't stall the round.
4. **No-shows:** a player who doesn't submit simply has no entry that round (they may still
   vote); a player who doesn't vote casts no vote. No penalties. If *nobody* submits, the
   round is skipped (no canon line) and play continues.
5. **Submissions:** max **120 chars** (`max-submission-length`), whitespace-normalised,
   editable until the deadline. Voting is anonymous; self-votes rejected server-side.
6. **Bot personas (revised 2026-07-17):** randomised per bot from a pool of 10 **realistic
   human personas** (a plumber who loves dad jokes, a sarcastic nurse, a deadpan developer, …)
   — the owner wants bots to imitate real people, not cartoon characters. The LLM system
   prompt additionally instructs bots to type like a human (occasional small typo / loose
   grammar, never constant mistakes) and to use only normal-keyboard characters (no em/en
   dashes, curly quotes, emoji — LLM giveaways). `LlmBotBrain.clean()` also scrubs
   typographic punctuation from bot output as a hard guarantee.
7. **Moral line:** the winning round-10 submission is appended to the story flagged as the
   moral, rendered as "The moral of the story: …".
8. **Player colors (2026-07-17):** colors are assigned **uniquely per party** — first free slot
   from an 8-slot palette in `Utils.PLAYER_COLOR_PALETTE` (a leaver in the lobby frees their
   slot; reconnects keep theirs). Each slot is a CSS `light-dark(lightHex, darkHex)` pair so
   the same member `color` string renders legibly on both daisyUI light and dark themes
   (all ≥4:1 contrast on white and on `#1d232a`; hue steps validated with the dataviz palette
   validator — every pair distinguishable to normal vision in both modes). Slot order is
   deliberate (most-distinct hues first: blue, green, magenta, orange, cyan, gold, violet,
   red) — don't reorder casually. Full CVD safety isn't achievable for 8 text-legible colors;
   the mitigation is that player names are always shown as text, so color never carries
   identity alone. Requires a `light-dark()`-capable browser (2024+); older browsers fall back
   to the light color.

## Open questions (remaining)

1. ~~**Persistence**~~ **Resolved 2026-07-17:** SQLite (WAL, single-connection pool standing in
   for `SELECT FOR UPDATE`, which SQLite lacks) at `./data/yarnia.db` / `YARNIA_DB_PATH`
   (Docker volume `/data`). Games survive restarts; `GameService` re-arms phase timers on
   startup with a short grace for overdue deadlines.
2. **Content filtering:** no profanity filtering on submissions/names yet.
3. **LLM key management:** bots use canned lines unless `DEEPSEEK_API_KEY` is set (or another
   Spring AI provider starter is swapped in via `pom.xml` + `spring.ai.*` config).
