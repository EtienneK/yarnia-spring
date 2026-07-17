package com.etiennek.yarnia.game;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Schedules phase-deadline callbacks into the GameService. A stale callback
 * (fired after the game moved on or was deleted) is a no-op: advance() checks
 * that the round/phase still match before acting.
 */
@Component
public class GameTimer {
    private static final Logger logger = LoggerFactory.getLogger(GameTimer.class);

    private final TaskScheduler scheduler;
    private final ObjectProvider<GameService> gameService; // lazy: breaks the GameService <-> GameTimer cycle

    GameTimer(TaskScheduler gameTaskScheduler, ObjectProvider<GameService> gameService) {
        this.scheduler = gameTaskScheduler;
        this.gameService = gameService;
    }

    public void schedule(UUID partyId, int roundNumber, GamePhase phase, Instant deadline) {
        scheduler.schedule(() -> {
            try {
                gameService.getObject().advance(partyId, roundNumber, phase);
            } catch (Exception e) {
                logger.error("failed to advance game " + partyId + " from " + phase, e);
            }
        }, deadline.plusMillis(300));
    }

    @Configuration
    static class SchedulerConfig {
        @Bean
        TaskScheduler gameTaskScheduler() {
            final var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(4);
            scheduler.setThreadNamePrefix("game-timer-");
            return scheduler;
        }
    }
}
