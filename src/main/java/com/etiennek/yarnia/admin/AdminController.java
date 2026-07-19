package com.etiennek.yarnia.admin;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.etiennek.yarnia.chat.repos.ChatMessageRepository;
import com.etiennek.yarnia.game.repos.GameStateRepository;
import com.etiennek.yarnia.game.repos.StorySegmentRepository;
import com.etiennek.yarnia.party.Constants.PartyPhase;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;
import com.etiennek.yarnia.party.repos.PartyRepository;
import com.etiennek.yarnia.party.repos.PartyStateRepository;

/**
 * Read-only ops dashboard. Auth handled by AdminAuthFilter; deliberately not
 * linked from the game UI in any way.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private @Autowired PartyRepository partyRepository;
    private @Autowired PartyStateRepository partyStateRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired GameStateRepository gameStateRepository;
    private @Autowired StorySegmentRepository storySegmentRepository;
    private @Autowired ChatMessageRepository chatMessageRepository;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    public record MemberStat(String name, boolean bot, boolean host, boolean connected, boolean ready, int score) {
    }

    public record PartyStat(String joinCode, boolean publicGame, String phase, List<MemberStat> members,
            Integer round, Integer totalRoundsHint, String gamePhase, Long phaseEndsInMs,
            long storyLines, long chatMessages) {
    }

    public record Stats(long uptimeMs, String dbPath, long dbSizeBytes, long totalParties, long waiting, long playing,
            long finished, long publicOpen, long humansConnected, long bots, long chatTotal, List<PartyStat> parties) {
    }

    @GetMapping("/api/stats")
    @Transactional(readOnly = true)
    public Stats stats() {
        final var states = partyStateRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s));

        final var partyStats = partyRepository.findAll().stream().map(party -> {
            final UUID id = party.getId();
            final var state = states.get(id);
            final var phase = state == null ? "?" : state.getPartyPhase().name();
            final var members = partyMemberRepository.findByPartyStateId(id).stream()
                    .sorted(Comparator.comparing(PartyMember::getName))
                    .map(m -> new MemberStat(m.getName(), m.isBot(), m.isHost(), m.isConnected(), m.isReady(),
                            m.getScore()))
                    .toList();
            final var game = gameStateRepository.findOneById(id).orElse(null);
            return new PartyStat(
                    party.getJoinCode(),
                    party.isPublicGame(),
                    phase,
                    members,
                    game == null ? null : game.getRoundNumber(),
                    null,
                    game == null ? null : game.getPhase().name(),
                    game == null ? null : game.getPhaseEndsAt().toEpochMilli() - Instant.now().toEpochMilli(),
                    storySegmentRepository.countByPartyId(id),
                    chatMessageRepository.countByPartyId(id));
        }).sorted(Comparator.comparing(PartyStat::phase).thenComparing(PartyStat::joinCode)).toList();

        final var allMembers = partyStats.stream().flatMap(p -> p.members().stream()).toList();

        return new Stats(
                ManagementFactory.getRuntimeMXBean().getUptime(),
                dbPath(),
                dbSize(),
                partyStats.size(),
                partyStats.stream().filter(p -> p.phase().equals(PartyPhase.WAITING.name())).count(),
                partyStats.stream().filter(p -> p.phase().equals(PartyPhase.PLAYING.name())).count(),
                partyStats.stream().filter(p -> p.phase().equals(PartyPhase.FINISHED.name())).count(),
                partyStats.stream()
                        .filter(p -> p.publicGame() && p.phase().equals(PartyPhase.WAITING.name())
                                && p.members().size() < com.etiennek.yarnia.party.Constants.MAX_PARTY_SIZE)
                        .count(),
                allMembers.stream().filter(m -> !m.bot() && m.connected()).count(),
                allMembers.stream().filter(MemberStat::bot).count(),
                chatMessageRepository.count(),
                partyStats);
    }

    private String dbPath() {
        // jdbc:sqlite:/data/yarnia.db?journal_mode=WAL -> /data/yarnia.db
        var url = datasourceUrl == null ? "" : datasourceUrl;
        url = url.replaceFirst("^jdbc:sqlite:", "");
        final var q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private long dbSize() {
        final var path = dbPath();
        return path.isBlank() ? 0 : new File(path).length();
    }

    /** Self-contained dashboard page; fetches /admin/api/stats and refreshes itself. */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String dashboard() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex, nofollow">
                <title>Yarnia Admin</title>
                <style>
                  :root { color-scheme: dark; }
                  body { background:#14181f; color:#d8dee9; font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
                         margin: 2rem; }
                  h1 { font-size: 1.3rem; } h1 small { color:#6b7280; font-weight: normal; }
                  .tiles { display:flex; flex-wrap:wrap; gap:.75rem; margin: 1rem 0 1.5rem; }
                  .tile { border:2px solid #2c3440; padding:.6rem 1rem; min-width:7.5rem; }
                  .tile b { display:block; font-size:1.5rem; }
                  .tile span { color:#8b95a5; font-size:.75rem; text-transform:uppercase; }
                  table { border-collapse: collapse; width:100%; font-size:.85rem; }
                  th, td { border:1px solid #2c3440; padding:.4rem .6rem; text-align:left; vertical-align:top; }
                  th { background:#1b212b; color:#8b95a5; text-transform:uppercase; font-size:.7rem; }
                  .phase-PLAYING { color:#7ee787; } .phase-WAITING { color:#e3b341; } .phase-FINISHED { color:#8b95a5; }
                  .bot { color:#79c0ff; } .disc { color:#f85149; text-decoration: line-through; }
                  .pub { color:#d2a8ff; }
                  #err { color:#f85149; }
                </style>
                </head>
                <body>
                <h1>Yarnia Admin <small id="meta"></small></h1>
                <div class="tiles" id="tiles"></div>
                <table>
                  <thead><tr><th>Code</th><th>Vis</th><th>Phase</th><th>Game</th><th>Players</th>
                  <th>Story</th><th>Chat</th></tr></thead>
                  <tbody id="rows"></tbody>
                </table>
                <p id="err"></p>
                <script>
                const el = (tag, cls, text) => {
                  const n = document.createElement(tag);
                  if (cls) n.className = cls;
                  if (text !== undefined) n.textContent = text;
                  return n;
                };
                const fmtUp = (ms) => {
                  const s = Math.floor(ms/1000);
                  return Math.floor(s/3600) + "h " + Math.floor((s%3600)/60) + "m";
                };
                async function refresh() {
                  try {
                    const r = await fetch("/admin/api/stats", { cache: "no-store" });
                    if (!r.ok) throw new Error("HTTP " + r.status);
                    const s = await r.json();
                    document.getElementById("err").textContent = "";
                    document.getElementById("meta").textContent =
                      "up " + fmtUp(s.uptimeMs) + " · db " + Math.round(s.dbSizeBytes/1024) + " KB (" + s.dbPath + ")";
                    const tiles = document.getElementById("tiles");
                    tiles.replaceChildren();
                    for (const [label, value] of [
                      ["parties", s.totalParties], ["waiting", s.waiting], ["playing", s.playing],
                      ["finished", s.finished], ["open public", s.publicOpen],
                      ["humans online", s.humansConnected], ["bots", s.bots], ["chat msgs", s.chatTotal],
                    ]) {
                      const t = el("div", "tile");
                      t.append(el("b", null, String(value)), el("span", null, label));
                      tiles.append(t);
                    }
                    const rows = document.getElementById("rows");
                    rows.replaceChildren();
                    for (const p of s.parties) {
                      const tr = el("tr");
                      tr.append(el("td", null, p.joinCode));
                      tr.append(el("td", p.publicGame ? "pub" : "", p.publicGame ? "public" : "private"));
                      tr.append(el("td", "phase-" + p.phase, p.phase));
                      const game = p.round == null ? "-"
                        : "r" + p.round + " " + p.gamePhase +
                          (p.phaseEndsInMs != null && p.gamePhase !== "FINISHED"
                            ? " (" + Math.max(0, Math.round(p.phaseEndsInMs/1000)) + "s)" : "");
                      tr.append(el("td", null, game));
                      const players = el("td");
                      p.members.forEach((m, i) => {
                        if (i > 0) players.append(", ");
                        const span = el("span", (m.bot ? "bot" : "") + (!m.connected && !m.bot ? " disc" : ""),
                          m.name + (m.host ? "*" : "") + " [" + m.score + "]");
                        players.append(span);
                      });
                      tr.append(players);
                      tr.append(el("td", null, String(p.storyLines)));
                      tr.append(el("td", null, String(p.chatMessages)));
                      rows.append(tr);
                    }
                  } catch (e) {
                    document.getElementById("err").textContent = "refresh failed: " + e.message;
                  }
                }
                refresh();
                setInterval(refresh, 5000);
                </script>
                </body>
                </html>
                """;
    }
}
