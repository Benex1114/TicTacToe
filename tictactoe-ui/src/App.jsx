import { useEffect, useRef, useState } from "react";
import { useOnlineGame } from "./useOnlineGame";
import { playSound, isMuted, setMuted } from "./sounds";
// Backend address: set VITE_API_URL when deploying, defaults to the local backend
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
const API_BASE = `${API_URL.replace(/\/$/, "")}/api/games`;
// Same server over WebSocket: http -> ws, https -> wss
const WS_URL = `${API_URL.replace(/\/$/, "").replace(/^http/, "ws")}/ws/game`;

const DIFFICULTIES = [
  { value: "EASY", label: "Easy", description: "Takes a win if it sees one, but never blocks you." },
  { value: "MEDIUM", label: "Medium", description: "Takes its wins and blocks yours. Otherwise it guesses." },
  { value: "HARD", label: "Hard", description: "Never misses a block, but sometimes slips. Spot it and set up a fork." },
  { value: "IMPOSSIBLE", label: "Impossible", description: "Plays perfectly. The best you can get is a draw." },
];

const MODES = [
  { value: "SINGLE_PLAYER", label: "Single player", description: "Play against the computer" },
  { value: "MULTI_PLAYER", label: "Two players", description: "Take turns on one screen" },
  { value: "ONLINE", label: "Online", description: "Play a friend on their own device" },
];

// Player names are remembered on this device (browser storage), so they're filled in next time.
// Storage can be unavailable (private windows, blocked cookies), so every access is guarded.
const NAMES_KEY = "tictactoe-names";
const EMPTY_NAMES = { solo: "", x: "", o: "", online: "" };
const MAX_NAME_LENGTH = 16;
const loadNames = () => {
  try {
    return { ...EMPTY_NAMES, ...JSON.parse(localStorage.getItem(NAMES_KEY) || "{}") };
  } catch {
    return EMPTY_NAMES;
  }
};

// One name box with an optional X or O beside it
function NameField({ mark, label, value, onChange, inputRef }) {
  return (
    <label className="name-field">
      {mark && (
        <span className="name-mark" aria-hidden="true">
          <Mark player={mark} />
        </span>
      )}
      <input
        className="name-input"
        value={value}
        onChange={(e) => onChange(e.target.value.slice(0, MAX_NAME_LENGTH))}
        placeholder={label}
        aria-label={label}
        maxLength={MAX_NAME_LENGTH}
        autoComplete="nickname"
        spellCheck="false"
        ref={inputRef}
      />
    </label>
  );
}

// Invite link for a room: this page with ?room=CODE on the end
const inviteLink = (code) => `${window.location.origin}${window.location.pathname}?room=${code}`;

const WIN_PATTERNS = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
];

const EMPTY_BOARD = Array(9).fill("_");

// Minimum time before the computer's reply appears, so your move and its move don't land at once
const AI_REPLY_DELAY_MS = 350;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// X and O drawn as SVG strokes so they can animate as if drawn by hand.
// pathLength="1" lets the CSS animate every stroke from 0 to 1, whatever its real length.
function Mark({ player, ghost = false }) {
  const className = `mark mark-${player.toLowerCase()}${ghost ? " mark-ghost" : ""}`;
  if (player === "X") {
    return (
      <svg className={className} viewBox="0 0 100 100" aria-hidden="true">
        <line className="mark-stroke" x1="24" y1="24" x2="76" y2="76" pathLength="1" />
        <line className="mark-stroke mark-stroke-2" x1="76" y1="24" x2="24" y2="76" pathLength="1" />
      </svg>
    );
  }
  return (
    <svg className={className} viewBox="0 0 100 100" aria-hidden="true">
      <circle className="mark-stroke" cx="50" cy="50" r="28" pathLength="1" transform="rotate(-90 50 50)" />
    </svg>
  );
}

// Line through the three winning cells, drawn like a pen stroke across the board.
// Board coordinates are 0-300 (same as the grid lines), so each cell's centre is 50, 150 or 250.
function WinSlash({ cells, winner }) {
  const centre = (i) => [50 + 100 * (i % 3), 50 + 100 * Math.floor(i / 3)];
  const [x1, y1] = centre(cells[0]);
  const [x2, y2] = centre(cells[2]);
  // Extend 35 units past each end so the line runs over the outer marks, not just to their centres
  const length = Math.hypot(x2 - x1, y2 - y1);
  const dx = ((x2 - x1) / length) * 35;
  const dy = ((y2 - y1) / length) * 35;
  return (
    <svg className={`win-slash slash-${winner.toLowerCase()}`} viewBox="0 0 300 300" aria-hidden="true">
      <line x1={x1 - dx} y1={y1 - dy} x2={x2 + dx} y2={y2 + dy} pathLength="1" />
    </svg>
  );
}

// Small blurred X's and O's drifting slowly in the background.
// Positions and timings come from a fixed formula, so they're the same on every render.
const BACKGROUND_MARKS = Array.from({ length: 22 }, (_, i) => {
  const r = (n) => ((Math.sin(i * 12.9898 + n * 78.233) * 43758.5453) % 1 + 1) % 1; // repeatable 0-1 values
  return {
    player: i % 2 === 0 ? "X" : "O",
    left: `${Math.round(r(1) * 92)}%`,
    top: `${Math.round(r(2) * 88)}%`,
    size: `${Math.round(28 + r(3) * 46)}px`,
    duration: `${Math.round(24 + r(4) * 30)}s`,
    delay: `-${Math.round(r(5) * 30)}s`, // negative delay: already mid-drift when the page opens
    dx: `${Math.round((r(6) - 0.5) * 160)}px`,
    dy: `${Math.round((r(7) - 0.5) * 140)}px`,
    rotate: `${Math.round((r(8) - 0.5) * 120)}deg`,
  };
});

function BackgroundMarks() {
  return (
    <div className="bg-marks" aria-hidden="true">
      {BACKGROUND_MARKS.map((m, i) => (
        <span
          key={i}
          className={`bg-mark bg-mark-${m.player.toLowerCase()}`}
          style={{
            left: m.left,
            top: m.top,
            width: m.size,
            height: m.size,
            animationDuration: m.duration,
            animationDelay: m.delay,
            "--dx": m.dx,
            "--dy": m.dy,
            "--rot": m.rotate,
          }}
        >
          <Mark player={m.player} />
        </span>
      ))}
    </div>
  );
}

// Reusable segmented control used for both the mode and difficulty pickers
function Segmented({ label, options, value, onChange }) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          className={`segment${value === o.value ? " active" : ""}`}
          onClick={() => {
            if (o.value !== value) playSound("tap");
            onChange(o.value);
          }}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

// Speaker icon for the sound on/off button (with waves when on, a cross when off)
function SpeakerIcon({ on }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path className="speaker-body" d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" />
      {on ? (
        <>
          <path d="M15.5 9a4 4 0 0 1 0 6" />
          <path d="M18 6.5a7.5 7.5 0 0 1 0 11" />
        </>
      ) : (
        <>
          <path d="M16 9.5l5 5" />
          <path d="M21 9.5l-5 5" />
        </>
      )}
    </svg>
  );
}

//App functions
export default function App() {
  const [scores, setScores] = useState({ X: 0, O: 0, DRAW: 0 });
  const [game, setGame] = useState(null);
  const [error, setError] = useState(null);
  const [gameMode, setGameMode] = useState("MULTI_PLAYER");
  const [difficulty, setDifficulty] = useState("MEDIUM");
  const [loading, setLoading] = useState(false);
  const [pendingMove, setPendingMove] = useState(false);
  const [serverReady, setServerReady] = useState(false);
  // "menu" = pick mode and difficulty, "game" = playing. Mode can only change on the menu.
  const [screen, setScreen] = useState("menu");

  // ---------- Online play ----------
  const online = useOnlineGame(WS_URL);
  const [joinCode, setJoinCode] = useState("");
  const [linkCopied, setLinkCopied] = useState(false);
  // A room code from an invite link (?room=K7QM), joined as soon as the server is awake
  const [codeFromLink, setCodeFromLink] = useState(() => new URLSearchParams(window.location.search).get("room"));
  const isOnline = gameMode === "ONLINE";
  const onlineActive = online.phase === "playing" || online.phase === "ended";

  // ---------- Player names ----------
  const [names, setNames] = useState(loadNames);
  const setName = (key) => (value) => setNames((n) => ({ ...n, [key]: value }));
  useEffect(() => {
    try {
      localStorage.setItem(NAMES_KEY, JSON.stringify(names));
    } catch {
      // storage not available: names just aren't remembered
    }
  }, [names]);
  // Opened an invite link without a saved name: ask for one before joining
  const [askNameToJoin, setAskNameToJoin] = useState(false);
  const onlineNameRef = useRef(null);
  // Put the cursor in the name box with the name selected, so typing replaces it
  useEffect(() => {
    if (askNameToJoin) onlineNameRef.current?.select();
  }, [askNameToJoin]);

  // Opening an invite link: switch to Online and remove ?room from the address,
  // so refreshing the page later doesn't try to join an old room
  useEffect(() => {
    if (!codeFromLink) return;
    setGameMode("ONLINE");
    setJoinCode(codeFromLink.toUpperCase());
    window.history.replaceState(null, "", window.location.pathname);
  }, [codeFromLink]);

  // Never join straight from a link: the code is filled in and the player confirms their name first.
  // (A name saved on this device may belong to someone else, e.g. the friend who sent the link.)
  useEffect(() => {
    if (codeFromLink) {
      setAskNameToJoin(true);
      setCodeFromLink(null);
    }
  }, [codeFromLink]);

  // A new online match (not a rematch) starts with fresh scores
  const previousPhase = useRef(online.phase);
  useEffect(() => {
    if (online.phase === "playing" && previousPhase.current !== "playing" && previousPhase.current !== "ended") {
      setScores({ X: 0, O: 0, DRAW: 0 });
    }
    previousPhase.current = online.phase;
  }, [online.phase]);

  // Count each finished online game once
  const scoredGameId = useRef(null);
  useEffect(() => {
    const g = online.game;
    if (g && g.status !== "IN_PROGRESS" && scoredGameId.current !== g.gameId) {
      scoredGameId.current = g.gameId;
      const key = g.status === "DRAW" ? "DRAW" : g.status[0]; // "X_WON" -> "X"
      setScores((s) => ({ ...s, [key]: s[key] + 1 }));
    }
  }, [online.game]);

  const copyInviteLink = async () => {
    try {
      await navigator.clipboard.writeText(inviteLink(online.code));
      setLinkCopied(true);
      setTimeout(() => setLinkCopied(false), 2000);
    } catch {
      setLinkCopied(false);
    }
  };

  const shareInviteLink = () => {
    navigator.share?.({ title: "Tic-Tac-Toe", text: `Play Tic-Tac-Toe with me! Room code ${online.code}`, url: inviteLink(online.code) }).catch(() => {});
  };

  const joinRoom = (event) => {
    event.preventDefault();
    if (joinCode.trim().length === 4) {
      setAskNameToJoin(false);
      online.join(joinCode, names.online);
    }
  };

  const quitOnline = () => {
    online.leave();
    setScreen("menu");
  };

  // Quit at any time: the first tap asks for a second tap, so a stray touch doesn't end the game
  const [confirmQuit, setConfirmQuit] = useState(false);
  const quitTimer = useRef(null);
  const quitNow = () => {
    clearTimeout(quitTimer.current);
    setConfirmQuit(false);
    if (onlineActive) quitOnline();
    else backToMenu();
  };
  const tapQuit = () => {
    if (confirmQuit) {
      quitNow();
      return;
    }
    setConfirmQuit(true);
    clearTimeout(quitTimer.current);
    quitTimer.current = setTimeout(() => setConfirmQuit(false), 3000);
  };
  useEffect(() => () => clearTimeout(quitTimer.current), []);

  // Wake the backend as soon as the page opens. On Render's free plan it sleeps
  // when unused and takes about a minute to start, so this hides most of the wait.
  useEffect(() => {
    let cancelled = false;
    const ping = async () => {
      try {
        const res = await fetch(`${API_URL.replace(/\/$/, "")}/api/health`);
        if (res.ok && !cancelled) setServerReady(true);
        else if (!cancelled) setTimeout(ping, 5000);
      } catch {
        if (!cancelled) setTimeout(ping, 5000);
      }
    };
    ping();
    return () => {
      cancelled = true;
    };
  }, []);

  const isSinglePlayer = gameMode === "SINGLE_PLAYER";
  // Names follow the game being played (it has a difficulty only in single player)
  const vsComputer = !onlineActive && (game ? Boolean(game.difficulty) : isSinglePlayer);
  // Names come from the server online (so both players see the same ones), otherwise from the menu boxes
  const nameX = onlineActive
    ? online.names?.X ?? "Player X"
    : vsComputer
      ? names.solo.trim() || "You"
      : names.x.trim() || "Player X";
  const nameO = onlineActive
    ? online.names?.O ?? "Player O"
    : vsComputer
      ? "Computer"
      : names.o.trim() || "Player O";
  // The mark that belongs to the person holding this device (none when two people share one screen)
  const myMark = onlineActive ? online.you : vsComputer ? "X" : null;
  const difficultyLabel = DIFFICULTIES.find((d) => d.value === (game?.difficulty ?? difficulty))?.label;

  // Start a fresh session from the menu: new scores, then the first game
  const startFromMenu = async () => {
    setScores({ X: 0, O: 0, DRAW: 0 });
    const started = await createGame();
    if (started) setScreen("game");
  };

  const backToMenu = () => {
    setGame(null);
    setError(null);
    setScreen("menu");
  };

  const createGame = async () => {
    // Difficulty is only sent for single player; the backend ignores it otherwise
    const body = isSinglePlayer ? { gameMode, difficulty } : { gameMode };
    setLoading(true);
    try {
      const res = await fetch(API_BASE, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      const data = await res.json();
      setError(null);
      setServerReady(true);
      setGame(data);
      return true;
    } catch {
      setError("Can't reach the game server. Check that the backend is running, then try again.");
      return false;
    } finally {
      setLoading(false);
    }
  };

  const makeMove = async (index) => {
    if (onlineActive) {
      online.move(index); // sent over the WebSocket; the hook shows it straight away
      return;
    }
    // Ignore taps while a move is on its way, so fast double-taps can't send two moves
    if (!game || pendingMove || game.board[index] !== "_" || !game.currentPlayer) return;

    // Show the player's mark straight away instead of waiting for the server
    const previous = game;
    const optimisticBoard = [...game.board];
    optimisticBoard[index] = game.currentPlayer;
    setGame({ ...game, board: optimisticBoard });
    setPendingMove(true);
    const startedAt = Date.now();

    try {
      const res = await fetch(`${API_BASE}/${game.gameId}/moves`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ cellIndex: index }),
      });

      const data = await res.json();
      // Against the computer, hold its reply briefly so your X finishes drawing first
      const aiReplied = vsComputer && data.board?.filter((c) => c === "O").length > game.board.filter((c) => c === "O").length;
      if (res.ok && aiReplied) {
        await wait(Math.max(0, AI_REPLY_DELAY_MS - (Date.now() - startedAt)));
      }
      if (!res.ok) {
        setGame(previous); // undo the optimistic mark
        setError(data.message);
        return;
      }
      setError(null);
      setGame(data);
      if (data.status !== "IN_PROGRESS") {
        updateScores(data.status);
      }
    } catch {
      setGame(previous);
      setError("Lost connection to the game server. Try that move again.");
    } finally {
      setPendingMove(false);
    }
  };

  // The game on screen: the online one when playing online, otherwise the local/computer one
  const shownGame = onlineActive ? online.game : game;
  const movePending = onlineActive ? online.pending : pendingMove;
  const myTurn = !onlineActive || shownGame?.currentPlayer === online.you;

  const getWinningCells = () => {
    const game = shownGame;
    if (!game || !game.status.includes("_WON")) return [];
    const winner = game.status.startsWith("X") ? "X" : "O";

    for (const pattern of WIN_PATTERNS) {
      if (pattern.every((i) => game.board[i] === winner)) {
        return pattern;
      }
    }
    return [];
  };

  const winningCells = getWinningCells();

  const updateScores = (status) => {
    if (status === "X_WON")
      setScores((s) => ({ ...s, X: s.X + 1 }));
    else if (status === "O_WON")
      setScores((s) => ({ ...s, O: s.O + 1 }));
    else if (status === "DRAW")
      setScores((s) => ({ ...s, DRAW: s.DRAW + 1 }));
  };

  // One line under the board that always says what's happening
  const getStatus = () => {
    if (onlineActive) return getOnlineStatus();
    // The free server sleeps when unused, so the first request can take up to a minute
    if (loading) return { text: "Connecting to the game server… the first game can take up to a minute.", tone: "idle" };
    if (!game && !serverReady) return { text: "Waking up the game server… this can take up to a minute.", tone: "idle" };
    if (!game) return { text: "Server is ready.", tone: "idle" };
    switch (game.status) {
      case "X_WON":
        return { text: vsComputer ? "You win!" : `${nameX} wins!`, tone: "x" };
      case "O_WON":
        return { text: vsComputer ? "Computer wins." : `${nameO} wins!`, tone: "o" };
      case "DRAW":
        return { text: "It's a draw.", tone: "draw" };
      default:
        if (vsComputer) return pendingMove ? { text: "Computer is thinking…", tone: "o" } : { text: "Your turn", tone: "x" };
        return { text: `${game.currentPlayer === "X" ? nameX : nameO}'s turn`, tone: game.currentPlayer.toLowerCase() };
    }
  };

  function getOnlineStatus() {
    const g = online.game;
    const friend = online.you === "X" ? nameO : nameX;
    if (online.phase === "ended") {
      return {
        text: online.endReason === "opponent-left" ? `${friend} left the game.` : "Connection lost, so this game has ended.",
        tone: "idle",
      };
    }
    const you = online.you;
    switch (g.status) {
      case "X_WON":
      case "O_WON":
        return g.status.startsWith(you) ? { text: "You win!", tone: you.toLowerCase() } : { text: `${friend} wins.`, tone: g.status[0].toLowerCase() };
      case "DRAW":
        return { text: "It's a draw.", tone: "draw" };
      default:
        return g.currentPlayer === you
          ? { text: "Your turn", tone: you.toLowerCase() }
          : { text: `${friend}'s turn`, tone: g.currentPlayer.toLowerCase() };
    }
  }

  const status = getStatus();
  const winner = shownGame?.status === "X_WON" ? "X" : shownGame?.status === "O_WON" ? "O" : null;
  const board = shownGame ? shownGame.board : EMPTY_BOARD;

  const sideClass = (mark) =>
    [
      "player",
      `player-${mark.toLowerCase()}`,
      shownGame?.currentPlayer === mark ? "is-turn" : "",
      winner === mark ? "is-winner" : "",
    ].join(" ");

  const gameOver = Boolean(shownGame && shownGame.status !== "IN_PROGRESS") || online.phase === "ended";

  // ---------- Sound effects ----------
  const [soundOn, setSoundOn] = useState(() => !isMuted());
  const toggleSound = () => {
    setMuted(soundOn);
    setSoundOn(!soundOn);
    if (!soundOn) playSound("tap"); // a little tick to show sound is back on
  };

  // A stroke sound for every new mark on the board: yours straight away (with the optimistic update),
  // the computer's or your friend's when it arrives. Compared with the last board we played sounds for,
  // so a server confirming your move, or a move being undone, makes no extra sound.
  const heardBoard = useRef({ id: null, board: EMPTY_BOARD });
  useEffect(() => {
    if (!shownGame) {
      heardBoard.current = { id: null, board: EMPTY_BOARD };
      return;
    }
    const before = heardBoard.current.id === shownGame.gameId ? heardBoard.current.board : EMPTY_BOARD;
    const added = shownGame.board.filter((cell, i) => cell !== "_" && before[i] === "_");
    added.forEach((mark, i) => playSound(mark.toLowerCase(), i * 0.35));
    heardBoard.current = { id: shownGame.gameId, board: shownGame.board };
  }, [shownGame]);

  // The result, once per game, just after the last mark's sound.
  // With your own mark (vs the computer, online) it's a win or a loss; on a shared screen any win is a win.
  const heardResult = useRef(null);
  useEffect(() => {
    if (!shownGame || shownGame.status === "IN_PROGRESS" || heardResult.current === shownGame.gameId) return;
    heardResult.current = shownGame.gameId;
    const result = shownGame.status === "DRAW" ? "draw" : myMark && !shownGame.status.startsWith(myMark) ? "lose" : "win";
    playSound(result, 0.3);
  }, [shownGame, myMark]);

  // Online: your friend joined, left, or asked for a rematch
  const heardPhase = useRef(online.phase);
  useEffect(() => {
    const before = heardPhase.current;
    heardPhase.current = online.phase;
    if (online.phase === "playing" && (before === "waiting" || before === "connecting")) playSound("join");
    if (online.phase === "ended" && before === "playing") playSound("left");
  }, [online.phase]);
  useEffect(() => {
    if (online.rematch.theirs && !online.rematch.mine) playSound("nudge");
  }, [online.rematch.theirs, online.rematch.mine]);

  // Which screen to show: online play drives it while active, otherwise the menu/game state
  const view = online.phase === "waiting" ? "waiting" : onlineActive ? "game" : screen;

  // ---------- Player cards (wide screens) ----------
  // While your move is on its way, the turn has really passed to the other side,
  // so their card lights up straight away instead of after the server replies
  const other = (mark) => (mark === "X" ? "O" : "X");
  const inProgress = shownGame?.status === "IN_PROGRESS" && online.phase !== "ended";
  const turn = inProgress ? (movePending ? other(shownGame.currentPlayer) : shownGame.currentPlayer) : null;

  const cardStatus = (mark) => {
    const name = mark === "X" ? nameX : nameO;
    if (winner === mark) return "Wins!";
    if (shownGame?.status === "DRAW") return "Draw";
    if (turn !== mark) return "\u00a0";
    if (mark === myMark) return "Your turn";
    if (vsComputer) return movePending ? "Thinking…" : "Computer's turn";
    return `${name}'s turn`;
  };

  const cardClass = (mark) =>
    [
      "player-card",
      `card-${mark.toLowerCase()}`,
      turn === mark ? "is-turn" : "",
      turn && turn !== mark ? "is-dim" : "",
      winner === mark ? "is-winner" : "",
      winner && winner !== mark ? "is-dim" : "",
    ].join(" ");

  const playerCard = (mark) => (
    <aside className={cardClass(mark)} aria-label={`${mark === "X" ? nameX : nameO}, ${scores[mark]} wins`}>
      <span className="card-mark" aria-hidden="true"><Mark player={mark} /></span>
      <span className="card-name">{mark === "X" ? nameX : nameO}</span>
      {mark === "O" && vsComputer && <span className="card-sub">{difficultyLabel}</span>}
      {/* Your own card says "You" under your name (when you've typed one) */}
      {mark === myMark && (mark === "X" ? nameX : nameO) !== "You" && <span className="card-sub">You</span>}
      <span className="card-score">{scores[mark]}</span>
      <span className="card-score-label">{scores[mark] === 1 ? "win" : "wins"}</span>
      <span className="card-status">{cardStatus(mark)}</span>
    </aside>
  );

  return (
    <main className="page">
      <BackgroundMarks />

      {/* Sound on/off, in the corner on every screen (remembered on this device) */}
      <button
        type="button"
        className={`sound-toggle${soundOn ? "" : " is-off"}`}
        onClick={toggleSound}
        aria-label={soundOn ? "Mute sounds" : "Turn sounds on"}
        title={soundOn ? "Mute sounds" : "Turn sounds on"}
      >
        <SpeakerIcon on={soundOn} />
      </button>

      <div className={`app${view === "game" ? " is-game" : ""}`}>
        {/* The title folds away during a game so the board gets the space */}
        <div className={`title-wrap${view === "game" ? " is-hidden" : ""}`} aria-hidden={view === "game"}>
          <h1 className="title title-pop">Tic‑Tac‑Toe</h1>
        </div>

        {view === "waiting" ? (
          /* ---------- Waiting for a friend to join an online room ---------- */
          <section className="waiting" aria-label="Waiting for your friend">
            <span className="menu-label">Room code</span>
            <p className="room-code" aria-label={`Room code ${online.code.split("").join(" ")}`}>{online.code}</p>
            <p className="waiting-hint">Send your friend the code, or the invite link.</p>
            <div className="invite-actions">
              <button type="button" className="new-game-btn" onClick={copyInviteLink}>
                {linkCopied ? "Link copied" : "Copy invite link"}
              </button>
              {typeof navigator.share === "function" && (
                <button type="button" className="secondary-btn" onClick={shareInviteLink}>
                  Share
                </button>
              )}
            </div>
            <p className="status status-idle" role="status" aria-live="polite">Waiting for your friend to join…</p>
            <button type="button" className="text-btn" onClick={quitOnline}>
              Cancel
            </button>
          </section>
        ) : view === "menu" ? (
          /* ---------- Menu: choose how to play ---------- */
          <section className="menu" aria-label="Game setup">
            <div className="mode-cards" role="radiogroup" aria-label="Game mode">
              {MODES.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  role="radio"
                  aria-checked={gameMode === m.value}
                  className={`mode-card${gameMode === m.value ? " active" : ""}`}
                  onClick={() => {
                    if (m.value !== gameMode) playSound("tap");
                    setGameMode(m.value);
                  }}
                >
                  <span className="mode-card-title">{m.label}</span>
                  <span className="mode-card-desc">{m.description}</span>
                </button>
              ))}
            </div>

            {/* Names: shown on the cards, turns and results */}
            <div className={`name-fields${gameMode === "MULTI_PLAYER" ? " is-pair" : ""}`}>
              {gameMode === "MULTI_PLAYER" ? (
                <>
                  <NameField mark="X" label="Player X name" value={names.x} onChange={setName("x")} />
                  <NameField mark="O" label="Player O name" value={names.o} onChange={setName("o")} />
                </>
              ) : isSinglePlayer ? (
                <NameField mark="X" label="Your name" value={names.solo} onChange={setName("solo")} />
              ) : (
                <NameField label="Your name (your friend sees this)" value={names.online} onChange={setName("online")} inputRef={onlineNameRef} />
              )}
            </div>

            {isSinglePlayer && (
              <div className="menu-row">
                <span className="menu-label">Difficulty</span>
                <Segmented label="Difficulty" options={DIFFICULTIES} value={difficulty} onChange={setDifficulty} />
                {/* What the selected level does, so players know what they're up against */}
                <p className="difficulty-desc" aria-live="polite">
                  {DIFFICULTIES.find((d) => d.value === difficulty)?.description}
                </p>
              </div>
            )}

            {isOnline ? (
              /* Online: create a room, or join a friend's with their code */
              <div className="online-panel">
                <button type="button" className="new-game-btn" onClick={() => online.create(names.online)} disabled={!serverReady || online.phase === "connecting"}>
                  {online.phase === "connecting" ? "Connecting…" : "Create a room"}
                </button>
                <span className="online-or">or join a friend</span>
                <form className="join-form" onSubmit={joinRoom}>
                  <input
                    className="code-input"
                    value={joinCode}
                    onChange={(e) => setJoinCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 4))}
                    placeholder="CODE"
                    aria-label="Room code"
                    autoCapitalize="characters"
                    autoComplete="off"
                    spellCheck="false"
                    inputMode="text"
                  />
                  <button type="submit" className="secondary-btn" disabled={!serverReady || joinCode.length !== 4 || online.phase === "connecting"}>
                    Join
                  </button>
                </form>
                {askNameToJoin && <p className="join-hint">You've been invited to room {joinCode}. Check your name above, then tap Join.</p>}
              </div>
            ) : (
              <button type="button" className="new-game-btn" onClick={startFromMenu} disabled={loading}>
                {loading ? "Starting…" : "Start game"}
              </button>
            )}

            <p className="status status-idle" role="status" aria-live="polite">
              {loading
                ? "Connecting to the game server… the first game can take up to a minute."
                : serverReady
                  ? "\u00a0"
                  : "Waking up the game server… this can take up to a minute."}
            </p>
          </section>
        ) : (
          /* ---------- Game: player cards either side of the board on wide screens ---------- */
          <div className="game-layout">
            {playerCard("X")}

            <div className="game-main">
              {/* Quit any time during a game (hidden once it ends, where Quit sits next to Restart) */}
              <div className={`game-topbar${gameOver ? " is-hidden" : ""}`}>
                <button type="button" className={`text-btn quit-btn${confirmQuit ? " is-confirming" : ""}`} onClick={tapQuit} disabled={gameOver}>
                  {confirmQuit ? "Tap again to quit" : "Quit"}
                </button>
              </div>

              {/* Player bar: scores and whose turn it is */}
              <section className="players" aria-label="Scores">
                <div className={sideClass("X")}>
                  <span className="player-mark" aria-hidden="true"><Mark player="X" /></span>
                  <span className="player-name"><span className="player-name-text">{nameX}</span></span>
                  <span className="player-score">{scores.X}</span>
                </div>

                <div className="draws">
                  <span className="draws-score">{scores.DRAW}</span>
                  <span className="draws-label">draws</span>
                </div>

                <div className={sideClass("O")}>
                  <span className="player-score">{scores.O}</span>
                  <span className="player-name">
                    <span className="player-name-text">{nameO}</span>
                    {vsComputer && <small className="player-sub">{difficultyLabel}</small>}
                  </span>
                  <span className="player-mark" aria-hidden="true"><Mark player="O" /></span>
                </div>
              </section>

              {/* Wide screens: the draw count sits above the board (the cards show each player's wins) */}
              <p className="draw-count">{scores.DRAW} {scores.DRAW === 1 ? "draw" : "draws"}</p>

              {/*Grid Layout*/}
              <div className={`board${winningCells.length ? " has-winner" : ""}`}>
                <svg key={shownGame?.gameId ?? "idle"} className="board-lines" viewBox="0 0 300 300" aria-hidden="true">
                  {/* Vertical lines */}
                  <line x1="100" y1="8" x2="100" y2="292" />
                  <line x1="200" y1="8" x2="200" y2="292" />

                  {/* Horizontal lines */}
                  <line x1="8" y1="100" x2="292" y2="100" />
                  <line x1="8" y1="200" x2="292" y2="200" />
                </svg>

                {/* Click & render layer */}
                <div className="cells">
                  {board.map((cell, index) => (
                    <button
                      key={index}
                      type="button"
                      className={`cell${winningCells.includes(index) ? " is-winning" : ""}`}
                      onClick={() => makeMove(index)}
                      disabled={!shownGame || movePending || !myTurn || online.phase === "ended" || cell !== "_" || !shownGame.currentPlayer}
                      aria-label={cell === "_" ? `Empty cell ${index + 1}` : `${cell} in cell ${index + 1}`}
                    >
                      {cell !== "_" ? (
                        <Mark player={cell} />
                      ) : (
                        // Faint preview of whose mark would go here (desktop hover only)
                        shownGame?.currentPlayer && !movePending && myTurn && <Mark player={shownGame.currentPlayer} ghost />
                      )}
                    </button>
                  ))}
                </div>

                {/* Slash through the winning line */}
                {winningCells.length > 0 && <WinSlash key={shownGame.gameId} cells={winningCells} winner={winner} />}
              </div>

              <p className={`status status-${status.tone}${gameOver ? " status-result" : " status-turn"}`} role="status" aria-live="polite">
                {status.text}
              </p>

              {/* Always takes up its space so the board doesn't jump; only visible when the game ends */}
              <div className={`end-actions${gameOver ? " is-visible" : ""}`} aria-hidden={!gameOver}>
                {/* Restart: same mode and difficulty, score kept */}
                {!onlineActive && (
                  <button type="button" className="new-game-btn" onClick={createGame} disabled={loading || !gameOver}>
                    {loading ? "Starting…" : "Restart"}
                  </button>
                )}
                {/* Online: a rematch starts when both players ask (hidden if the friend has gone) */}
                {onlineActive && online.phase === "playing" && (
                  <button type="button" className="new-game-btn" onClick={online.requestRematch} disabled={!gameOver || online.rematch.mine}>
                    {online.rematch.mine ? "Waiting…" : online.rematch.theirs ? "Accept rematch" : "Rematch"}
                  </button>
                )}
                {/* Quit: back to the menu, score resets on the next start */}
                <button type="button" className="secondary-btn" onClick={quitNow} disabled={!gameOver}>
                  Quit
                </button>
              </div>
            </div>

            {playerCard("O")}
          </div>
        )}

        {(isOnline ? online.error : error) && (
          <p className="error" role="alert">{isOnline ? online.error : error}</p>
        )}
      </div>
    </main>
  );
}
