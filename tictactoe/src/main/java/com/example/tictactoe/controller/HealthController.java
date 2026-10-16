package com.example.tictactoe.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight endpoint the frontend calls on page load.
 * On Render's free plan this wakes the sleeping server early,
 * so it is usually ready by the time the player starts a game.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
