package com.etiennek.yarnia.game;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable game rules. Defaults are the real game; tests/dev can override via
 * yarnia.game.* properties (e.g. shorter timers).
 */
@ConfigurationProperties(prefix = "yarnia.game")
public record GameProperties(
        @DefaultValue("10") int totalRounds,
        @DefaultValue("60") int submitSeconds,
        @DefaultValue("30") int voteSeconds,
        @DefaultValue("10") int revealSeconds,
        @DefaultValue("2") int winnerBonus,
        @DefaultValue("2") int finalRoundMultiplier,
        @DefaultValue("120") int maxSubmissionLength,
        /** Once everyone has acted, the phase ends after this short grace period. */
        @DefaultValue("2") int earlyAdvanceDelaySeconds) {
}
