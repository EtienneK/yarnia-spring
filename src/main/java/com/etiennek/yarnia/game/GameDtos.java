package com.etiennek.yarnia.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameDtos {

    /**
     * The full game view broadcast to /topic/party/{partyId}/game.
     * Fields are null/empty when not relevant to the current phase; notably,
     * submission authors and vote counts are hidden until REVEAL.
     */
    public record GameSnapshot(
            int round,
            int totalRounds,
            GamePhase phase,
            long phaseEndsAt,
            int maxSubmissionLength,
            String prompt,
            List<StoryLine> story,
            List<SubmissionView> submissions,
            List<UUID> submitted,
            List<UUID> voted,
            Map<UUID, Integer> scores,
            Map<UUID, Integer> roundPoints,
            UUID winnerSubmissionId,
            List<UUID> winnerIds) {
    }

    public record StoryLine(
            String text,
            UUID authorId,
            String authorName,
            String color,
            boolean moral) {
    }

    public record SubmissionView(
            UUID id,
            String text,
            UUID authorId,
            String authorName,
            String color,
            int votes) {
    }
}
