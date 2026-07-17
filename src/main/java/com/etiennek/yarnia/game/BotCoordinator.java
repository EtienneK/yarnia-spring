package com.etiennek.yarnia.game;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Drives AI players. When a submit/vote phase starts, each bot gets scheduled
 * with a small random "thinking" delay, asks the BotBrain for a move, and
 * plays it through the normal GameService entry points (so all the same
 * validation applies). The brain call happens outside any transaction.
 */
@Component
public class BotCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(BotCoordinator.class);
    private static final Random RANDOM = new Random();

    public record SubmitTask(UUID partyId, int round, UUID botId, String persona,
            List<String> story, boolean moralRound, int maxLen, Instant deadline) {
    }

    public record VoteTask(UUID partyId, int round, UUID botId, String persona,
            List<String> story, List<Candidate> candidates, Instant deadline) {
    }

    public record Candidate(UUID submissionId, String text) {
    }

    private final TaskScheduler scheduler;
    private final ObjectProvider<GameService> gameService; // lazy: breaks the GameService <-> BotCoordinator cycle
    private final BotBrain botBrain;

    BotCoordinator(TaskScheduler gameTaskScheduler, ObjectProvider<GameService> gameService, BotBrain botBrain) {
        this.scheduler = gameTaskScheduler;
        this.gameService = gameService;
        this.botBrain = botBrain;
    }

    public void onSubmitPhase(List<SubmitTask> tasks) {
        for (final var task : tasks) {
            scheduler.schedule(() -> {
                try {
                    final var text = botBrain.continueStory(
                            task.persona(), task.story(), task.moralRound(), task.maxLen());
                    gameService.getObject().submit(task.partyId(), task.botId(), text);
                } catch (Exception e) {
                    logger.error("bot " + task.botId() + " failed to submit", e);
                }
            }, thinkingTime(task.deadline()));
        }
    }

    public void onVotePhase(List<VoteTask> tasks) {
        for (final var task : tasks) {
            scheduler.schedule(() -> {
                try {
                    final var texts = task.candidates().stream().map(Candidate::text).toList();
                    var pick = botBrain.pickVote(task.persona(), task.story(), texts);
                    if (pick < 0 || pick >= task.candidates().size()) {
                        pick = RANDOM.nextInt(task.candidates().size());
                    }
                    gameService.getObject().vote(
                            task.partyId(), task.botId(), task.candidates().get(pick).submissionId());
                } catch (Exception e) {
                    logger.error("bot " + task.botId() + " failed to vote", e);
                }
            }, thinkingTime(task.deadline()));
        }
    }

    /** 1.5s..8s, but never later than ~70% of the time left before the deadline. */
    private Instant thinkingTime(Instant deadline) {
        final var now = Instant.now();
        final var remainingMs = Math.max(Duration.between(now, deadline).toMillis(), 1000);
        final var maxDelay = Math.min(8_000, remainingMs * 7 / 10);
        final var minDelay = Math.min(1_500, maxDelay);
        return now.plusMillis(minDelay + (long) (RANDOM.nextDouble() * (maxDelay - minDelay)));
    }
}
