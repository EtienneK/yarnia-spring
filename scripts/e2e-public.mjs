// Public matchmaking verification (expects a FRESH db).
import { createRequire } from "module";
const require = createRequire(new URL("../src/main/ui/package.json", import.meta.url));
const SockJS = require("sockjs-client");
const { Client } = require("@stomp/stompjs");

const BASE = `http://localhost:${process.env.YARNIA_PORT ?? "3002"}`;
const fail = (m) => { console.error("FAIL:", m); process.exit(1); };
const post = async (path, body) => fetch(`${BASE}${path}`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  ...(body ? { body: JSON.stringify(body) } : {}),
});

// 1. No games at all -> 404
let r = await post("/api/party/join-public");
if (r.status !== 404) fail(`expected 404 with no games, got ${r.status}`);
console.log("1. join-public with no games -> 404 OK");

// 2. Private game exists -> still 404
const priv = await (await post("/api/party/create", { publicGame: false })).json();
r = await post("/api/party/join-public");
if (r.status !== 404) fail(`expected 404 with only private games, got ${r.status}`);
console.log("2. join-public ignores private games -> 404 OK");

// 3. Public game exists -> matched to it
const pub = await (await post("/api/party/create", { publicGame: true })).json();
r = await post("/api/party/join-public");
if (r.status !== 200) fail(`expected 200 with a public game, got ${r.status}`);
const match = await r.json();
if (match.partyId !== pub.partyId) fail(`matched wrong party: ${match.partyId} != ${pub.partyId}`);
if (match.joinCode !== pub.joinCode) fail(`joinCode missing/wrong: ${match.joinCode}`);
console.log(`3. join-public matched the public game (${match.joinCode}) OK`);

// 4. Connect both, check snapshot: 2 members + publicGame flag
const snapshotPromise = new Promise((resolve, reject) => {
  const clients = [];
  const mk = (creds, name) => {
    const c = new Client({
      webSocketFactory: () => new SockJS(`${BASE}/ws`),
      connectHeaders: { partyId: creds.partyId, playerId: creds.playerId, joinToken: creds.joinToken, playerName: name },
      onConnect: () => {
        c.subscribe(`/topic/party/${creds.partyId}/snapshot`, (m) => {
          const snap = JSON.parse(m.body);
          if (Object.keys(snap.members).length === 2) resolve({ snap, clients });
        });
        c.publish({ destination: `/app/party/${creds.partyId}/user-snapshot`, body: "{}" });
      },
      onStompError: (f) => reject(new Error("stomp: " + f.headers["message"])),
    });
    c.activate();
    clients.push(c);
  };
  mk(pub, "Host");
  setTimeout(() => mk(match, "Guest"), 400);
  setTimeout(() => reject(new Error("timeout waiting for 2 members")), 15000);
});

try {
  const { snap, clients } = await snapshotPromise;
  if (snap.publicGame !== true) fail("snapshot.publicGame should be true, got " + snap.publicGame);
  console.log("4. both connected; snapshot shows 2 members and publicGame=true OK");
  await Promise.all(clients.map((c) => c.deactivate()));
} catch (e) {
  fail(e.message);
}

// 5. Prefers the fuller lobby: create a second empty public game, then match again -
//    should land in the original (which has 2 members).
await post("/api/party/create", { publicGame: true });
const again = await (await post("/api/party/join-public")).json();
if (again.partyId !== pub.partyId) fail("should prefer the fuller lobby");
console.log("5. matchmaking prefers the fuller lobby OK");

console.log("PASS: public matchmaking works");
process.exit(0);
