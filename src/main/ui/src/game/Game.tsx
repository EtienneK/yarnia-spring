import { useCallback, useEffect, useRef, useState } from "react";
import type { PartyMatchInfo } from "./Menu.tsx";
import { MIN_PARTY_SIZE } from "./rules.ts";
import Hero from "../components/Hero.tsx";
//import { useWebSocketService } from "../utils/hooks.ts";
import { RxStomp } from "@stomp/rx-stomp";
import SockJS from "sockjs-client/dist/sockjs";
import { map } from "rxjs";
import Cookies from "universal-cookie";
import { MAX_NAME_LENGTH } from "../utils/constants.ts";

// import { PartyBot } from "./bot.ts";

export type GamePhase = "WAITING" | "PLAYING" | "FINISHED";

export interface PartySnapshot {
  partyPhase: GamePhase;
  members: Record<
    string,
    {
      name: string;
      color: string;
      host: boolean;
      ready: boolean;
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
  const [nameInput, setNameInput] = useState<string>("");

  useEffect(() => {
    const cookies = new Cookies(null, { path: "/" });
    cookies.set("partyId", partyInfo.partyId);
    cookies.set("playerId", partyInfo.playerId);
    cookies.set("joinToken", partyInfo.joinToken);
    return () => {
      cookies.remove("partyId");
      cookies.remove("playerId");
      cookies.remove("joinToken");
    };
  }, [partyInfo.joinToken, partyInfo.partyId, partyInfo.playerId]);
  const clientRef = useRef(new RxStomp());

  const publish = useCallback(
    <T,>({ destination, body }: { destination: string; body: T }) => {
      let toPublish: string;
      if (typeof body === "string") {
        toPublish = body;
      } else {
        toPublish = JSON.stringify(body);
      }
      clientRef.current.publish({
        destination,
        body: toPublish,
      });
    },
    [],
  );

  useEffect(() => {
    const client = clientRef.current;
    let preferredName: string | null = localStorage.getItem("preferredName");
    if (preferredName?.trim().length === 0) {
      localStorage.removeItem("preferredName");
      preferredName = null;
    }

    client.configure({
      webSocketFactory: () => new SockJS(window.location.origin + "/ws"),
      connectHeaders: {
        partyId: partyInfo.partyId,
        playerId: partyInfo.playerId,
        joinToken: partyInfo.joinToken,
        ...(preferredName ? { playerName: preferredName } : undefined),
      },
      debug: (msg) => {
        console.log("stomp - ", msg);
      },
      reconnectDelay: 5_000,
      heartbeatIncoming: 1_000,
      heartbeatOutgoing: 1_000,
      connectionTimeout: 5_000,
    });

    client.activate();

    const errorSub = client.stompErrors$.subscribe((error) => {
      const errorCode = error?.headers?.message;
      if (errorCode === "forbidden") {
        setConnectionError("Unauthorised.");
        client.deactivate();
      } else if (errorCode === "full") {
        setConnectionError("Party is full.");
        client.deactivate();
      } else {
        console.error(error);
      }
    });

    const snapshotTopicSub = client
      .watch(`/topic/party/${partyInfo.partyId}/snapshot`)
      .pipe(map((message) => JSON.parse(message.body)))
      .subscribe((message) => {
        setSnapshot(message);
        const playerName = message.members[partyInfo.playerId].name;
        setNameInput(playerName);
        localStorage.setItem("preferredName", playerName);
      });

    // const snapshotQueueSub = client
    //   .watch("/user/queue/snapshot")
    //   .pipe(map((message) => JSON.parse(message.body) as PartySnapshot))
    //   .subscribe((message) => {
    //     setSnapshot(message);
    //     if (isFirstUpdate.current) {
    //       isFirstUpdate.current = false;
    //       const playerName = message.members[partyInfo.playerId].name;
    //       setNameInput(playerName);
    //       localStorage.setItem("preferredName", playerName);
    //     }
    //   });

    const connectedSub = client.connected$.subscribe(() => {
      publish({
        destination: `/app/party/${partyInfo.partyId}/user-snapshot`,
        body: { partyId: partyInfo.partyId },
      });
    });

    return () => {
      connectedSub.unsubscribe();
      errorSub.unsubscribe();
      snapshotTopicSub.unsubscribe();
      // snapshotQueueSub.unsubscribe();
      client.deactivate();
    };
  }, [partyInfo.joinToken, partyInfo.partyId, partyInfo.playerId, publish]);

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
    if (value.trim().length > 0) {
      if (nameTimeoutRef.current) clearTimeout(nameTimeoutRef.current);
      nameTimeoutRef.current = setTimeout(() => {
        publish({
          destination: `/app/party/${partyInfo.partyId}/setName`,
          body: value,
        });
      }, 300);
    }
  };

  const onNameBlur = () => {
    setNameInput(localStorage.getItem("preferredName") as string);
  };

  const addBot = () => {
    publish({ destination: `/app/party/${partyInfo.partyId}/addBot`, body: true, });
  };

  const toggleReady = () => {
    publish({ destination: `/app/party/${partyInfo.partyId}/setReady`, body: !myMember?.ready, });
  };

  const startGame = () => {
    publish({ destination: `/app/party/${partyInfo.partyId}/startGame`, body: true, });
  };

  const finishGame = () => {
    publish({ destination: `/app/party/${partyInfo.partyId}/finishGame`, body: true, });
  };

  const myMember = snapshot?.members[partyInfo.playerId];
  const isHost = myMember?.host ?? false;
  const memberList = snapshot ? Object.entries(snapshot.members) : [];

  const waitingForReady = () => memberList.findIndex((m) => !m[1].ready) > -1;
  const needMorePlayers = () => MIN_PARTY_SIZE - memberList.length > 0;

  if (!snapshot || connectionError) {
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
            onBlur={() => onNameBlur()}
            className="input w-full mb-5 text-lg"
            maxLength={MAX_NAME_LENGTH}
          />

          <div className="text-left mb-10">
            <div className="text-lg font-bold">
              Players ({memberList.length})
            </div>
            {memberList.map(([id, member]) => (
              <div key={id} className="party-member-row">
                {member.ready ? (
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
                  {member.host && (
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
            className={`btn w-full mb-2 ${myMember?.ready ? "btn-secondary" : "btn-success"}`}
            onClick={toggleReady}
          >
            {myMember?.ready ? "Unready" : "Ready"}
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

          {snapshot?.partyPhase === "PLAYING" && (
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

          {snapshot?.partyPhase === "FINISHED" && (
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
