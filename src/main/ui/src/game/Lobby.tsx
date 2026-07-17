import {
  MAX_BOTS_IN_PARTY,
  MAX_NAME_LENGTH,
  MAX_PARTY_SIZE,
  MIN_PARTY_SIZE,
} from "./rules.ts";
import type { PartySnapshot } from "./types.ts";

export function Lobby({
  joinCode,
  snapshot,
  myPlayerId,
  nameInput,
  onNameChange,
  onNameBlur,
  onToggleReady,
  onStartGame,
  onAddBot,
  onLeave,
}: {
  joinCode: string;
  snapshot: PartySnapshot;
  myPlayerId: string;
  nameInput: string;
  onNameChange: (value: string) => void;
  onNameBlur: () => void;
  onToggleReady: () => void;
  onStartGame: () => void;
  onAddBot: () => void;
  onLeave: () => void;
}) {
  const myMember = snapshot.members[myPlayerId];
  const isHost = myMember?.host ?? false;
  const memberList = Object.entries(snapshot.members);

  const waitingForReady = memberList.findIndex((m) => !m[1].ready) > -1;
  const needMorePlayers = MIN_PARTY_SIZE - memberList.length > 0;
  const botCount = memberList.filter(([, m]) => m.bot).length;

  return (
    <div>
      <div className="mb-10">
        <div className="text-xl mb-1 text-gray-500">Join Code:</div>
        <div className="font-mono text-4xl tracking-widest text-center">
          {joinCode}
        </div>
      </div>

      <label className="block text-gray-500 text-sm text-left">Your Name</label>
      <input
        type="text"
        placeholder="Your name"
        value={nameInput}
        onChange={(e) => onNameChange(e.target.value)}
        onBlur={onNameBlur}
        className="input w-full mb-5 text-lg"
        maxLength={MAX_NAME_LENGTH}
      />

      <div className="text-left mb-10">
        <div className="text-lg font-bold">Players ({memberList.length})</div>
        {memberList.map(([id, member]) => (
          <div key={id} className="party-member-row">
            {member.ready ? (
              <span className="mr-2">✅</span>
            ) : (
              <span className="mr-2">❌</span>
            )}
            <span className="party-member-name" style={{ color: member.color }}>
              {member.name}
              {id === myPlayerId ? " (You)" : ""}
            </span>
            <span className="party-member-badges">
              {member.host && (
                <span className="badge badge-primary ml-2">Host</span>
              )}
              {member.bot && (
                <span className="badge badge-secondary ml-2">Bot</span>
              )}
              {!member.connected && (
                <span className="badge badge-error ml-2">Disconnected</span>
              )}
            </span>
          </div>
        ))}
      </div>

      {needMorePlayers && (
        <p className="text-gray-500 mb-10">
          <span className="loading loading-spinner loading-sm mr-2"></span>
          Waiting for {MIN_PARTY_SIZE - memberList.length} more players...
        </p>
      )}

      {!needMorePlayers && waitingForReady && (
        <p className="text-gray-500 mb-10">
          <span className="loading loading-spinner loading-sm mr-2"></span>
          Waiting for all players to ready-up...
        </p>
      )}

      <button
        className={`btn w-full mb-2 ${myMember?.ready ? "btn-secondary" : "btn-success"}`}
        onClick={onToggleReady}
      >
        {myMember?.ready ? "Unready" : "Ready"}
      </button>

      {isHost && (
        <button
          className="btn btn-primary w-full mb-2"
          onClick={onStartGame}
          disabled={needMorePlayers || waitingForReady}
        >
          Start Game
        </button>
      )}

      {isHost &&
        botCount < MAX_BOTS_IN_PARTY &&
        memberList.length < MAX_PARTY_SIZE && (
          <button className="btn btn-secondary mr-2" onClick={onAddBot}>
            Add Bot
          </button>
        )}
      <button className="btn btn-secondary" onClick={onLeave}>
        Leave
      </button>
    </div>
  );
}
