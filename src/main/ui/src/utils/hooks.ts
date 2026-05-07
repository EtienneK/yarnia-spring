import { useState, useEffect, type Dispatch, type SetStateAction, useReducer, useRef, useCallback } from 'react'
import SockJS from 'sockjs-client/dist/sockjs';
import { Client, type IMessage } from '@stomp/stompjs';

function getStorageValue<S>(key: string, initialState: S): S {
  const saved = localStorage.getItem(key)
  if (!saved) {
    return initialState
  }
  try {
    const initial = JSON.parse(saved) as S
    return initial
  } catch (e) {
    console.error(e)
    return initialState
  }
}

export function useLocalStorageState<S> (key: string, initialState: S): [S, Dispatch<SetStateAction<S>>, () => void] {
  const [value, setValue] = useState(() => {
    return getStorageValue(key, initialState)
  })

  useEffect(() => {
    // storing input name
    localStorage.setItem(key, JSON.stringify(value))
  }, [key, value])

  return [value, setValue, () => localStorage.removeItem(key)]
};

// ========================================= STOMP and Websockets =========================================
// See: https://medium.com/front-end-world/a-complete-guide-to-using-stomp-js-and-sockjs-in-react-react-native-typescript-0d8bade60b48

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type SubscriptionCallback = (message: any) => void;

type State = {
  client: Client | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  subscriptions: Map<string, any>;
};

type Action =
  | {type: 'SET_CLIENT'; payload: Client}
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  | {type: 'ADD_SUBSCRIPTION'; payload: {destination: string; subscription: any}}
  | {type: 'REMOVE_SUBSCRIPTION'; payload: string}
  | {type: 'CLEAR_CLIENT'};

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case 'SET_CLIENT':
      return {...state, client: action.payload};
    case 'ADD_SUBSCRIPTION':
      return {...state, subscriptions: new Map(state.subscriptions).set(action.payload.destination, action.payload.subscription)};
    case 'REMOVE_SUBSCRIPTION':
      { const updatedSubscriptions = new Map(state.subscriptions);
      updatedSubscriptions.delete(action.payload);
      return {...state, subscriptions: updatedSubscriptions}; }
    case 'CLEAR_CLIENT':
      return {client: null, subscriptions: new Map()};
    default:
      return state;
  }
};

export const useWebSocketService = (
  webSocketUrl: string,
  onConnectCallback: () => void,
  onErrorCallback: (error: string) => void,
) => {
  const [state, dispatch] = useReducer(reducer, {
    client: null,
    subscriptions: new Map(),
  });

  const clientRef = useRef<Client | null>(null);
  const isConnected = useRef(false);

  useEffect(() => {
    clientRef.current = state.client;
  }, [state.client]);

  const connect = useCallback(() => {
    if (state.client || isConnected.current) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(webSocketUrl),
      debug: str => console.log('debugLog', str),
      reconnectDelay: 5000,
      heartbeatIncoming: 1000,
      heartbeatOutgoing: 1000,
      onConnect: () => {
        isConnected.current = true;
        console.log('WebSocket connected');
        onConnectCallback();
      },
      onStompError: error => {
        onErrorCallback(error.headers['message'] || 'Unknown error');
      },
    });

    client.activate();
    dispatch({type: 'SET_CLIENT', payload: client});
  }, [state.client, webSocketUrl, onConnectCallback, onErrorCallback]);

  const subscribe = useCallback(
    (destination: string, callback: SubscriptionCallback) => {
      const client = clientRef.current;
      if (!client || !isConnected.current) return;

      if (state.subscriptions.has(destination)) return;

      const subscription = client.subscribe(destination, (message: IMessage) => {
        if (message.body) callback(JSON.parse(message.body));
      });

      dispatch({type: 'ADD_SUBSCRIPTION', payload: {destination, subscription}});
    },
    [state.subscriptions],
  );

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const send = useCallback((destination: string, body: Record<string, any> = {}) => {
    const client = clientRef.current;
    if (!client || !isConnected.current) return;
    client.publish({destination, body: JSON.stringify(body)});
  }, []);

  const unsubscribe = useCallback((destination: string) => {
    const subscription = state.subscriptions.get(destination);
    if (subscription) {
      subscription.unsubscribe();
      dispatch({type: 'REMOVE_SUBSCRIPTION', payload: destination});
    }
  }, [state.subscriptions]);

  const disconnect = useCallback(() => {
    const client = clientRef.current;
    if (client && isConnected.current) {
      state.subscriptions.forEach(subscription => subscription.unsubscribe());
      client.deactivate();
      dispatch({type: 'CLEAR_CLIENT'});
      isConnected.current = false;
    }
  }, [state.subscriptions]);

  return { connect, subscribe, send, unsubscribe, disconnect };
};
