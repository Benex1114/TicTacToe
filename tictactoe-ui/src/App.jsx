import { useEffect, useState } from "react";
// Backend address: set VITE_API_URL when deploying, defaults to the local backend
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
const API_BASE = `${API_URL.replace(/\/$/, "")}/api/games`;

const DIFFICULTIES = [
  { value: "EASY", label: "Easy" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HARD", label: "Hard" },
  { value: "IMPOSSIBLE", label: "Impossible" },
];

const MODES = [
  { value: "SINGLE_PLAYER", label: "Single player", description: "Play against the computer" },
  { value: "MULTI_PLAYER", label: "Two players", description: "Take turns on one screen" },
];

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
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
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
  const vsComputer = game ? Boolean(game.difficulty) : isSinglePlayer;
  const nameX = vsComputer ? "You" : "Player X";
  const nameO = vsComputer ? "Computer" : "Player O";
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
    // Ignore taps while a move is on its way, so fast double-taps can't send two moves
    if (!game || pendingMove || game.board[index] !== "_" || !game.currentPlayer) return;

    // Show the player's mark straight away instead of waiting for the server
    const previous = game;
    const optimisticBoard = [...game.board];
    optimisticBoard[index] = game.currentPlayer;
    setGame({ ...game, board: optimisticBoard });
    setPendingMove(true);

    try {
      const res = await fetch(`${API_BASE}/${game.gameId}/moves`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ cellIndex: index }),
      });

      const data = await res.json();
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

  const getWinningCells = () => {
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
    // The free server sleeps when unused, so the first request can take up to a minute
    if (loading) return { text: "Connecting to the game server… the first game can take up to a minute.", tone: "idle" };
    if (!game && !serverReady) return { text: "Waking up the game server… this can take up to a minute.", tone: "idle" };
    if (!game) return { text: "Server is ready.", tone: "idle" };
    switch (game.status) {
      case "X_WON":
        return { text: vsComputer ? "You win!" : "Player X wins!", tone: "x" };
      case "O_WON":
        return { text: vsComputer ? "Computer wins." : "Player O wins!", tone: "o" };
      case "DRAW":
        return { text: "It's a draw.", tone: "draw" };
      default:
        if (vsComputer) return pendingMove ? { text: "Computer is thinking…", tone: "o" } : { text: "Your turn", tone: "x" };
        return { text: `Player ${game.currentPlayer}'s turn`, tone: game.currentPlayer.toLowerCase() };
    }
  };

  const status = getStatus();
  const winner = game?.status === "X_WON" ? "X" : game?.status === "O_WON" ? "O" : null;
  const board = game ? game.board : EMPTY_BOARD;

  const sideClass = (mark) =>
    [
      "player",
      `player-${mark.toLowerCase()}`,
      game?.currentPlayer === mark ? "is-turn" : "",
      winner === mark ? "is-winner" : "",
    ].join(" ");

  const gameOver = game && game.status !== "IN_PROGRESS";

  return (
    <main className="page">
      <div className="app">
        {/* The title folds away during a game so the board gets the space */}
        <div className={`title-wrap${screen === "game" ? " is-hidden" : ""}`} aria-hidden={screen === "game"}>
          <h1 className="title title-pop">Tic‑Tac‑Toe</h1>
        </div>

        {screen === "menu" ? (
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
                  onClick={() => setGameMode(m.value)}
                >
                  <span className="mode-card-title">{m.label}</span>
                  <span className="mode-card-desc">{m.description}</span>
                </button>
              ))}
            </div>

            {isSinglePlayer && (
              <div className="menu-row">
                <span className="menu-label">Difficulty</span>
                <Segmented label="Difficulty" options={DIFFICULTIES} value={difficulty} onChange={setDifficulty} />
              </div>
            )}

            <button type="button" className="new-game-btn" onClick={startFromMenu} disabled={loading}>
              {loading ? "Starting…" : "Start game"}
            </button>

            <p className="status status-idle" role="status" aria-live="polite">
              {loading
                ? "Connecting to the game server… the first game can take up to a minute."
                : serverReady
                  ? "\u00a0"
                  : "Waking up the game server… this can take up to a minute."}
            </p>
          </section>
        ) : (
          /* ---------- Game: board only, no mode switching ---------- */
          <>
            {/* Player bar: scores and whose turn it is */}
            <section className="players" aria-label="Scores">
              <div className={sideClass("X")}>
                <span className="player-mark" aria-hidden="true">X</span>
                <span className="player-name">{nameX}</span>
                <span className="player-score">{scores.X}</span>
              </div>

              <div className="draws">
                <span className="draws-score">{scores.DRAW}</span>
                <span className="draws-label">draws</span>
              </div>

              <div className={sideClass("O")}>
                <span className="player-score">{scores.O}</span>
                <span className="player-name">
                  {nameO}
                  {vsComputer && <small className="player-sub">{difficultyLabel}</small>}
                </span>
                <span className="player-mark" aria-hidden="true">O</span>
              </div>
            </section>

            {/*Grid Layout*/}
            <div className="board">
              <svg key={game?.gameId ?? "idle"} className="board-lines" viewBox="0 0 300 300" aria-hidden="true">
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
                    disabled={!game || pendingMove || cell !== "_" || !game.currentPlayer}
                    aria-label={cell === "_" ? `Empty cell ${index + 1}` : `${cell} in cell ${index + 1}`}
                  >
                    {cell !== "_" && (
                      <span className={`cell-mark mark-${cell.toLowerCase()}`}>{cell}</span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            <p className={`status status-${status.tone}`} role="status" aria-live="polite">
              {status.text}
            </p>

            {/* Shown only when the game ends, so the mode can't change mid-game */}
            {gameOver && (
              <div className="end-actions">
                <button type="button" className="new-game-btn" onClick={createGame} disabled={loading}>
                  {loading ? "Starting…" : "Play again"}
                </button>
                <button type="button" className="text-btn" onClick={backToMenu}>
                  Back to menu
                </button>
              </div>
            )}
          </>
        )}

        {error && <p className="error" role="alert">{error}</p>}
      </div>
    </main>
  );
}
