import { useState } from "react";
const API_BASE = "http://localhost:8080/api/games";

const DIFFICULTIES = [
  { value: "EASY", label: "Easy" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HARD", label: "Hard" },
  { value: "IMPOSSIBLE", label: "Impossible" },
];

const MODES = [
  { value: "SINGLE_PLAYER", label: "Single player" },
  { value: "MULTI_PLAYER", label: "Two players" },
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

  const isSinglePlayer = gameMode === "SINGLE_PLAYER";
  // Names follow the game being played (it has a difficulty only in single player)
  const vsComputer = game ? Boolean(game.difficulty) : isSinglePlayer;
  const nameX = vsComputer ? "You" : "Player X";
  const nameO = vsComputer ? "Computer" : "Player O";
  const difficultyLabel = DIFFICULTIES.find((d) => d.value === (game?.difficulty ?? difficulty))?.label;

  const changeMode = (mode) => {
    if (mode === gameMode) return;
    setGameMode(mode);
    setScores({ X: 0, O: 0, DRAW: 0 }); // scores from different modes shouldn't mix
    setGame(null);
  };

  const createGame = async () => {
    // Difficulty is only sent for single player; the backend ignores it otherwise
    const body = isSinglePlayer ? { gameMode, difficulty } : { gameMode };
    try {
      const res = await fetch(API_BASE, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      const data = await res.json();
      setError(null);
      setGame(data);
    } catch {
      setError("Can't reach the game server. Make sure the backend is running on port 8080.");
    }
  };

  const makeMove = async (index) => {
    if (!game || game.board[index] !== "_" || !game.currentPlayer) return;

    const res = await fetch(`${API_BASE}/${game.gameId}/moves`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cellIndex: index }),
    });

    const data = await res.json();
    if (!res.ok) {
      setError(data.message);
      return;
    }
    setGame(data);
    if (data.status !== "IN_PROGRESS") {
      updateScores(data.status);
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
    if (!game) return { text: "Pick a mode, then start a new game.", tone: "idle" };
    switch (game.status) {
      case "X_WON":
        return { text: vsComputer ? "You win!" : "Player X wins!", tone: "x" };
      case "O_WON":
        return { text: vsComputer ? "Computer wins." : "Player O wins!", tone: "o" };
      case "DRAW":
        return { text: "It's a draw.", tone: "draw" };
      default:
        if (vsComputer) return { text: "Your turn", tone: "x" };
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

  return (
    <main className="page">
      <div className="app">
        <h1 className="title title-pop">Tic‑Tac‑Toe</h1>

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

        {/* Mode and difficulty */}
        <div className="controls">
          <Segmented label="Game mode" options={MODES} value={gameMode} onChange={changeMode} />
          {isSinglePlayer && (
            <Segmented label="Difficulty" options={DIFFICULTIES} value={difficulty} onChange={setDifficulty} />
          )}
        </div>

        {/*Grid Layout*/}
        <div className={`board${game ? "" : " is-idle"}`}>
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
                disabled={!game || cell !== "_" || !game.currentPlayer}
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

        {/* New Game Button */}
        <button type="button" className="new-game-btn" onClick={createGame}>
          {game && game.status !== "IN_PROGRESS" ? "Play again" : "New game"}
        </button>

        {error && <p className="error" role="alert">{error}</p>}
      </div>
    </main>
  );
}
