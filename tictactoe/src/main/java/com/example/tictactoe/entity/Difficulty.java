package com.example.tictactoe.entity;

/**
 * AI difficulty for single-player games. Each level plays in a recognisably different way:
 *
 * EASY       - takes a win if one is available, otherwise plays randomly (never blocks you)
 * MEDIUM     - takes a win, otherwise blocks your win, otherwise plays randomly
 * HARD       - always takes its wins and blocks yours; otherwise half the time it makes a
 *              "quiet mistake" that a sharp player can exploit with a fork
 * IMPOSSIBLE - always plays the minimax move (can't be beaten)
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD,
    IMPOSSIBLE
}
