// Drives a full Yarnia game: 1 scripted human (host) + 2 bots.
// Run from src/main/ui so its node_modules resolve.
import { createRequire } from "module";
const require = createRequire(new URL("../src/main/ui/package.json", import.meta.url));
const SockJS = require("sockjs-client");
const { Client } = require("@stomp/stompjs");

const BASE = `http://localhost:${process.env.YARNIA_PORT ?? "3001"}`;
const log = (...a) => console.log(new Date().toISOString().slice(11, 23), ...a);

const fail = (msg) => {
  console.error("FAIL:", msg);
  process.exit(1);
};

const res = await fetch(`${BASE}/api/party/create`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: "{}",
});
if (!res.ok) fail("create party: " + res.status);
const { partyId, playerId, joinCode, joinToken } = await res.json();
log("party created", joinCode, partyId);

let lastGame = null;
let submittedRound = -1;
let votedRound = -1;
let finished = false;
let chatSent = false;
const chatReceived = [];
let chatHistoryReceived = null;

const client = new Client({
  webSocketFactory: () => new SockJS(`${BASE}/ws`),
  connectHeaders: { partyId, playerId, joinToken, playerName: "Scripty" },
  reconnectDelay: 2000,
  onStompError: (frame) => log("STOMP error:", frame.headers["message"]),
  onConnect: () => {
    log("connected");
    client.subscribe(`/topic/party/${partyId}/snapshot`, (m) => {
      const snap = JSON.parse(m.body);
      const members = Object.entries(snap.members);
      const colors = members.map(([, v]) => v.color);
      if (new Set(colors).size !== colors.length)
        fail("duplicate player colors: " + colors.join(" | "));
      if (!colors.every((c) => /^light-dark\(#[0-9a-f]{6}, #[0-9a-f]{6}\)$/.test(c)))
        fail("unexpected color format: " + colors.join(" | "));
      log(
        "party:",
        snap.partyPhase,
        members.map(([, v]) => `${v.name}${v.bot ? "(bot)" : ""}${v.ready ? "*" : ""}`).join(", "),
      );
      if (snap.partyPhase === "WAITING") {
        const bots = members.filter(([, v]) => v.bot).length;
        const me = snap.members[playerId];
        if (bots < 2) {
          client.publish({ destination: `/app/party/${partyId}/addBot`, body: "true" });
        } else if (me && !me.ready) {
          client.publish({ destination: `/app/party/${partyId}/setReady`, body: "true" });
        } else if (me && me.ready && members.every(([, v]) => v.ready)) {
          log(">>> starting game");
          client.publish({ destination: `/app/party/${partyId}/startGame`, body: "true" });
        }
      }
    });
    client.subscribe(`/topic/party/${partyId}/game`, (m) => {
      const g = JSON.parse(m.body);
      lastGame = g;
      log(
        `game: r${g.round}/${g.totalRounds} ${g.phase} submitted=${g.submitted.length} voted=${g.voted.length}`,
      );
      if (g.phase === "SUBMITTING" && submittedRound !== g.round) {
        submittedRound = g.round;
        const text =
          g.round === g.totalRounds
            ? "The moral is: always test your code."
            : `and then chapter ${g.round} got weirder (scripted).`;
        setTimeout(() => {
          client.publish({ destination: `/app/party/${partyId}/game/submit`, body: text });
          log(">>> submitted:", text);
        }, 500);
      }
      if (g.phase === "VOTING" && votedRound !== g.round && g.submissions) {
        const mine =
          g.round === g.totalRounds
            ? "The moral is: always test your code."
            : `and then chapter ${g.round} got weirder (scripted).`;
        const candidate = g.submissions.find((s) => s.text !== mine);
        if (candidate) {
          votedRound = g.round;
          setTimeout(() => {
            client.publish({ destination: `/app/party/${partyId}/game/vote`, body: candidate.id });
            log(">>> voted for:", candidate.text.slice(0, 40));
          }, 300);
        }
      }
      if (g.phase === "REVEAL") {
        for (const s of g.submissions ?? []) {
          log(`   reveal: [${s.votes} votes] ${s.authorName}: ${s.text.slice(0, 60)}`);
        }
        log("   scores:", JSON.stringify(g.scores));
        log("   roundPoints:", JSON.stringify(g.roundPoints));
      }
      if (g.phase === "FINISHED" && !finished) {
        finished = true;
        log("=== GAME FINISHED ===");
        log("story:");
        for (const line of g.story) {
          log(`   ${line.moral ? "[MORAL] " : ""}${line.authorName ?? "Narrator"}: ${line.text}`);
        }
        log("final scores:", JSON.stringify(g.scores));
        log("winners:", JSON.stringify(g.winnerIds));

        // Assertions
        const errs = [];
        if (!chatReceived.some((m) => m.text.startsWith("hello bots"))) errs.push("own chat message never echoed");
        if (chatHistoryReceived === null) errs.push("chat history never received");
        const botChats = chatReceived.filter((m) => m.bot).length;
        log(`chat summary: ${chatReceived.length} received, ${botChats} from bots`);
        if (g.story.length < 2) errs.push("story too short: " + g.story.length);
        if (!g.winnerIds || g.winnerIds.length === 0) errs.push("no winners");
        const total = Object.values(g.scores).reduce((a, b) => a + b, 0);
        if (total <= 0) errs.push("no points were scored");
        if (!g.story.some((l) => l.moral)) errs.push("no moral line in story");
        if (errs.length) fail(errs.join("; "));
        console.log("PASS: full game completed");
        client.deactivate().then(() => process.exit(0));
      }
    });
    client.subscribe(`/topic/party/${partyId}/chat`, (m) => {
      const msg = JSON.parse(m.body);
      chatReceived.push(msg);
      log(`CHAT ${msg.senderName}${msg.bot ? "[bot]" : ""}: ${msg.text}`);
      if (!chatSent) {
        chatSent = true;
        setTimeout(() => {
          client.publish({
            destination: `/app/party/${partyId}/chat`,
            body: "hello bots! good luck, you will need it",
          });
        }, 500);
      }
    });
    client.subscribe(`/topic/party/${partyId}/chat-history-${playerId}`, (m) => {
      chatHistoryReceived = JSON.parse(m.body);
      log(`CHAT-HISTORY: ${chatHistoryReceived.length} messages`);
    });
    client.publish({ destination: `/app/party/${partyId}/chat-history`, body: "true" });
    // Open the conversation once connected.
    setTimeout(() => {
      if (!chatSent) {
        chatSent = true;
        client.publish({
          destination: `/app/party/${partyId}/chat`,
          body: "hello bots! good luck, you will need it",
        });
      }
    }, 1500);
    client.publish({
      destination: `/app/party/${partyId}/user-snapshot`,
      body: JSON.stringify({ partyId }),
    });
  },
});

client.activate();

setTimeout(() => {
  log("last game snapshot:", JSON.stringify(lastGame));
  fail("timeout after 120s");
}, 120_000);
