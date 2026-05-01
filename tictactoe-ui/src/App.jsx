import { useState } from "react";
const API_BASE = "http://localhost:8080/api/games";

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

//App functions
export default function App() {
  const [players, setPlayers] = useState({X: "", O: "", });
  const [scores, setScores] = useState({X: 0, O: 0, DRAW: 0,});
  const [game, setGame] = useState(null);
  const [error, setError] = useState(null);
  const [gameMode, setGameMode] = useState("MULTI_PLAYER");

  const createGame = async () => {
    const res = await fetch(API_BASE, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ gameMode }), });
    const data = await res.json();
    setGame(data);
  };

  const makeMove = async (index) => {
    if (!game || game.board[index] !== "_" || !game.currentPlayer) return;

    const res = await fetch(`${API_BASE}/${game.gameId}/moves`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cellIndex: index }),
    });

    const data = await res.json();
    res.ok ? setGame(data) : setError(data.message);
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

// Top Container
  return (
<div style={pageStyle}>
    <div style={appContainer}>
      {/* Title */}
      <h2 className= "title-pop" style={{fontSize: 50}}>Tic‑Tac‑Toe</h2>

      {/* Scoreboard */}
      <div style={{ marginBottom: "10px", textAlign: "center" }}>
        <strong>Scores</strong>
        <div>{players.X || "Player X"}: {scores.X}</div>
        <div>{players.O || "Player O"}: {scores.O}</div>
        <div>Draws: {scores.DRAW}</div>
      </div>
          
      <div style={{ display: "flex", gap: "10px" }}>
        <label>
          <input
            type="radio"
            value="SINGLE_PLAYER"
            checked={gameMode === "SINGLE_PLAYER"}
            onChange={() => setGameMode("SINGLE_PLAYER")}
          />
          Single Player
        </label>

        <label>
          <input
            type="radio"
            value="MULTI_PLAYER"
            checked={gameMode === "MULTI_PLAYER"}
            onChange={() => setGameMode("MULTI_PLAYER")}
          />
          Two Player
        </label>
      </div>


      {/* New Game Button */}
      <button className="new-game-btn" onClick={createGame}>New Game</button>

      {game && (
        <>
          <h3>
            {game.status === "IN_PROGRESS" && `Turn: ${game.currentPlayer === "X" ? players.X || "Player X" : players.O || "Player O"}`}
            {game.status === "X_WON" && `Winner: ${players.X || "Player X"}`}
            {game.status === "O_WON" && `Winner: ${players.O || "Player O"}`}
            {game.status === "DRAW" && "It's a Draw!"}
          </h3>

          {/*Grid Layout*/}
          <div style={{ position: "relative", width: 300, height: 300 }}>
            <div key={game.gameId} className="board-container">
            <svg width="300" height="300">
              {/* Vertical lines */}
              <line x1="100" y1="0" x2="100" y2="300" stroke="#243864" strokeWidth="7" />
              <line x1="200" y1="0" x2="200" y2="300" stroke="#243864" strokeWidth="7" />

              {/* Horizontal lines */}
              <line x1="0" y1="100" x2="300" y2="100" stroke="#243864" strokeWidth="7" />
              <line x1="0" y1="200" x2="300" y2="200" stroke="#243864" strokeWidth="7" />
            </svg>
            </div>

            {/* Click & render layer */}
            <div style={overlayGrid}>
              {game.board.map((cell, index) => (
                <button
                  key={index}
                  onClick={() => makeMove(index)}
                  disabled={cell !== "_" || !game.currentPlayer}
                  style={{...overlayCell,
                  backgroundColor: winningCells.includes(index)
                    ? "rgba(34, 197, 94, 0.2)"
                    : "transparent",
                  boxShadow: winningCells.includes(index)
                    ? "0 0 25px rgba(34, 197, 94, 0.6)"
                    : "none",
                  animation: winningCells.includes(index)
                    ? "pulse 1.2s infinite alternate"
                    : "none",
                }}  
                onMouseEnter={(e) => {
                    if (cell === "_") e.target.style.transform = "scale(1.05)";
                }}
                onMouseLeave={(e) => {
                  e.target.style.transform = "scale(1)";
                }}
                >
                  {cell !== "_" && (
                    <span className="cell-mark" style={{color: cell === "X" ? "#38bdf8" : "#f472b6"}}>
                      {cell}
                    </span>
                  )}
                </button>
                
              ))}
            </div>
          </div>

        </>
      )}

      {error && <p style={{ color: "red" }}>{error}</p>}
    </div>
 </div>
  );
}

const overlayGrid = {
  position: "absolute",
  top: 0,
  left: 0,
  width: "300px",
  height: "300px",
  display: "grid",
  gridTemplateColumns: "repeat(3, 1fr)",
  gridTemplateRows: "repeat(3, 1fr)",
};

const overlayCell = {
  background: "transparent",
  border: "none",
  fontSize: "46px",
  fontWeight: "900",
  color: "#ffffff",
  cursor: "pointer",
  transition: "transform 0.15s ease, background-color 0.3s ease",
};

const pageStyle = {
  minHeight: "100vh",
  display: "flex",
  justifyContent: "center",
  alignItems: "center",
  background: "radial-gradient(circle at top, #1e293b, #020617)",
  color: "#e5e7eb",
  fontFamily: "Inter, system-ui, sans-serif",
};

const appContainer = {
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  gap: "12px",
};
