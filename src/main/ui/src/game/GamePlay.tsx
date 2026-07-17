import { useEffect, useRef, useState } from "react";
import type { GameSnapshot, PartySnapshot, Publish } from "./types.ts";

function Countdown({ endsAt, phaseKey }: { endsAt: number; phaseKey: string }) {
  const [now, setNow] = useState(() => Date.now());
  const startRef = useRef(Date.now());

  useEffect(() => {
    startRef.current = Date.now();
  }, [phaseKey]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(timer);
  }, []);

  const remaining = Math.max(0, endsAt - now);
  const total = Math.max(1000, endsAt - startRef.current);
  const pct = Math.min(100, (remaining / total) * 100);

  return (
    <div className="flex items-center gap-3 mb-4">
      <progress
        className={`progress w-full ${remaining < 10_000 ? "progress-error" : "progress-primary"}`}
        value={pct}
        max={100}
      />
      <span className="font-mono text-lg w-12 text-right">
        {Math.ceil(remaining / 1000)}s
      </span>
    </div>
  );
}

function Story({ game }: { game: GameSnapshot }) {
  const bottomRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [game.story.length]);

  return (
    <div className="card bg-base-100 shadow mb-4">
      <div className="card-body p-4 text-left max-h-60 overflow-y-auto">
        <h3 className="text-sm text-gray-500 font-bold uppercase">The Story</h3>
        <p className="leading-relaxed">
          {game.story.map((line, i) => (
            <span key={i}>
              {line.moral && (
                <span className="block mt-2 font-bold">
                  The moral of the story:{" "}
                </span>
              )}
              <span
                style={line.color ? { color: line.color } : undefined}
                title={line.authorName ?? "The Narrator"}
              >
                {line.text}
              </span>{" "}
            </span>
          ))}
        </p>
        <div ref={bottomRef} />
      </div>
    </div>
  );
}

function Scoreboard({
  game,
  party,
  myPlayerId,
}: {
  game: GameSnapshot;
  party: PartySnapshot;
  myPlayerId: string;
}) {
  const rows = Object.entries(game.scores)
    .map(([id, score]) => ({
      id,
      score,
      name: party.members[id]?.name ?? "???",
      color: party.members[id]?.color,
      points: game.roundPoints?.[id] ?? 0,
      winner: game.winnerIds?.includes(id) ?? false,
    }))
    .sort((a, b) => b.score - a.score);

  return (
    <div className="card bg-base-100 shadow mb-4">
      <div className="card-body p-4 text-left">
        <h3 className="text-sm text-gray-500 font-bold uppercase">Scores</h3>
        {rows.map((row) => (
          <div key={row.id} className="flex justify-between items-center">
            <span style={{ color: row.color }}>
              {row.winner && "👑 "}
              {row.name}
              {row.id === myPlayerId ? " (You)" : ""}
            </span>
            <span>
              {row.points > 0 && (
                <span className="text-success mr-2">+{row.points}</span>
              )}
              <span className="badge badge-neutral">{row.score}</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function GamePlay({
  partyInfo,
  party,
  game,
  publish,
  onLeave,
}: {
  partyInfo: { partyId: string; playerId: string };
  party: PartySnapshot;
  game: GameSnapshot;
  publish: Publish;
  onLeave: () => void;
}) {
  const [draft, setDraft] = useState("");
  const [myVote, setMyVote] = useState<string | null>(null);
  const [mySubmission, setMySubmission] = useState<string | null>(null);

  const phaseKey = `${game.round}:${game.phase}`;
  const roundKey = `${game.round}`;

  // New round: clear everything typed for the previous one.
  useEffect(() => {
    setDraft("");
    setMyVote(null);
    setMySubmission(null);
  }, [roundKey]);

  const base = `/app/party/${partyInfo.partyId}/game`;

  const submitLine = () => {
    const text = draft.replace(/\s+/g, " ").trim();
    if (!text) return;
    publish({ destination: `${base}/submit`, body: text });
    setMySubmission(text);
  };

  const vote = (submissionId: string, text: string) => {
    if (isMine(text)) return;
    publish({ destination: `${base}/vote`, body: submissionId });
    setMyVote(submissionId);
  };

  const playAgain = () => {
    publish({ destination: `${base}/playAgain`, body: true });
  };

  const isMine = (text: string) =>
    mySubmission !== null && text === mySubmission;

  const hasSubmitted = game.submitted.includes(partyInfo.playerId);
  const memberCount = Object.keys(party.members).length;
  const isHost = party.members[partyInfo.playerId]?.host ?? false;

  const header = (
    <div className="mb-4">
      <div className="flex justify-between items-baseline mb-1">
        <span className="text-gray-500">
          Round {game.round} / {game.totalRounds}
        </span>
        {game.round === game.totalRounds && game.phase !== "FINISHED" && (
          <span className="badge badge-warning">2x points!</span>
        )}
      </div>
      <h2 className="text-xl font-bold">{game.prompt}</h2>
    </div>
  );

  if (game.phase === "SUBMITTING") {
    return (
      <div>
        {header}
        <Countdown endsAt={game.phaseEndsAt} phaseKey={phaseKey} />
        <Story game={game} />
        <textarea
          className="textarea w-full text-lg mb-2"
          rows={3}
          placeholder={
            game.round === game.totalRounds
              ? "The moral of the story is..."
              : "...continue the story!"
          }
          maxLength={game.maxSubmissionLength}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
        />
        <div className="text-right text-xs text-gray-500 mb-2">
          {draft.length}/{game.maxSubmissionLength}
        </div>
        <button
          className="btn btn-primary w-full mb-4"
          onClick={submitLine}
          disabled={!draft.trim()}
        >
          {hasSubmitted ? "Update Submission" : "Submit"}
        </button>
        {hasSubmitted && (
          <p className="text-success mb-2">✅ Submitted! You can still edit.</p>
        )}
        <p className="text-gray-500">
          {game.submitted.length}/{memberCount} players submitted
        </p>
      </div>
    );
  }

  if (game.phase === "VOTING") {
    return (
      <div>
        {header}
        <Countdown endsAt={game.phaseEndsAt} phaseKey={phaseKey} />
        <Story game={game} />
        <div className="flex flex-col gap-2 mb-4">
          {(game.submissions ?? []).map((s) => {
            const mine = isMine(s.text);
            const selected = myVote === s.id;
            return (
              <button
                key={s.id}
                className={`btn h-auto min-h-12 py-3 justify-start text-left normal-case ${
                  selected
                    ? "btn-primary"
                    : mine
                      ? "btn-ghost border border-base-300 opacity-60"
                      : "btn-outline"
                }`}
                onClick={() => vote(s.id, s.text)}
                disabled={mine}
              >
                <span className="whitespace-normal">
                  {s.text}
                  {mine && (
                    <span className="badge badge-ghost ml-2">Yours</span>
                  )}
                  {selected && " ✔"}
                </span>
              </button>
            );
          })}
        </div>
        <p className="text-gray-500">
          {game.voted.length}/{memberCount} players voted
        </p>
      </div>
    );
  }

  if (game.phase === "REVEAL") {
    return (
      <div>
        {header}
        <Countdown endsAt={game.phaseEndsAt} phaseKey={phaseKey} />
        {(game.submissions ?? []).length === 0 && (
          <p className="text-gray-500 mb-4">Nobody submitted anything! 😅</p>
        )}
        <div className="flex flex-col gap-2 mb-4">
          {(game.submissions ?? []).map((s) => (
            <div
              key={s.id}
              className={`card shadow-sm ${
                s.id === game.winnerSubmissionId
                  ? "bg-primary text-primary-content"
                  : "bg-base-100"
              }`}
            >
              <div className="card-body p-3 text-left">
                <p>
                  {s.id === game.winnerSubmissionId && "👑 "}
                  {s.text}
                </p>
                <div className="flex justify-between text-sm">
                  <span style={{ color: s.color ?? undefined }}>
                    — {s.authorName}
                  </span>
                  <span>
                    {s.votes} vote{s.votes === 1 ? "" : "s"}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
        <Scoreboard game={game} party={party} myPlayerId={partyInfo.playerId} />
      </div>
    );
  }

  // FINISHED
  const winners = (game.winnerIds ?? [])
    .map((id) => party.members[id]?.name ?? "???")
    .join(" & ");

  return (
    <div>
      <h2 className="text-3xl font-bold mb-2">🏆 {winners} wins!</h2>
      <p className="text-gray-500 mb-4">The End!</p>
      <Story game={game} />
      <Scoreboard game={game} party={party} myPlayerId={partyInfo.playerId} />
      {isHost && (
        <button className="btn btn-primary w-full mb-2" onClick={playAgain}>
          Play Again
        </button>
      )}
      <button className="btn btn-secondary w-full" onClick={onLeave}>
        Leave
      </button>
    </div>
  );
}
