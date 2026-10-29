import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Online two-player games over a WebSocket.
 *
 * Keeps one connection to the backend's /ws/game endpoint and turns its messages into state:
 *   phase: "idle"       not playing online
 *          "connecting" creating or joining a room
 *          "waiting"    room created, waiting for a friend to join
 *          "playing"    a game is on (or just finished)
 *          "ended"      the friend left or the connection dropped
 *   code, you ("X" or "O"), game (same shape as the REST API), error, pending (a move is on its way)
 *   rematch: { mine, theirs } - who has asked for another game after one ends
 *
 * Actions: create(), join(code), move(cellIndex), requestRematch(), leave()
 */
const NO_REMATCH = { mine: false, theirs: false };
const INITIAL = { phase: "idle", code: null, you: null, game: null, error: null, pending: false, endReason: null, rematch: NO_REMATCH };

// A tiny message every 25 s so hosting proxies don't close a quiet connection (e.g. while waiting for a friend)
const KEEP_ALIVE_MS = 25_000;

export function useOnlineGame(wsUrl) {
  const [state, setState] = useState(INITIAL);
  const socketRef = useRef(null);
  const queueRef = useRef([]); // messages waiting for the connection to open
  const confirmedGameRef = useRef(null); // last game the server sent, to undo a rejected move

  const handleMessage = useCallback((event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch {
      return;
    }
    switch (message.type) {
      case "ROOM_CREATED":
        setState((s) => ({ ...s, phase: "waiting", code: message.code, you: message.you, error: null }));
        break;
      case "GAME_STARTED":
        confirmedGameRef.current = message.game;
        setState((s) => ({ ...s, phase: "playing", code: message.code, you: message.you, game: message.game, error: null, pending: false, rematch: NO_REMATCH }));
        break;
      case "REMATCH_REQUESTED":
        setState((s) => ({ ...s, rematch: { ...s.rematch, theirs: true } }));
        break;
      case "GAME_UPDATE":
        confirmedGameRef.current = message.game;
        setState((s) => ({ ...s, game: message.game, error: null, pending: false }));
        break;
      case "OPPONENT_LEFT":
        setState((s) => ({ ...s, phase: "ended", endReason: "opponent-left", pending: false }));
        break;
      case "ERROR":
        // Undo an optimistic move the server refused, and show why
        setState((s) => ({ ...s, error: message.message, pending: false, game: confirmedGameRef.current ?? s.game, phase: s.phase === "connecting" ? "idle" : s.phase }));
        break;
      default:
        break;
    }
  }, []);

  // Opens the connection if needed; messages sent before it opens are queued
  const send = useCallback(
    (message) => {
      let socket = socketRef.current;
      if (!socket || socket.readyState === WebSocket.CLOSING || socket.readyState === WebSocket.CLOSED) {
        socket = new WebSocket(wsUrl);
        socketRef.current = socket;
        socket.onopen = () => {
          queueRef.current.forEach((m) => socket.send(m));
          queueRef.current = [];
          socket.keepAlive = setInterval(() => {
            if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "PING" }));
          }, KEEP_ALIVE_MS);
        };
        socket.onmessage = handleMessage;
        socket.onclose = () => {
          clearInterval(socket.keepAlive);
          if (socketRef.current !== socket) return; // an old connection we already replaced
          socketRef.current = null;
          queueRef.current = [];
          setState((s) => {
            if (s.phase === "idle" || s.phase === "ended") return s;
            // Never got into a room: back to the menu with a clear message
            if (s.phase === "connecting") {
              return { ...INITIAL, error: "Couldn't connect to the game server. Check your internet and try again." };
            }
            if (s.phase === "waiting") {
              return { ...INITIAL, error: "Lost connection while waiting, so the room was closed. Create a new one to try again." };
            }
            return { ...s, phase: "ended", endReason: "connection-lost", pending: false };
          });
        };
      }
      const text = JSON.stringify(message);
      if (socket.readyState === WebSocket.OPEN) socket.send(text);
      else queueRef.current.push(text);
    },
    [wsUrl, handleMessage]
  );

  const create = useCallback(() => {
    setState({ ...INITIAL, phase: "connecting" });
    send({ type: "CREATE" });
  }, [send]);

  const join = useCallback(
    (code) => {
      setState({ ...INITIAL, phase: "connecting" });
      send({ type: "JOIN", code: code.trim().toUpperCase() });
    },
    [send]
  );

  // Latest state for event handlers, plus a flag set the instant a move is sent,
  // so two quick taps can't both get through before React re-renders
  const stateRef = useRef(state);
  const movingRef = useRef(false);
  useEffect(() => {
    stateRef.current = state;
    if (!state.pending) movingRef.current = false;
  }, [state]);

  const move = useCallback(
    (cellIndex) => {
      const s = stateRef.current;
      const game = s.game;
      if (!game || movingRef.current || game.status !== "IN_PROGRESS" || game.currentPlayer !== s.you || game.board[cellIndex] !== "_") {
        return;
      }
      movingRef.current = true;
      send({ type: "MOVE", cellIndex });
      // Show your mark straight away; the server's GAME_UPDATE confirms it (or ERROR undoes it)
      const board = [...game.board];
      board[cellIndex] = s.you;
      setState((current) => ({ ...current, pending: true, game: { ...game, board } }));
    },
    [send]
  );

  // Ask for another game; it starts when your friend asks too (or straight away if they already did)
  const requestRematch = useCallback(() => {
    const s = stateRef.current;
    if (s.phase !== "playing" || !s.game || s.game.status === "IN_PROGRESS" || s.rematch.mine) return;
    send({ type: "REMATCH" });
    setState((current) => ({ ...current, rematch: { ...current.rematch, mine: true } }));
  }, [send]);

  // Closing the connection is enough: the server treats it as leaving and tells the friend
  const leave = useCallback(() => {
    const socket = socketRef.current;
    socketRef.current = null;
    queueRef.current = [];
    if (socket) {
      clearInterval(socket.keepAlive);
      socket.close();
    }
    confirmedGameRef.current = null;
    setState(INITIAL);
  }, []);

  // Close the connection if the page or component goes away
  useEffect(() => () => socketRef.current?.close(), []);

  return { ...state, create, join, move, requestRematch, leave };
}
