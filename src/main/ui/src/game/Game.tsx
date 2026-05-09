import { useCallback, useEffect, useRef, useState } from "react";
import type { PartyMatchInfo } from "./Menu.tsx";
import { MIN_PARTY_SIZE } from "./rules.ts";
import Hero from "../components/Hero.tsx";
//import { useWebSocketService } from "../utils/hooks.ts";
import { RxStomp } from "@stomp/rx-stomp";
import SockJS from "sockjs-client/dist/sockjs";
import { map } from "rxjs";

// import { PartyBot } from "./bot.ts";

export type GamePhase = "waiting" | "playing" | "finished";

export interface PartySnapshot {
  phase: GamePhase;
  members: Record<
    string,
    {
      name: string;
      color: string;
      isHost: boolean;
      isReady: boolean;
      connected: boolean;
    }
  >;
}

export function Game({
  partyInfo,
  onLeave,
}: {
  partyInfo: PartyMatchInfo;
  onLeave: () => void;
}) {
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<PartySnapshot | null>(null);
  const [nameInput, setNameInput] = useState(partyInfo.playerName || "Player");

  const clientRef = useRef(new RxStomp());

  const publish = useCallback(
    <T,>({ destination, body }: { destination: string; body: T }) => {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
      })
    },
    [],
  );

  useEffect(() => {
    const client = clientRef.current;
    client.configure({
      webSocketFactory: () => new SockJS(window.location.origin + "/ws"),
      connectHeaders: {
        partyId: partyInfo.partyId,
        playerId: partyInfo.playerId,
        joinToken: partyInfo.joinToken,
      },
      debug: (msg) => {
        console.log("stomp - ", msg);
      },
      heartbeatIncoming: 0,
      heartbeatOutgoing: 20000,
      reconnectDelay: 200,
      connectionTimeout: 5000,
    });

    const errorSub = client.stompErrors$.subscribe((error) => {
      if (error?.headers?.message === "forbidden") {
        setConnectionError("Unauthorised.");
        client.deactivate();
      } else {
        console.error(error);
      }
    });

    client.activate();

    const snapshotTopicSub = client
      .watch("/user/queue/snapshot")
      .pipe(map((message) => JSON.parse(message.body)))
      .subscribe((message) => {
        setSnapshot(message);
      });

    const connectedSub = client.connected$.subscribe(() => {
      publish({
        destination: `/app/party/${partyInfo.partyId}/snapshot`,
        body: { partyId: partyInfo.partyId },
      });
    });

    return () => {
      connectedSub.unsubscribe();
      errorSub.unsubscribe();
      snapshotTopicSub.unsubscribe();
      client.deactivate();
    };
  }, [partyInfo.joinToken, partyInfo.partyId, partyInfo.playerId, publish]);

  // useWebSocketService({
  //   connectHeaders: {
  //     partyId: partyInfo.partyId,
  //     playerId: partyInfo.playerId,
  //     joinToken: partyInfo.joinToken,
  //   },
  //   onConnectCallback: ({subscribe, publish}) => {
  //     subscribe(
  //       "/topic/party/" + partyInfo.partyId + "/snapshot",
  //       (snapshot: PartySnapshot) => {
  //         setSnapshot(snapshot);
  //       },
  //     );
  //     subscribe(
  //       "/user/queue/snapshot",
  //       (snapshot: PartySnapshot) => {
  //         setSnapshot(snapshot);
  //       },
  //     );
  //     setTimeout(() => {publish(`/app/party/${partyInfo.partyId}/snapshot`, { partyId: partyInfo.partyId })}, 4000);
  //   },
  //   onErrorCallback: (error, disconnect) => {
  //     if (error?.headers?.message === "forbidden") {
  //       setConnectionError("Unauthorised.");
  //       disconnect();
  //     } else {
  //       console.error(error);
  //     }
  //   },
  // });

  // const botsRef = useRef<PartyBot[]>([]);
  const nameTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  //   const partyMatch = useActor<'partyMatch'>({
  //     name: 'partyMatch',
  //     key: [matchInfo.matchId],
  //     params: {
  //       playerId: matchInfo.playerId,
  //       joinToken: matchInfo.joinToken,
  //     },
  //     enabled: true,
  //   });

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  //   (partyMatch as any).useEvent(
  //     'partyUpdate',
  //     (partySnapshot: PartySnapshot): void => {
  //       return setSnapshot(partySnapshot)
  //     },
  //   )

  //   useEffect(() => {
  //     console.log('Connection status: ', partyMatch.connStatus)
  //     if (partyMatch.connStatus === 'connected') {
  //       partyMatch.connection?.getSnapshot().then((snap: unknown) => {
  //         const s = snap as PartySnapshot
  //         setSnapshot(s)
  //         const myName = s.members[matchInfo.playerId]?.name
  //         if (myName) setNameInput(myName)
  //       })
  //     }
  //   }, [matchInfo.playerId, partyMatch.connection, partyMatch.connStatus])

  const onNameChange = (value: string) => {
    setNameInput(value);
    localStorage.setItem("preferredName", value);
    if (nameTimeoutRef.current) clearTimeout(nameTimeoutRef.current);
    nameTimeoutRef.current = setTimeout(() => {
      //partyMatch.connection?.setName({ name: value }).catch(() => {})
    }, 300);
  };

  const addBot = () => {
    //partyMatch.connection?.addBot().catch(() => {})
  };

  const toggleReady = () => {
    //partyMatch.connection?.toggleReady().catch(() => {})
  };

  const startGame = () => {
    //partyMatch.connection?.startGame().catch(() => {})
  };

  const finishGame = () => {
    //partyMatch.connection?.finishGame().catch(() => {})
  };

  const myMember = snapshot?.members[partyInfo.playerId];
  const isHost = myMember?.isHost ?? false;
  const memberList = snapshot ? Object.entries(snapshot.members) : [];

  const waitingForReady = () => memberList.findIndex((m) => !m[1].isReady) > -1;
  const needMorePlayers = () => MIN_PARTY_SIZE - memberList.length > 0;

  if (
    /*partyMatch.connStatus !== 'connected' ||*/ !snapshot ||
    connectionError
  ) {
    return (
      <Hero>
        <p className="mb-5">
          {!connectionError && (
            <>
              <span className="loading loading-spinner loading-sm mr-2"></span>
              Connecting to{" "}
              <span className="font-bold font-mono">{partyInfo.joinCode}</span>
              ...
            </>
          )}
          {connectionError && <>{connectionError}</>}
        </p>
        <button
          className="btn btn-secondary"
          onClick={() => {
            onLeave();
          }}
        >
          Leave
        </button>
      </Hero>
    );
  }

  return (
    <div className="bg-base-200 min-h-screen text-center p-3 pt-10 m-auto max-w-xl">
      <div className="app">
        <div>
          <div className="mb-10">
            <div className="text-xl mb-1 text-gray-500">Join Code:</div>
            <div className="font-mono text-4xl tracking-widest text-center">
              {partyInfo.joinCode}
            </div>
          </div>

          <label className="block text-gray-500 text-sm text-left">
            Your Name
          </label>
          <input
            type="text"
            placeholder="Your name"
            value={nameInput}
            onChange={(e) => onNameChange(e.target.value)}
            className="input w-full mb-5 text-lg"
          />

          <div className="text-left mb-10">
            <div className="text-lg font-bold">
              Players ({memberList.length})
            </div>
            {memberList.map(([id, member]) => (
              <div key={id} className="party-member-row">
                {member.isReady ? (
                  <span className="mr-2">✅</span>
                ) : (
                  <span className="mr-2">❌</span>
                )}
                <span
                  className="party-member-name"
                  style={{ color: member.color }}
                >
                  {member.name}
                  {id === partyInfo.playerId ? " (You)" : ""}
                </span>
                <span className="party-member-badges">
                  {member.isHost && (
                    <span className="badge badge-primary ml-2">Host</span>
                  )}
                  {!member.connected && (
                    <span className="badge badge-error ml-2">Disconnected</span>
                  )}
                </span>
              </div>
            ))}
          </div>

          {needMorePlayers() && (
            <p className="text-gray-500 mb-10">
              <span className="loading loading-spinner loading-sm mr-2"></span>
              Waiting for {MIN_PARTY_SIZE - memberList.length} more players...
            </p>
          )}

          {!needMorePlayers() && waitingForReady() && (
            <p className="text-gray-500 mb-10">
              <span className="loading loading-spinner loading-sm mr-2"></span>
              Waiting for all players to ready-up...
            </p>
          )}

          <button
            className={`btn w-full mb-2 ${myMember?.isReady ? "btn-secondary" : "btn-success"}`}
            onClick={toggleReady}
          >
            {myMember?.isReady ? "Unready" : "Ready"}
          </button>

          {isHost && (
            <button
              className="btn btn-primary w-full mb-2"
              onClick={startGame}
              disabled={needMorePlayers() || waitingForReady()}
            >
              Start Game
            </button>
          )}

          <button className="btn btn-secondary" onClick={addBot}>
            Add Bot
          </button>
          <button
            className="btn btn-secondary"
            onClick={() => {
              onLeave();
            }}
          >
            Leave
          </button>

          {snapshot?.phase === "playing" && (
            <div style={{ marginTop: 16, textAlign: "center" }}>
              <p
                style={{
                  color: "#8e8e93",
                  fontSize: 14,
                  marginBottom: 12,
                }}
              >
                Game is in progress
              </p>
              {isHost ? (
                <button className="btn btn-primary" onClick={finishGame}>
                  Finish Game
                </button>
              ) : (
                <p style={{ color: "#6e6e73", fontSize: 12 }}>
                  The host can finish the game when ready.
                </p>
              )}
            </div>
          )}

          {snapshot?.phase === "finished" && (
            <div
              className="match-found-text"
              style={{ textAlign: "center", marginTop: 16 }}
            >
              Game Complete!
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
