// Stress test: N concurrent games (1 scripted human + 2 canned bots each).
import { createRequire } from "module";
const require = createRequire(new URL("../src/main/ui/package.json", import.meta.url));
const SockJS = require("sockjs-client");
const { Client } = require("@stomp/stompjs");

const BASE = `http://localhost:${process.env.YARNIA_PORT ?? "3002"}`;
const N = parseInt(process.env.GAMES ?? "100", 10);
const RAMP_MS = 100; // one new game every 100ms
const GAME_TIMEOUT_MS = 240_000;

const stats = {
  started: 0,
  finished: 0,
  failed: 0,
  stompErrors: 0,
  createLatencies: [],
  gameDurations: [],
  errors: [],
};

function pct(arr, p) {
  if (!arr.length) return 0;
  const s = [...arr].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.floor((p / 100) * s.length))];
}

async function runGame(idx) {
  const t0 = Date.now();
  try {
    const tCreate = Date.now();
    const res = await fetch(`${BASE}/api/party/create`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ publicGame: idx % 2 === 0 }),
    });
    stats.createLatencies.push(Date.now() - tCreate);
    if (!res.ok) throw new Error(`create ${res.status}`);
    const { partyId, playerId, joinToken } = await res.json();
    stats.started++;

    await new Promise((resolve, reject) => {
      let submittedRound = -1;
      let votedRound = -1;
      let botsAdded = 0;
      let readied = false;
      let started = false;
      const timeout = setTimeout(() => reject(new Error("game timeout")), GAME_TIMEOUT_MS);

      const client = new Client({
        webSocketFactory: () => new SockJS(`${BASE}/ws`),
        connectHeaders: { partyId, playerId, joinToken, playerName: `Load${idx}` },
        reconnectDelay: 3000,
        onStompError: (f) => {
          stats.stompErrors++;
          reject(new Error("stomp: " + f.headers["message"]));
        },
        onConnect: () => {
          client.subscribe(`/topic/party/${partyId}/snapshot`, (m) => {
            const snap = JSON.parse(m.body);
            const members = Object.values(snap.members);
            if (snap.partyPhase === "WAITING") {
              const bots = members.filter((v) => v.bot).length;
              if (bots < 2 && botsAdded < 4) {
                botsAdded++;
                client.publish({ destination: `/app/party/${partyId}/addBot`, body: "true" });
              } else if (!readied && bots >= 2) {
                readied = true;
                client.publish({ destination: `/app/party/${partyId}/setReady`, body: "true" });
              } else if (readied && !started && members.every((v) => v.ready)) {
                started = true;
                client.publish({ destination: `/app/party/${partyId}/startGame`, body: "true" });
              }
            }
          });
          client.subscribe(`/topic/party/${partyId}/game`, (m) => {
            const g = JSON.parse(m.body);
            if (g.phase === "SUBMITTING" && submittedRound !== g.round) {
              submittedRound = g.round;
              client.publish({
                destination: `/app/party/${partyId}/game/submit`,
                body: `load line r${g.round} from game ${idx}`,
              });
            } else if (g.phase === "VOTING" && votedRound !== g.round && g.submissions) {
              const target = g.submissions.find(
                (s) => !s.text.startsWith(`load line r${g.round} from game ${idx}`),
              );
              if (target) {
                votedRound = g.round;
                client.publish({ destination: `/app/party/${partyId}/game/vote`, body: target.id });
              }
            } else if (g.phase === "FINISHED") {
              clearTimeout(timeout);
              client.deactivate().then(resolve, resolve);
            }
          });
          client.publish({ destination: `/app/party/${partyId}/user-snapshot`, body: "{}" });
        },
      });
      client.activate();
    });

    stats.finished++;
    stats.gameDurations.push(Date.now() - t0);
  } catch (e) {
    stats.failed++;
    if (stats.errors.length < 10) stats.errors.push(`game ${idx}: ${e.message}`);
  }
}

console.log(`Stress: ${N} games, ramp ${RAMP_MS}ms, vs ${BASE}`);
const t0 = Date.now();
const games = [];
for (let i = 0; i < N; i++) {
  games.push(runGame(i));
  await new Promise((r) => setTimeout(r, RAMP_MS));
}

const progress = setInterval(() => {
  console.log(
    `  t+${Math.round((Date.now() - t0) / 1000)}s started=${stats.started} finished=${stats.finished} failed=${stats.failed}`,
  );
}, 10_000);

await Promise.all(games);
clearInterval(progress);

const wall = Math.round((Date.now() - t0) / 1000);
console.log("=== RESULTS ===");
console.log(`games: ${N}, finished: ${stats.finished}, failed: ${stats.failed}, stompErrors: ${stats.stompErrors}`);
console.log(`wall time: ${wall}s`);
console.log(`create latency ms: p50=${pct(stats.createLatencies, 50)} p95=${pct(stats.createLatencies, 95)} max=${Math.max(0, ...stats.createLatencies)}`);
console.log(`game duration s: p50=${Math.round(pct(stats.gameDurations, 50) / 1000)} p95=${Math.round(pct(stats.gameDurations, 95) / 1000)} max=${Math.round(Math.max(0, ...stats.gameDurations) / 1000)}`);
for (const e of stats.errors) console.log("ERR:", e);
process.exit(stats.failed > 0 ? 1 : 0);
