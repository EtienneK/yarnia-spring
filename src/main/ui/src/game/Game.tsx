import { useCallback, useEffect, useRef, useState } from "react";
import type { PartyMatchInfo } from "./Menu.tsx";
import Hero from "../components/Hero.tsx";
import { RxStomp } from "@stomp/rx-stomp";
import SockJS from "sockjs-client/dist/sockjs";
import { map } from "rxjs";
import Cookies from "universal-cookie";
import type { ChatMessage, GameSnapshot, PartySnapshot } from "./types.ts";
import { Lobby } from "./Lobby.tsx";
import { GamePlay } from "./GamePlay.tsx";
import { Chat } from "./Chat.tsx";
import { sound } from "./audio.ts";

export function Game({
  partyInfo,
  onLeave,
}: {
  partyInfo: PartyMatchInfo;
  onLeave: () => void;
}) {
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<PartySnapshot | null>(null);
  const [gameSnapshot, setGameSnapshot] = useState<GameSnapshot | null>(null);
  const [nameInput, setNameInput] = useState<string>("");
  const [soundOn, setSoundOn] = useState(
    () => localStorage.getItem("sound") !== "off",
  );
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatOpen, setChatOpen] = useState(false);
  const [chatUnread, setChatUnread] = useState(0);
  const chatOpenRef = useRef(false);

  useEffect(() => {
    sound.setEnabled(soundOn);
  }, [soundOn]);

  const toggleSound = () => {
    localStorage.setItem("sound", soundOn ? "off" : "on");
    setSoundOn(!soundOn);
  };

  // Music follows the party phase; stingers are triggered by the components.
  const partyPhase = snapshot?.partyPhase;
  useEffect(() => {
    if (partyPhase === "WAITING") sound.playMusic("lobby");
    else if (partyPhase === "PLAYING") sound.playMusic("game");
    else if (partyPhase === "FINISHED") sound.playMusic("victory");
    else sound.stopMusic();
    return () => sound.stopMusic();
  }, [partyPhase]);

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
      } else if (errorCode === "in_progress") {
        setConnectionError("This game is already in progress.");
        client.deactivate();
      } else {
        console.error(error);
      }
    });

    const snapshotTopicSub = client
      .watch(`/topic/party/${partyInfo.partyId}/snapshot`)
      .pipe(map((message) => JSON.parse(message.body) as PartySnapshot))
      .subscribe((message) => {
        setSnapshot(message);
        const playerName = message.members[partyInfo.playerId]?.name;
        if (playerName) {
          setNameInput(playerName);
          localStorage.setItem("preferredName", playerName);
        }
      });

    const gameTopicSub = client
      .watch(`/topic/party/${partyInfo.partyId}/game`)
      .pipe(map((message) => JSON.parse(message.body) as GameSnapshot))
      .subscribe((message) => {
        setGameSnapshot(message);
      });

    const chatTopicSub = client
      .watch(`/topic/party/${partyInfo.partyId}/chat`)
      .pipe(map((message) => JSON.parse(message.body) as ChatMessage))
      .subscribe((message) => {
        setChatMessages((prev) =>
          prev.some((m) => m.id === message.id) ? prev : [...prev, message],
        );
        if (message.senderId !== partyInfo.playerId) {
          sound.chat();
          if (!chatOpenRef.current) setChatUnread((n) => n + 1);
        }
      });

    const chatHistorySub = client
      .watch(`/topic/party/${partyInfo.partyId}/chat-history-${partyInfo.playerId}`)
      .pipe(map((message) => JSON.parse(message.body) as ChatMessage[]))
      .subscribe((history) => {
        setChatMessages(history);
      });

    const connectedSub = client.connected$.subscribe(() => {
      publish({
        destination: `/app/party/${partyInfo.partyId}/user-snapshot`,
        body: { partyId: partyInfo.partyId },
      });
      // Also ask for the game snapshot in case we (re)connected mid-game.
      publish({
        destination: `/app/party/${partyInfo.partyId}/game/snapshot`,
        body: true,
      });
      publish({
        destination: `/app/party/${partyInfo.partyId}/chat-history`,
        body: true,
      });
    });

    return () => {
      connectedSub.unsubscribe();
      errorSub.unsubscribe();
      snapshotTopicSub.unsubscribe();
      gameTopicSub.unsubscribe();
      chatTopicSub.unsubscribe();
      chatHistorySub.unsubscribe();
      client.deactivate();
    };
  }, [partyInfo.joinToken, partyInfo.partyId, partyInfo.playerId, publish]);

  const nameTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  const toggleChat = () => {
    setChatOpen((open) => {
      const next = !open;
      chatOpenRef.current = next;
      if (next) setChatUnread(0);
      return next;
    });
  };

  const sendChat = (text: string) => {
    publish({
      destination: `/app/party/${partyInfo.partyId}/chat`,
      body: text,
    });
  };

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
        <button className="btn btn-secondary" onClick={onLeave}>
          Leave
        </button>
      </Hero>
    );
  }

  const inGame = snapshot.partyPhase !== "WAITING";

  return (
    <div className="bg-base-200 min-h-screen text-center p-3 pt-6 m-auto max-w-xl">
      {!inGame && (
        <Lobby
          joinCode={partyInfo.joinCode}
          snapshot={snapshot}
          myPlayerId={partyInfo.playerId}
          soundOn={soundOn}
          onToggleSound={toggleSound}
          nameInput={nameInput}
          onNameChange={onNameChange}
          onNameBlur={onNameBlur}
          onToggleReady={() =>
            publish({
              destination: `/app/party/${partyInfo.partyId}/setReady`,
              body: !snapshot.members[partyInfo.playerId]?.ready,
            })
          }
          onStartGame={() =>
            publish({
              destination: `/app/party/${partyInfo.partyId}/startGame`,
              body: true,
            })
          }
          onAddBot={() =>
            publish({
              destination: `/app/party/${partyInfo.partyId}/addBot`,
              body: true,
            })
          }
          onLeave={onLeave}
        />
      )}

      {inGame && !gameSnapshot && (
        <p className="mt-10">
          <span className="loading loading-spinner loading-sm mr-2"></span>
          Loading game...
        </p>
      )}

      {inGame && gameSnapshot && (
        <GamePlay
          partyInfo={partyInfo}
          party={snapshot}
          game={gameSnapshot}
          publish={publish}
          onLeave={onLeave}
          soundOn={soundOn}
          onToggleSound={toggleSound}
        />
      )}

      <Chat
        messages={chatMessages}
        unread={chatUnread}
        open={chatOpen}
        onToggle={toggleChat}
        onSend={sendChat}
        myPlayerId={partyInfo.playerId}
      />
    </div>
  );
}
