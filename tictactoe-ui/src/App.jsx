import { useEffect, useState } from "react";
// Backend address: set VITE_API_URL when deploying, defaults to the local backend
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
const API_BASE = `${API_URL.replace(/\/$/, "")}/api/games`;

const DIFFICULTIES = [
  { value: "EASY", label: "Easy", description: "Takes a win if it sees one, but never blocks you." },
  { value: "MEDIUM", label: "Medium", description: "Takes its wins and blocks yours. Otherwise it guesses." },
  { value: "HARD", label: "Hard", description: "Never misses a block, but sometimes slips. Spot it and set up a fork." },
  { value: "IMPOSSIBLE", label: "Impossible", description: "Plays perfectly. The best you can get is a draw." },
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
                {/* What the selected level does, so players know what they're up against */}
                <p className="difficulty-desc" aria-live="polite">
                  {DIFFICULTIES.find((d) => d.value === difficulty)?.description}
                </p>
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
                <span className="player-mark" aria-hidden="true"><Mark player="X" /></span>
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
                <span className="player-mark" aria-hidden="true"><Mark player="O" /></span>
              </div>
            </section>

            {/*Grid Layout*/}
            <div className={`board${winningCells.length ? " has-winner" : ""}`}>
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
                    {cell !== "_" ? (
                      <Mark player={cell} />
                    ) : (
                      // Faint preview of whose mark would go here (desktop hover only)
                      game?.currentPlayer && !pendingMove && <Mark player={game.currentPlayer} ghost />
                    )}
                  </button>
                ))}
              </div>

              {/* Slash through the winning line */}
              {winningCells.length > 0 && <WinSlash key={game.gameId} cells={winningCells} winner={winner} />}
            </div>

            <p className={`status status-${status.tone}${gameOver ? " status-result" : ""}`} role="status" aria-live="polite">
              {status.text}
            </p>

            {/* Always takes up its space so the board doesn't jump; only visible when the game ends */}
            <div className={`end-actions${gameOver ? " is-visible" : ""}`} aria-hidden={!gameOver}>
              {/* Restart: same mode and difficulty, score kept */}
              <button type="button" className="new-game-btn" onClick={createGame} disabled={loading || !gameOver}>
                {loading ? "Starting…" : "Restart"}
              </button>
              {/* Quit: back to the menu, score resets on the next start */}
              <button type="button" className="secondary-btn" onClick={backToMenu} disabled={!gameOver}>
                Quit
              </button>
            </div>
          </>
        )}

        {error && <p className="error" role="alert">{error}</p>}
      </div>
    </main>
  );
}
