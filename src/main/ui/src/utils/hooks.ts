import {
  useState,
  useEffect,
  type Dispatch,
  type SetStateAction,
  useRef,
  useCallback,
} from "react";
import SockJS from "sockjs-client/dist/sockjs";
import { Client, StompHeaders, type IFrame, type StompSubscription } from "@stomp/stompjs";

function getStorageValue<S>(key: string, initialState: S): S {
  const saved = localStorage.getItem(key);
  if (!saved) {
    return initialState;
  }
  try {
    const initial = JSON.parse(saved) as S;
    return initial;
  } catch (e) {
    console.error(e);
    return initialState;
  }
}

export function useLocalStorageState<S>(
  key: string,
  initialState: S,
): [S, Dispatch<SetStateAction<S>>, () => void] {
  const [value, setValue] = useState(() => {
    return getStorageValue(key, initialState);
  });

  useEffect(() => {
    // storing input name
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue, () => localStorage.removeItem(key)];
}

export const useWebSocketService = ({
  url,
  connectHeaders,
  onConnectCallback,
  onErrorCallback,
}: {
  url?: string;
  connectHeaders?: StompHeaders,
  onConnectCallback?: (
    subscribe: <T>(
      destination: string,
      callback: (body: T) => void,
    ) => StompSubscription | null,
  ) => void;
  onErrorCallback?: (frame: IFrame, disconnect: () => void) => void;
}) => {
  const stompClientRef = useRef<Client>(null);

  const subscribe = useCallback(
    <T>(
      destination: string,
      callback: (body: T) => void,
    ): StompSubscription | null => {
      if (!stompClientRef.current?.connected) return null;
      return stompClientRef.current.subscribe(destination, (message) => {
        if (message.body) callback(JSON.parse(message.body) as T);
      });
    },
    [],
  );

  const disconnect = useCallback(() => {
    stompClientRef.current?.deactivate();
  }, []);

  useEffect(() => {
    const stompClient = new Client({
      connectHeaders,
      webSocketFactory: () => new SockJS(url ?? window.location.origin + "/ws"),
      reconnectDelay: 5000,
      heartbeatIncoming: 1000,
      heartbeatOutgoing: 1000,
      debug: (str) => console.log("stomp-debug: ", str),
      onConnect: () => {
        onConnectCallback?.(subscribe);
      },
      onStompError: (frame) => onErrorCallback?.(frame, disconnect),
    });

    stompClient.activate();
    stompClientRef.current = stompClient;

    const handleBeforeUnload = () => {
      stompClient.deactivate();
    };
    window.addEventListener("beforeunload", handleBeforeUnload);

    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
      disconnect();
    };
  }, [onConnectCallback, onErrorCallback, url, subscribe, disconnect, connectHeaders]);

  const publish = useCallback(<T>(destination: string, body: T) => {
    const client = stompClientRef.current;
    if (!client?.connected) return;

    let bodyStr: string;
    if (typeof body === "string") {
      bodyStr = body;
    } else {
      bodyStr = JSON.stringify(body);
    }
    client.publish({ destination, body: bodyStr });
  }, []);

  return { stompClientRef, publish };
};
