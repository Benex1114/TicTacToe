# Tic-Tac-Toe: Spring Boot + React

A full-stack Tic-Tac-Toe game. The **Spring Boot REST API** holds all the game rules (move validation, win and draw detection, and an unbeatable **minimax AI** opponent). The **React (Vite) frontend** draws the board and keeps score.

<p align="center">
  <img src="Pictures/Screenshot%202026-05-01%20181151.jpg" width="45%" alt="Start screen" />
  <img src="Pictures/Screenshot%202026-05-01%20181705.jpg" width="45%" alt="Winning line highlighted" />
</p>

---

## Features

- **Two modes**
  - **Single Player**: play X against the computer (O), which uses the **minimax** algorithm and never loses
  - **Two Player**: two players on one screen; the starting player alternates between X and O each game
- **Server-side game logic**: the backend validates every move, so the client can't make illegal ones
- **Win detection** across all 8 lines, with the winning line highlighted in the UI
- **Scoreboard** for X wins, O wins and draws across the session
- **Clean error handling**: a global exception handler returns consistent JSON errors (game not found, cell already taken, game already over, invalid index)
- **Input validation** with Jakarta Bean Validation (`cellIndex` must be 0–8)

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 4 (Web MVC, Validation), Maven |
| Frontend | React 19, Vite, JavaScript, CSS |
| Storage | In-memory (`ConcurrentHashMap`) |

## How the AI works

When it's O's turn in single-player mode, the backend tries every empty cell and scores the resulting position using **minimax**:

- `+10` if O wins, `-10` if X wins, `0` for a draw
- O picks the move with the highest score, assuming X always plays its best reply

Tic-Tac-Toe has a small game tree, so a full search runs instantly and the AI plays perfectly. The best you can do against it is a draw.

## API

Base URL: `http://localhost:8080/api/games`

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/games` | `{ "gameMode": "SINGLE_PLAYER" \| "MULTI_PLAYER" }` | Create a new game |
| `GET` | `/api/games/{gameId}` | – | Get the current game state |
| `POST` | `/api/games/{gameId}/moves` | `{ "cellIndex": 0-8 }` | Make a move (the AI replies automatically in single-player mode) |

**Example response**

```json
{
  "gameId": "3f1c2b9e-...",
  "board": ["X", "_", "_", "_", "O", "_", "_", "_", "_"],
  "currentPlayer": "X",
  "status": "IN_PROGRESS"
}
```

`status` is one of `IN_PROGRESS`, `X_WON`, `O_WON`, `DRAW`.

Board cells are indexed like this:

```
 0 | 1 | 2
---+---+---
 3 | 4 | 5
---+---+---
 6 | 7 | 8
```

## Project structure

```
TicTacToe/
├── tictactoe/                  Spring Boot backend
│   └── src/main/java/com/example/tictactoe/
│       ├── controller/         GameController (REST endpoints)
│       ├── service/            GameService (rules + minimax AI)
│       ├── repository/         In-memory GameRepository
│       ├── entity/, domain/    Game, GameMode, Player, GameStatus
│       ├── dto/                Request/response objects
│       └── exception/          Custom exceptions + GlobalExceptionHandler
├── tictactoe-ui/               React + Vite frontend
│   └── src/App.jsx
└── Pictures/                   Screenshots
```

## Running locally

**Prerequisites:** JDK 17+, Node.js 20+

**1. Start the backend** (runs on port 8080)

```bash
cd tictactoe
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

**2. Start the frontend** (runs on port 5173)

```bash
cd tictactoe-ui
npm install
npm run dev
```

**3.** Open http://localhost:5173, pick a mode and click **New Game**.

## Planned improvements

- Save games in a database (MongoDB or MySQL) instead of in memory
- Configure the API URL and CORS origin through environment variables for deployment
- Unit tests for the win, draw and minimax logic

## Author

**Seetharaman Kumar Iyer**, [GitHub @Benex1114](https://github.com/Benex1114)
