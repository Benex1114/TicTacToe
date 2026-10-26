package com.example.tictactoe.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.entity.Difficulty;
import com.example.tictactoe.entity.Game;
import com.example.tictactoe.entity.GameMode;
import com.example.tictactoe.exception.GameNotFoundException;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.repository.GameRepository;

@Service
public class GameService {

    // Created once instead of on every win check (minimax checks wins thousands of times per move)
    private static final int[][] WIN_PATTERNS = {
            {0, 1, 2},
            {3, 4, 5},
            {6, 7, 8},
            {0, 3, 6},
            {1, 4, 7},
            {2, 5, 8},
            {0, 4, 8},
            {2, 4, 6}
    };

    // Chance that Hard makes a "quiet mistake" on a turn where nothing urgent is happening
    static final double HARD_SLIP_CHANCE = 0.5;

    private final GameRepository gameRepository;
    private final Random random;
    private Player lastStartingPlayer = null;

    @Autowired
    public GameService(GameRepository gameRepository) {
        this(gameRepository, new Random());
    }

    // Lets tests pass a seeded or fixed Random so AI behaviour is repeatable
    GameService(GameRepository gameRepository, Random random) {
        this.gameRepository = gameRepository;
        this.random = random;
    }

    //Create Game
    public GameResponse createGame(GameMode gameMode) {
        return createGame(gameMode, null);
    }

    public GameResponse createGame(GameMode gameMode, Difficulty difficulty) {
        
        Player starter;

        if (gameMode == GameMode.SINGLE_PLAYER) {
            starter = Player.X;
        } else {
            starter = (lastStartingPlayer == null || lastStartingPlayer == Player.O)
                    ? Player.X
                    : Player.O;
            lastStartingPlayer = starter;
        }

        Game game = new Game();
        game.setId(UUID.randomUUID().toString());
        game.setBoardState("_,_,_,_,_,_,_,_,_");
        game.setStartingPlayer(starter);
        game.setCurrentPlayer(starter);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setGameMode(gameMode != null ? gameMode : GameMode.MULTI_PLAYER);
        // Difficulty only applies to single player; default keeps the unbeatable AI
        game.setDifficulty(game.getGameMode() == GameMode.SINGLE_PLAYER
                ? (difficulty != null ? difficulty : Difficulty.IMPOSSIBLE)
                : null);
        if(game.getGameMode()==GameMode.MULTI_PLAYER) {lastStartingPlayer = starter;}
        return saveNewGame(game);
    }

    /**
     * Starts a two-player game for an online room, with the starting player chosen by the room
     * (each room alternates on its own, separately from local two-player games).
     */
    public GameResponse createOnlineGame(Player starter) {
        Game game = new Game();
        game.setId(UUID.randomUUID().toString());
        game.setBoardState("_,_,_,_,_,_,_,_,_");
        game.setStartingPlayer(starter);
        game.setCurrentPlayer(starter);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setGameMode(GameMode.MULTI_PLAYER);
        return saveNewGame(game);
    }

    private GameResponse saveNewGame(Game game) {
        game.setCreatedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());

        Game savedGame = gameRepository.save(game);

        List<String> board =
                Arrays.asList(savedGame.getBoardState().split(","));

        return new GameResponse(
                savedGame.getId(),
                board,
                savedGame.getCurrentPlayer(),
                savedGame.getStatus(),
                savedGame.getDifficulty()
        );
        
    }

    //Get Game by Id
    public GameResponse getGameById(String gameId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));

        List<String> board =
                Arrays.asList(game.getBoardState().split(","));

        return new GameResponse(
                game.getId(),
                board,
                game.getCurrentPlayer(),
                game.getStatus(),
                game.getDifficulty()
        );
    }

    //Winning condition checker
    private boolean hasPlayerWon(String[] board, Player player) {

    String p = player.name();    


    for (int[] pattern : WIN_PATTERNS) {
        if (board[pattern[0]].equals(p) &&
            board[pattern[1]].equals(p) &&
            board[pattern[2]].equals(p)) {
            return true;
        }
    }
    return false;
    }

    //Checking Draw condition
    private boolean isDraw(String[] board) {
    for (String cell : board) {
        if (cell.equals("_")) {
            return false;
        }
    }
    return true;
    }

    //Make Move
    public GameResponse makeMove(String gameId, int cellIndex) {

    Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new GameNotFoundException(gameId));

    if (!game.getStatus().equals(GameStatus.IN_PROGRESS)) {
        throw new InvalidMoveException("Game already finished");
    }

    String[] board = game.getBoardState().split(",");

    if (cellIndex < 0 || cellIndex > 8) {
        throw new InvalidMoveException("Invalid cell index");
    }

    if (!board[cellIndex].equals("_")) {
        throw new InvalidMoveException("Cell already occupied");
    }

    Player currentPlayer = game.getCurrentPlayer();
    board[cellIndex] = currentPlayer.toString(); //patching

    // Check win
    if (hasPlayerWon(board, currentPlayer)) {
        game.setStatus(currentPlayer == Player.X ? GameStatus.X_WON : GameStatus.O_WON);
        game.setCurrentPlayer(null);
    }
    // Check draw
    else if (isDraw(board)) {
        game.setStatus(GameStatus.DRAW);
        game.setCurrentPlayer(null);
    }
    // Continue game
    else {
        game.setCurrentPlayer(currentPlayer == Player.X ? Player.O : Player.X);
    }

    game.setBoardState(String.join(",", board));

    if (game.getGameMode() == GameMode.SINGLE_PLAYER &&
        game.getStatus() == GameStatus.IN_PROGRESS &&
        game.getCurrentPlayer() == Player.O) {

        makeAiMove(game);
    }

    Game savedGame = gameRepository.save(game);

    return new GameResponse(
            savedGame.getId(),
            Arrays.asList(savedGame.getBoardState().split(",")),
            savedGame.getCurrentPlayer(),
            savedGame.getStatus(),
            savedGame.getDifficulty()
    );
    }

    private void makeAiMove(Game game) {
    String[] board = game.getBoardState().split(",");

    int move = chooseAiMove(board, game.getDifficulty());
    board[move] = Player.O.name();

    if (hasPlayerWon(board, Player.O)) {
        game.setStatus(GameStatus.O_WON);
        game.setCurrentPlayer(null);
    } else if (isDraw(board)) {
        game.setStatus(GameStatus.DRAW);
        game.setCurrentPlayer(null);
    } else {
        game.setCurrentPlayer(Player.X);
    }

    game.setBoardState(String.join(",", board));
    }

    // Each difficulty has its own strategy, so the levels feel different to play against
    private int chooseAiMove(String[] board, Difficulty difficulty) {
        Difficulty level = difficulty != null ? difficulty : Difficulty.IMPOSSIBLE;

        return switch (level) {
            // Rule-based levels: look one move ahead only
            case EASY -> winningMove(board, Player.O).orElseGet(() -> randomEmptyCell(board));
            case MEDIUM -> winningMove(board, Player.O)
                    .or(() -> winningMove(board, Player.X)) // block the player's win
                    .orElseGet(() -> randomEmptyCell(board));
            // Search-based levels: minimax looks all the way to the end of the game
            case HARD -> hardMove(board);
            case IMPOSSIBLE -> findBestMove(board);
        };
    }

    // A cell where the given player would complete a line right now, if there is one
    private Optional<Integer> winningMove(String[] board, Player player) {
        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_")) {
                board[i] = player.name();
                boolean wins = hasPlayerWon(board, player);
                board[i] = "_";
                if (wins) {
                    return Optional.of(i);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Hard never gives away a free win: it always takes its own win and always blocks yours.
     * On any other turn, half the time it makes a "quiet mistake": a move that looks safe
     * but is worse than the best move, which lets a sharp player set up a fork and win.
     * If the player doesn't spot it, Hard goes straight back to perfect play.
     */
    private int hardMove(String[] board) {
        Optional<Integer> urgent = winningMove(board, Player.O).or(() -> winningMove(board, Player.X));
        if (urgent.isPresent()) {
            return urgent.get();
        }
        if (random.nextDouble() < HARD_SLIP_CHANCE) {
            List<Integer> mistakes = quietMistakes(board);
            if (!mistakes.isEmpty()) {
                return mistakes.get(random.nextInt(mistakes.size()));
            }
        }
        return findBestMove(board);
    }

    // Every move that scores worse than the best one (the position gets worse for O, but nothing is lost straight away)
    private List<Integer> quietMistakes(String[] board) {
        int[] scores = new int[9];
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_")) {
                board[i] = Player.O.name();
                // Full search window, so each score is exact rather than a pruned bound
                scores[i] = minimax(board, false, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
                board[i] = "_";
                best = Math.max(best, scores[i]);
            }
        }
        List<Integer> mistakes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_") && scores[i] < best) {
                mistakes.add(i);
            }
        }
        return mistakes;
    }

    private int randomEmptyCell(String[] board) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i].equals("_")) {
                empty.add(i);
            }
        }
        return empty.get(random.nextInt(empty.size()));
    }

    private int findBestMove(String[] board) {
        int bestScore = Integer.MIN_VALUE;
        int move = -1;

        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_")) {
                board[i] = Player.O.name();
                // Alpha starts at the best score found so far, so worse moves are cut off early
                int score = minimax(board, false, bestScore, Integer.MAX_VALUE, 1);
                board[i] = "_";

                if (score > bestScore) {
                    bestScore = score;
                    move = i;
                }
            }
        }
        return move;
    }

    /**
     * Minimax with alpha-beta pruning.
     * alpha = best score O is already guaranteed, beta = best score X is already guaranteed.
     * When alpha >= beta the rest of this branch can't change the result, so it is skipped.
     * Same moves as plain minimax, far fewer positions searched.
     *
     * Scores include depth (how many moves away the result is): a win sooner scores higher,
     * a loss later scores higher. So the AI wins as fast as it can and, when it's losing,
     * still blocks and makes the player earn the win instead of giving up.
     */
    private int minimax(String[] board, boolean isMaximizing, int alpha, int beta, int depth) {

        if (hasPlayerWon(board, Player.O)) return 10 - depth;
        if (hasPlayerWon(board, Player.X)) return depth - 10;
        if (isDraw(board)) return 0;

        if (isMaximizing) {
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < 9; i++) {
                if (board[i].equals("_")) {
                    board[i] = Player.O.name();
                    best = Math.max(best, minimax(board, false, alpha, beta, depth + 1));
                    board[i] = "_";
                    alpha = Math.max(alpha, best);
                    if (alpha >= beta) break;
                }
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) {
                if (board[i].equals("_")) {
                    board[i] = Player.X.name();
                    best = Math.min(best, minimax(board, true, alpha, beta, depth + 1));
                    board[i] = "_";
                    beta = Math.min(beta, best);
                    if (alpha >= beta) break;
                }
            }
            return best;
        }
    }

}
