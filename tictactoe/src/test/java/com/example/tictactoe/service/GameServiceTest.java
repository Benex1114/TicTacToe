package com.example.tictactoe.service;
 
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
 
import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.entity.Difficulty;
import com.example.tictactoe.entity.Game;
import com.example.tictactoe.entity.GameMode;
import com.example.tictactoe.exception.GameNotFoundException;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.repository.GameRepository;
 
/**
* Unit tests for GameService: game creation, move validation,
* win/draw detection and the minimax AI. No Spring context needed.
*/
class GameServiceTest {
 
    private GameService gameService;
 
    @BeforeEach
    void setUp() {
        gameService = new GameService(new GameRepository());
    }
 
    // ---------- Game creation ----------
 
    @Test
    void newGameHasEmptyBoardAndIsInProgress() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
 
        assertEquals(Collections.nCopies(9, "_"), game.getBoard());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }
 
    @Test
    void singlePlayerGameAlwaysStartsWithX() {
        gameService.createGame(GameMode.SINGLE_PLAYER);
        GameResponse second = gameService.createGame(GameMode.SINGLE_PLAYER);
 
        assertEquals(Player.X, second.getCurrentPlayer());
    }
 
    @Test
    void multiPlayerStartingPlayerAlternatesBetweenGames() {
        GameResponse first = gameService.createGame(GameMode.MULTI_PLAYER);
        GameResponse second = gameService.createGame(GameMode.MULTI_PLAYER);
 
        assertNotEquals(first.getCurrentPlayer(), second.getCurrentPlayer());
    }
 
    @Test
    void gettingUnknownGameThrows() {
        assertThrows(GameNotFoundException.class, () -> gameService.getGameById("does-not-exist"));
    }
 
    // ---------- Difficulty ----------

    @Test
    void singlePlayerStoresChosenDifficulty() {
        GameResponse game = gameService.createGame(GameMode.SINGLE_PLAYER, Difficulty.EASY);

        assertEquals(Difficulty.EASY, game.getDifficulty());
        assertEquals(Difficulty.EASY, gameService.getGameById(game.getGameId()).getDifficulty());
    }

    @Test
    void singlePlayerDefaultsToImpossible() {
        GameResponse game = gameService.createGame(GameMode.SINGLE_PLAYER, null);

        assertEquals(Difficulty.IMPOSSIBLE, game.getDifficulty());
    }

    @Test
    void multiPlayerIgnoresDifficulty() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER, Difficulty.HARD);

        assertNull(game.getDifficulty());
    }

    // ---------- Move validation ----------
 
    @Test
    void cannotPlayOnOccupiedCell() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
        gameService.makeMove(id, 4);
 
        assertThrows(InvalidMoveException.class, () -> gameService.makeMove(id, 4));
    }
 
    @Test
    void cannotPlayOutsideTheBoard() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
 
        assertThrows(InvalidMoveException.class, () -> gameService.makeMove(id, 9));
    }
 
    @Test
    void playersAlternateTurns() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
        Player first = game.getCurrentPlayer();
 
        GameResponse after = gameService.makeMove(game.getGameId(), 0);
 
        assertNotEquals(first, after.getCurrentPlayer());
    }
 
    // ---------- Win and draw detection ----------
 
    @Test
    void completingARowWinsTheGame() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
        String id = game.getGameId();
        Player starter = game.getCurrentPlayer();
 
        // Starter takes the top row (0,1,2); opponent plays 3,4
        GameResponse result = play(id, 0, 3, 1, 4, 2);
 
        assertEquals(starter == Player.X ? GameStatus.X_WON : GameStatus.O_WON, result.getStatus());
        assertNull(result.getCurrentPlayer());
    }
 
    @Test
    void completingADiagonalWinsTheGame() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
        Player starter = game.getCurrentPlayer();
 
        GameResponse result = play(game.getGameId(), 0, 1, 4, 2, 8);
 
        assertEquals(starter == Player.X ? GameStatus.X_WON : GameStatus.O_WON, result.getStatus());
    }
 
    @Test
    void fullBoardWithNoLineIsADraw() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
 
        // Final board (S = starter, T = other):
        //  S | T | S
        //  S | T | T
        //  T | S | S
        GameResponse result = play(id, 0, 1, 2, 4, 3, 5, 7, 6, 8);
 
        assertEquals(GameStatus.DRAW, result.getStatus());
    }
 
    @Test
    void noMovesAllowedAfterGameEnds() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
        play(id, 0, 3, 1, 4, 2);
 
        assertThrows(InvalidMoveException.class, () -> gameService.makeMove(id, 8));
    }
 
    // ---------- Minimax AI ----------
 
    @Test
    void aiRepliesImmediatelyInSinglePlayer() {
        String id = gameService.createGame(GameMode.SINGLE_PLAYER).getGameId();
 
        GameResponse after = gameService.makeMove(id, 0);
 
        assertEquals(1, Collections.frequency(after.getBoard(), "O"));
        assertEquals(Player.X, after.getCurrentPlayer());
    }
 
    @Test
    void aiBlocksAnImmediateWin() {
        String id = gameService.createGame(GameMode.SINGLE_PLAYER).getGameId();
        // X plays the centre; minimax answers with a corner
        GameResponse board = gameService.makeMove(id, 4);
        int aiCorner = board.getBoard().indexOf("O");
 
        // X threatens a line through the centre; AI must block the opposite cell
        int x = (aiCorner == 0) ? 2 : 0;
        int block = 8 - x;
        board = gameService.makeMove(id, x);
 
        assertEquals("O", board.getBoard().get(block));
    }
 
    @Test
    void aiNeverLosesAgainstAnyOpponent() {
        // Exhaustively try every possible sequence of X moves
        assertAiNeverLoses(List.of());
    }

    @Test
    void movingInUnknownGameThrowsGameNotGound() {
        assertThrows(GameNotFoundException.class, () -> gameService.makeMove("does-not-exist", 0));
    }
 
    // ---------- Difficulty levels ----------
    //
    // Boards are written as 9 cells, row by row, e.g. "X,_,_,O,_,_,_,_,_".
    // A fixed Random makes the "random" moves predictable: it always picks the LAST empty cell,
    // and its roll decides whether Hard slips on its first move (0.0 = slips, 0.99 = doesn't).

    private GameRepository repository;

    /** A Random that always picks the last empty cell and always rolls the given number. */
    private static Random fixedRandom(double roll) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }

            @Override
            public double nextDouble() {
                return roll;
            }
        };
    }

    /** Starts a single-player game at the given difficulty with the board already set up (X to move). */
    private String gameWithBoard(GameService service, Difficulty difficulty, String board) {
        String id = service.createGame(GameMode.SINGLE_PLAYER, difficulty).getGameId();
        Game game = repository.findById(id).orElseThrow();
        game.setBoardState(board);
        return id;
    }

    private GameService serviceWithFixedRandom() {
        return serviceWithFixedRandom(0.0);
    }

    private GameService serviceWithFixedRandom(double roll) {
        repository = new GameRepository();
        return new GameService(repository, fixedRandom(roll));
    }

    @Test
    void easyTakesAWinWhenOneIsAvailable() {
        GameService service = serviceWithFixedRandom();
        // O has 0 and 1; X plays 8, leaving O free to complete the top row at 2
        String id = gameWithBoard(service, Difficulty.EASY, "O,O,_,X,X,_,_,_,_");

        GameResponse result = service.makeMove(id, 8);

        assertEquals("O", result.getBoard().get(2));
        assertEquals(GameStatus.O_WON, result.getStatus());
    }

    @Test
    void easyDoesNotBlockYourWin() {
        GameService service = serviceWithFixedRandom();
        // X plays 1 and now threatens 2 (top row); Easy plays randomly instead of blocking
        String id = gameWithBoard(service, Difficulty.EASY, "X,_,_,O,_,_,_,_,_");

        GameResponse result = service.makeMove(id, 1);

        assertEquals("_", result.getBoard().get(2));
    }

    @Test
    void mediumBlocksYourWin() {
        GameService service = serviceWithFixedRandom();
        String id = gameWithBoard(service, Difficulty.MEDIUM, "X,_,_,O,_,_,_,_,_");

        GameResponse result = service.makeMove(id, 1);

        assertEquals("O", result.getBoard().get(2));
    }

    @Test
    void mediumPrefersWinningOverBlocking() {
        GameService service = serviceWithFixedRandom();
        // O can win at 2, and X threatens 5: winning comes first
        String id = gameWithBoard(service, Difficulty.MEDIUM, "O,O,_,X,X,_,_,_,_");

        GameResponse result = service.makeMove(id, 8);

        assertEquals(GameStatus.O_WON, result.getStatus());
        assertEquals("O", result.getBoard().get(2));
    }

    @Test
    void hardAlwaysTakesAWinEvenWhenItWouldSlip() {
        GameService service = serviceWithFixedRandom(0.0); // roll says "slip" every turn
        String id = gameWithBoard(service, Difficulty.HARD, "O,O,_,X,X,_,_,_,_");

        GameResponse result = service.makeMove(id, 8);

        assertEquals(GameStatus.O_WON, result.getStatus());
    }

    @Test
    void hardAlwaysBlocksEvenWhenItWouldSlip() {
        GameService service = serviceWithFixedRandom(0.0);
        // X plays 1 and threatens 2; a slip must never mean leaving that open
        String id = gameWithBoard(service, Difficulty.HARD, "X,_,_,O,_,_,_,_,_");

        GameResponse result = service.makeMove(id, 1);

        assertEquals("O", result.getBoard().get(2));
    }

    @Test
    void hardSlipIsAQuietMistakeNotARandomMove() {
        GameService service = serviceWithFixedRandom(0.0);
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.HARD).getGameId();

        GameResponse result = service.makeMove(id, 4);

        // Against a centre opening the safe replies are corners; the quiet mistakes are the edges (1, 3, 5, 7).
        // The fixed Random picks the last mistake on the list: edge 7.
        assertEquals("O", result.getBoard().get(7));
    }

    @Test
    void hardPlaysPerfectlyWhenTheRollIsHigh() {
        GameService service = serviceWithFixedRandom(0.99); // roll says "no slip"
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.HARD).getGameId();

        GameResponse result = service.makeMove(id, 4);

        assertEquals("O", result.getBoard().get(0)); // the minimax corner
    }

    @Test
    void hardsMistakeCanBeExploitedWithAFork() {
        GameService service = serviceWithFixedRandom(0.0);
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.HARD).getGameId();

        service.makeMove(id, 4);                      // X centre; Hard's quiet mistake is edge 7
        service.makeMove(id, 0);                      // X threatens 8 (diagonal); Hard must block
        GameResponse board = service.makeMove(id, 6); // X now threatens 3 and 2 at once: a fork
        assertEquals(GameStatus.IN_PROGRESS, board.getStatus());

        int winningCell = board.getBoard().get(3).equals("_") ? 3 : 2;
        GameResponse result = service.makeMove(id, winningCell);

        assertEquals(GameStatus.X_WON, result.getStatus());
    }

    @Test
    void hardNeverMissesAWinOrABlockInAnyGame() {
        // Every possible game against Hard (with its slips): whenever O could win it does,
        // and whenever X threatened to win, O blocked
        assertHardNeverGivesAFreeWin(List.of());
    }

    private void assertHardNeverGivesAFreeWin(List<Integer> xMoves) {
        GameService fresh = new GameService(new GameRepository(), new Random(11));
        String id = fresh.createGame(GameMode.SINGLE_PLAYER, Difficulty.HARD).getGameId();
        GameResponse board = fresh.getGameById(id);
        List<String> beforeAi = board.getBoard();
        for (int cell : xMoves) {
            beforeAi = new java.util.ArrayList<>(board.getBoard());
            beforeAi.set(cell, "X"); // the board Hard saw when choosing its reply
            board = fresh.makeMove(id, cell);
        }

        boolean hardReplied = !xMoves.isEmpty() && board.getStatus() != GameStatus.X_WON && board.getStatus() != GameStatus.DRAW;
        if (hardReplied) {
            List<Integer> oWins = winningCells(beforeAi, "O");
            List<Integer> xThreats = winningCells(beforeAi, "X");
            if (!oWins.isEmpty()) {
                assertEquals(GameStatus.O_WON, board.getStatus(), "Hard missed a win after X moves " + xMoves);
            } else if (xThreats.size() == 1) {
                // One threat can always be blocked (two threats is a fork: X has earned the win)
                assertEquals("O", board.getBoard().get(xThreats.get(0)), "Hard missed a block after X moves " + xMoves);
            }
        }
        if (board.getStatus() != GameStatus.IN_PROGRESS) return;

        for (int cell = 0; cell < 9; cell++) {
            if (board.getBoard().get(cell).equals("_")) {
                List<Integer> next = new java.util.ArrayList<>(xMoves);
                next.add(cell);
                assertHardNeverGivesAFreeWin(next);
            }
        }
    }

    /** Every empty cell where the player would complete a line on their next move. */
    private static List<Integer> winningCells(List<String> board, String player) {
        int[][] lines = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
        java.util.Set<Integer> cells = new java.util.TreeSet<>();
        for (int[] line : lines) {
            int count = 0;
            int empty = -1;
            for (int i : line) {
                if (board.get(i).equals(player)) count++;
                else if (board.get(i).equals("_")) empty = i;
            }
            if (count == 2 && empty >= 0) cells.add(empty);
        }
        return new java.util.ArrayList<>(cells);
    }

    @Test
    void impossiblePlaysTheMinimaxMoveFromTheStart() {
        GameService service = serviceWithFixedRandom();
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.IMPOSSIBLE).getGameId();

        GameResponse result = service.makeMove(id, 4);

        // Same "always slip" roll as the Hard tests, but Impossible never slips and takes the corner
        assertEquals("O", result.getBoard().get(0));
    }

    @Test
    void everyDifficultyAlwaysMakesALegalMove() {
        // Play every possible game against each level; the AI must never break the rules or crash
        for (Difficulty level : Difficulty.values()) {
            assertLegalPlay(level, List.of());
        }
    }

    private void assertLegalPlay(Difficulty level, List<Integer> xMoves) {
        GameService fresh = new GameService(new GameRepository(), new Random(7));
        String id = fresh.createGame(GameMode.SINGLE_PLAYER, level).getGameId();
        GameResponse board = fresh.getGameById(id);
        for (int cell : xMoves) {
            board = fresh.makeMove(id, cell);
        }

        long xs = Collections.frequency(board.getBoard(), "X");
        long os = Collections.frequency(board.getBoard(), "O");
        assertTrue(os == xs || (os == xs - 1 && board.getStatus() != GameStatus.IN_PROGRESS),
                level + ": wrong number of O moves after X moves " + xMoves);
        if (board.getStatus() != GameStatus.IN_PROGRESS) return;

        for (int cell = 0; cell < 9; cell++) {
            if (board.getBoard().get(cell).equals("_")) {
                List<Integer> next = new java.util.ArrayList<>(xMoves);
                next.add(cell);
                assertLegalPlay(level, next);
            }
        }
    }

    // ---------- Helpers ----------
 
    private GameResponse play(String gameId, int... cells) {
        GameResponse response = null;
        for (int cell : cells) {
            response = gameService.makeMove(gameId, cell);
        }
        return response;
    }
 
    private void assertAiNeverLoses(List<Integer> xMoves) {
        GameService fresh = new GameService(new GameRepository());
        String id = fresh.createGame(GameMode.SINGLE_PLAYER).getGameId();
        GameResponse board = fresh.getGameById(id);
        for (int cell : xMoves) {
            board = fresh.makeMove(id, cell);
        }
 
        assertNotEquals(GameStatus.X_WON, board.getStatus(), "AI lost after X moves " + xMoves);
        if (board.getStatus() != GameStatus.IN_PROGRESS) return;
 
        for (int cell = 0; cell < 9; cell++) {
            if (board.getBoard().get(cell).equals("_")) {
                List<Integer> next = new java.util.ArrayList<>(xMoves);
                next.add(cell);
                assertAiNeverLoses(next);
            }
        }
    }
}