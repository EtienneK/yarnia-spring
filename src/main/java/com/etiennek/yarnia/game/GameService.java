package com.etiennek.yarnia.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.etiennek.yarnia.game.BotCoordinator.Candidate;
import com.etiennek.yarnia.game.BotCoordinator.SubmitTask;
import com.etiennek.yarnia.game.BotCoordinator.VoteTask;
import com.etiennek.yarnia.game.GameDtos.GameSnapshot;
import com.etiennek.yarnia.game.GameDtos.StoryLine;
import com.etiennek.yarnia.game.GameDtos.SubmissionView;
import com.etiennek.yarnia.game.GameEntities.GameState;
import com.etiennek.yarnia.game.GameEntities.RoundSubmission;
import com.etiennek.yarnia.game.GameEntities.RoundVote;
import com.etiennek.yarnia.game.GameEntities.StorySegment;
import com.etiennek.yarnia.game.repos.GameStateRepository;
import com.etiennek.yarnia.game.repos.RoundSubmissionRepository;
import com.etiennek.yarnia.game.repos.RoundVoteRepository;
import com.etiennek.yarnia.game.repos.StorySegmentRepository;
import com.etiennek.yarnia.party.Constants.PartyPhase;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.PartyEvents;
import com.etiennek.yarnia.party.PartyService;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;
import com.etiennek.yarnia.party.repos.PartyStateRepository;

@Service
@Transactional
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private @Autowired GameStateRepository gameStateRepository;
    private @Autowired StorySegmentRepository storySegmentRepository;
    private @Autowired RoundSubmissionRepository roundSubmissionRepository;
    private @Autowired RoundVoteRepository roundVoteRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired PartyStateRepository partyStateRepository;
    private @Autowired PartyService partyService;
    private @Autowired SimpMessagingTemplate template;
    private @Autowired GameProperties props;
    private @Autowired GameTimer gameTimer;
    private @Autowired BotCoordinator botCoordinator;

    // ------------------------------------------------------------------ events

    @EventListener
    public void onGameStarted(PartyEvents.GameStartedEvent event) {
        startGame(event.partyId());
    }

    @EventListener
    public void onPartyDeleted(PartyEvents.PartyDeletedEvent event) {
        deleteGameData(event.partyId());
    }

    /**
     * Timers live in memory, so after a restart every persisted in-flight game
     * would sit on a dead deadline forever. Re-arm them; overdue phases get a
     * short grace so players have a moment to reconnect.
     */
    @EventListener
    public void onApplicationReady(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        for (final var state : gameStateRepository.findAll()) {
            if (state.getPhase() == GamePhase.FINISHED) {
                continue;
            }
            var deadline = state.getPhaseEndsAt();
            final var earliest = Instant.now().plusSeconds(props.revealSeconds());
            if (deadline.isBefore(earliest)) {
                deadline = earliest;
                gameStateRepository.save(state.withPhaseEndsAt(deadline));
            }
            gameTimer.schedule(state.getId(), state.getRoundNumber(), state.getPhase(), deadline);
            logger.info("re-armed timer for game {} ({} r{})", state.getId(), state.getPhase(),
                    state.getRoundNumber());
        }
    }

    @EventListener
    public void onMemberDisconnected(PartyEvents.MemberDisconnectedEvent event) {
        // A leaver might have been the only player everyone was waiting on.
        final var state = gameStateRepository.findById(event.partyId()).orElse(null);
        if (state != null && maybeEndPhaseEarly(state)) {
            broadcast(event.partyId());
        }
    }

    // ----------------------------------------------------------------- actions

    private void startGame(UUID partyId) {
        if (gameStateRepository.existsById(partyId)) {
            return;
        }
        storySegmentRepository.save(
                new StorySegment(UUID.randomUUID(), partyId, 0, SeedLines.random(), null, false));

        final var deadline = Instant.now().plusSeconds(props.submitSeconds());
        gameStateRepository.save(new GameState(partyId, 1, GamePhase.SUBMITTING, deadline));
        gameTimer.schedule(partyId, 1, GamePhase.SUBMITTING, deadline);
        botCoordinator.onSubmitPhase(submitTasks(partyId, 1, deadline));
        broadcast(partyId);
    }

    /** Create or replace this player's submission for the current round. */
    public void submit(UUID partyId, UUID playerId, String rawText) {
        final var state = gameStateRepository.findById(partyId).orElse(null);
        if (state == null) {
            return;
        }

        final var text = sanitize(rawText);
        if (state.getPhase() == GamePhase.SUBMITTING
                && Instant.now().isBefore(state.getPhaseEndsAt().plusSeconds(1))
                && text != null
                && isMemberOf(partyId, playerId)) {

            final var round = state.getRoundNumber();
            final var existing = roundSubmissionRepository
                    .findByPartyIdAndRoundNumberAndPlayerId(partyId, round, playerId);
            if (existing.isPresent()) {
                roundSubmissionRepository.save(existing.get().withText(text).withSubmittedAt(Instant.now()));
            } else {
                roundSubmissionRepository.save(new RoundSubmission(
                        UUID.randomUUID(), partyId, round, playerId, text, Instant.now(), 0));
            }
            maybeEndPhaseEarly(state);
        }

        broadcast(partyId);
    }

    /** Cast or change this player's vote for the current round. No self-votes. */
    public void vote(UUID partyId, UUID voterId, UUID submissionId) {
        final var state = gameStateRepository.findById(partyId).orElse(null);
        if (state == null) {
            return;
        }

        if (state.getPhase() == GamePhase.VOTING
                && Instant.now().isBefore(state.getPhaseEndsAt().plusSeconds(1))
                && isMemberOf(partyId, voterId)) {

            final var round = state.getRoundNumber();
            final var submission = roundSubmissionRepository.findById(submissionId)
                    .filter(s -> s.getPartyId().equals(partyId) && s.getRoundNumber() == round)
                    .orElse(null);

            if (submission != null && !submission.getPlayerId().equals(voterId)) {
                final var existing = roundVoteRepository
                        .findByPartyIdAndRoundNumberAndVoterId(partyId, round, voterId);
                if (existing.isPresent()) {
                    roundVoteRepository.save(existing.get().withSubmissionId(submissionId));
                } else {
                    roundVoteRepository.save(new RoundVote(
                            UUID.randomUUID(), partyId, round, voterId, submissionId));
                }
                maybeEndPhaseEarly(state);
            }
        }

        broadcast(partyId);
    }

    /** Host only, after a finished game: wipe game data and return the party to the lobby. */
    public void playAgain(UUID partyId, UUID playerId) {
        final var partyState = partyStateRepository.findById(partyId).orElse(null);
        if (partyState == null || !partyState.getPartyPhase().equals(PartyPhase.FINISHED)) {
            return;
        }
        final var members = partyMemberRepository.findByPartyStateId(partyId);
        final var me = members.stream().filter(m -> m.getId().equals(playerId)).findFirst().orElse(null);
        if (me == null || !me.isHost()) {
            return;
        }

        deleteGameData(partyId);
        for (final var member : members) {
            partyMemberRepository.save(member.withScore(0).withReady(member.isBot()));
        }
        partyStateRepository.save(partyState.withPartyPhase(PartyPhase.WAITING));

        template.convertAndSend("/topic/party/" + partyId + "/snapshot",
                partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId)));
    }

    /** Timer callback: move to the next phase iff the game is still where the timer left it. */
    public void advance(UUID partyId, int expectedRound, GamePhase expectedPhase) {
        final var state = gameStateRepository.findById(partyId).orElse(null);
        if (state == null || state.getRoundNumber() != expectedRound || state.getPhase() != expectedPhase) {
            return;
        }

        switch (state.getPhase()) {
            case SUBMITTING -> beginVoting(state);
            case VOTING -> beginReveal(state);
            case REVEAL -> nextRoundOrFinish(state);
            case FINISHED -> {
            }
        }
    }

    // ------------------------------------------------------------- transitions

    private void beginVoting(GameState state) {
        final var partyId = state.getId();
        final var round = state.getRoundNumber();
        final var submissions = roundSubmissionRepository.findByPartyIdAndRoundNumber(partyId, round);

        if (submissions.isEmpty()) {
            // Nothing to vote on; skip straight to (an empty) reveal.
            beginReveal(state);
            return;
        }

        final var shuffled = new ArrayList<>(submissions);
        Collections.shuffle(shuffled);
        for (var i = 0; i < shuffled.size(); i++) {
            roundSubmissionRepository.save(shuffled.get(i).withDisplayOrder(i));
        }

        final var deadline = Instant.now().plusSeconds(props.voteSeconds());
        gameStateRepository.save(state.withPhase(GamePhase.VOTING).withPhaseEndsAt(deadline));
        gameTimer.schedule(partyId, round, GamePhase.VOTING, deadline);
        botCoordinator.onVotePhase(voteTasks(partyId, round, shuffled, deadline));
        broadcast(partyId);
    }

    private void beginReveal(GameState state) {
        final var partyId = state.getId();
        final var round = state.getRoundNumber();
        final var submissions = roundSubmissionRepository.findByPartyIdAndRoundNumber(partyId, round);
        final var votes = roundVoteRepository.findByPartyIdAndRoundNumber(partyId, round);

        final var tally = tally(submissions, votes, round);
        if (tally.winner() != null) {
            final var members = membersById(partyId);
            tally.pointsByPlayer().forEach((memberId, points) -> {
                final var member = members.get(memberId);
                if (member != null) {
                    partyMemberRepository.save(member.withScore(member.getScore() + points));
                }
            });

            final var position = storySegmentRepository.findByPartyIdOrderByPositionAsc(partyId).size();
            storySegmentRepository.save(new StorySegment(
                    UUID.randomUUID(),
                    partyId,
                    position,
                    tally.winner().getText(),
                    tally.winner().getPlayerId(),
                    round == props.totalRounds()));
        }

        final var deadline = Instant.now().plusSeconds(props.revealSeconds());
        gameStateRepository.save(state.withPhase(GamePhase.REVEAL).withPhaseEndsAt(deadline));
        gameTimer.schedule(partyId, round, GamePhase.REVEAL, deadline);
        broadcast(partyId);
    }

    private void nextRoundOrFinish(GameState state) {
        final var partyId = state.getId();

        if (state.getRoundNumber() >= props.totalRounds()) {
            gameStateRepository.save(state.withPhase(GamePhase.FINISHED).withPhaseEndsAt(Instant.now()));

            partyStateRepository.findById(partyId).ifPresent(partyState -> partyStateRepository
                    .save(partyState.withPartyPhase(PartyPhase.FINISHED)));
            template.convertAndSend("/topic/party/" + partyId + "/snapshot",
                    partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId)));
            broadcast(partyId);
            return;
        }

        final var round = state.getRoundNumber() + 1;
        final var deadline = Instant.now().plusSeconds(props.submitSeconds());
        gameStateRepository.save(state
                .withRoundNumber(round)
                .withPhase(GamePhase.SUBMITTING)
                .withPhaseEndsAt(deadline));
        gameTimer.schedule(partyId, round, GamePhase.SUBMITTING, deadline);
        botCoordinator.onSubmitPhase(submitTasks(partyId, round, deadline));
        broadcast(partyId);
    }

    // ---------------------------------------------------------------- snapshot

    public GameSnapshot snapshot(UUID partyId) {
        final var state = gameStateRepository.findById(partyId).orElse(null);
        if (state == null) {
            return null;
        }

        final var round = state.getRoundNumber();
        final var phase = state.getPhase();
        final var members = membersById(partyId);
        final var segments = storySegmentRepository.findByPartyIdOrderByPositionAsc(partyId);
        final var submissions = roundSubmissionRepository.findByPartyIdAndRoundNumber(partyId, round);
        final var votes = roundVoteRepository.findByPartyIdAndRoundNumber(partyId, round);

        final var story = segments.stream().map(segment -> {
            final var author = segment.getAuthorId() == null ? null : members.get(segment.getAuthorId());
            return new StoryLine(
                    segment.getText(),
                    segment.getAuthorId(),
                    author == null ? null : author.getName(),
                    author == null ? null : author.getColor(),
                    segment.isMoral());
        }).toList();

        final var submitted = submissions.stream().map(RoundSubmission::getPlayerId).toList();
        final var voted = votes.stream().map(RoundVote::getVoterId).toList();
        final var scores = members.values().stream()
                .collect(Collectors.toMap(PartyMember::getId, PartyMember::getScore));

        List<SubmissionView> submissionViews = null;
        Map<UUID, Integer> roundPoints = null;
        UUID winnerSubmissionId = null;
        List<UUID> winnerIds = null;

        if (phase == GamePhase.VOTING) {
            // Anonymous: no author info, no counts.
            submissionViews = submissions.stream()
                    .sorted(Comparator.comparingInt(RoundSubmission::getDisplayOrder))
                    .map(s -> new SubmissionView(s.getId(), s.getText(), null, null, null, 0))
                    .toList();
        } else if (phase == GamePhase.REVEAL) {
            final var tally = tally(submissions, votes, round);
            submissionViews = submissions.stream()
                    .sorted(Comparator
                            .comparingInt((RoundSubmission s) -> tally.votesBySubmission().getOrDefault(s.getId(), 0))
                            .reversed()
                            .thenComparingInt(RoundSubmission::getDisplayOrder))
                    .map(s -> {
                        final var author = members.get(s.getPlayerId());
                        return new SubmissionView(
                                s.getId(),
                                s.getText(),
                                s.getPlayerId(),
                                author == null ? "???" : author.getName(),
                                author == null ? null : author.getColor(),
                                tally.votesBySubmission().getOrDefault(s.getId(), 0));
                    })
                    .toList();
            roundPoints = tally.pointsByPlayer();
            winnerSubmissionId = tally.winner() == null ? null : tally.winner().getId();
        } else if (phase == GamePhase.FINISHED) {
            final var best = members.values().stream().mapToInt(PartyMember::getScore).max().orElse(0);
            winnerIds = members.values().stream()
                    .filter(m -> m.getScore() == best)
                    .map(PartyMember::getId)
                    .toList();
        }

        return new GameSnapshot(
                round,
                props.totalRounds(),
                phase,
                state.getPhaseEndsAt().toEpochMilli(),
                props.maxSubmissionLength(),
                prompt(phase, round),
                story,
                submissionViews,
                submitted,
                voted,
                scores,
                roundPoints,
                winnerSubmissionId,
                winnerIds);
    }

    public void broadcast(UUID partyId) {
        final var snapshot = snapshot(partyId);
        if (snapshot != null) {
            template.convertAndSend("/topic/party/" + partyId + "/game", snapshot);
        }
    }

    /**
     * If every player who still can act this phase has acted, pull the deadline
     * forward so the game moves on after a short grace period. The previously
     * scheduled timer becomes a stale no-op. Returns true if the deadline moved.
     */
    private boolean maybeEndPhaseEarly(GameState state) {
        final var phase = state.getPhase();
        if (phase != GamePhase.SUBMITTING && phase != GamePhase.VOTING) {
            return false;
        }

        final var partyId = state.getId();
        final var round = state.getRoundNumber();
        // Disconnected humans shouldn't keep everyone else waiting.
        final var activeMembers = partyMemberRepository.findByPartyStateId(partyId).stream()
                .filter(m -> m.isBot() || m.isConnected())
                .toList();
        if (activeMembers.isEmpty()) {
            return false;
        }

        final var submissions = roundSubmissionRepository.findByPartyIdAndRoundNumber(partyId, round);

        final boolean everyoneDone;
        if (phase == GamePhase.SUBMITTING) {
            final var submitted = submissions.stream()
                    .map(RoundSubmission::getPlayerId)
                    .collect(Collectors.toSet());
            everyoneDone = activeMembers.stream().allMatch(m -> submitted.contains(m.getId()));
        } else {
            final var voted = roundVoteRepository.findByPartyIdAndRoundNumber(partyId, round).stream()
                    .map(RoundVote::getVoterId)
                    .collect(Collectors.toSet());
            // Only players with something to vote FOR count (no self-votes, so the
            // author of the sole submission can never vote).
            everyoneDone = activeMembers.stream()
                    .filter(m -> submissions.stream().anyMatch(s -> !s.getPlayerId().equals(m.getId())))
                    .allMatch(m -> voted.contains(m.getId()));
        }

        if (!everyoneDone) {
            return false;
        }

        final var newDeadline = Instant.now().plusSeconds(props.earlyAdvanceDelaySeconds());
        if (!newDeadline.isBefore(state.getPhaseEndsAt())) {
            return false;
        }
        gameStateRepository.save(state.withPhaseEndsAt(newDeadline));
        gameTimer.schedule(partyId, round, phase, newDeadline);
        return true;
    }

    // ----------------------------------------------------------------- helpers

    private record TallyResult(
            Map<UUID, Integer> votesBySubmission,
            Map<UUID, Integer> pointsByPlayer,
            RoundSubmission winner) {
    }

    private TallyResult tally(List<RoundSubmission> submissions, List<RoundVote> votes, int round) {
        final var votesBySubmission = new HashMap<UUID, Integer>();
        for (final var vote : votes) {
            votesBySubmission.merge(vote.getSubmissionId(), 1, Integer::sum);
        }

        // Most votes wins; ties go to the earliest submission (then id for determinism).
        final var winner = submissions.stream()
                .max(Comparator
                        .comparingInt((RoundSubmission s) -> votesBySubmission.getOrDefault(s.getId(), 0))
                        .thenComparing(RoundSubmission::getSubmittedAt, Comparator.reverseOrder())
                        .thenComparing(RoundSubmission::getId, Comparator.reverseOrder()))
                .orElse(null);

        final var multiplier = round == props.totalRounds() ? props.finalRoundMultiplier() : 1;
        final var pointsByPlayer = new HashMap<UUID, Integer>();
        for (final var submission : submissions) {
            final var count = votesBySubmission.getOrDefault(submission.getId(), 0);
            if (count > 0) {
                pointsByPlayer.merge(submission.getPlayerId(), count * multiplier, Integer::sum);
            }
        }
        if (winner != null) {
            pointsByPlayer.merge(winner.getPlayerId(), props.winnerBonus() * multiplier, Integer::sum);
        }

        return new TallyResult(votesBySubmission, pointsByPlayer, winner);
    }

    private String prompt(GamePhase phase, int round) {
        return switch (phase) {
            case SUBMITTING -> round == props.totalRounds()
                    ? "BONUS ROUND — double points! What is the moral of the story?"
                    : "Continue the story!";
            case VOTING -> round == props.totalRounds()
                    ? "Vote for the best moral!"
                    : "Vote for your favourite!";
            case REVEAL -> "The votes are in!";
            case FINISHED -> "The End!";
        };
    }

    private String sanitize(String rawText) {
        if (rawText == null) {
            return null;
        }
        var text = rawText.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > props.maxSubmissionLength()) {
            text = text.substring(0, props.maxSubmissionLength());
        }
        return text;
    }

    private boolean isMemberOf(UUID partyId, UUID playerId) {
        return partyMemberRepository.findById(playerId)
                .filter(m -> m.getPartyState().getId().equals(partyId))
                .isPresent();
    }

    private Map<UUID, PartyMember> membersById(UUID partyId) {
        return partyMemberRepository.findByPartyStateId(partyId).stream()
                .collect(Collectors.toMap(PartyMember::getId, m -> m));
    }

    private List<String> storyTexts(UUID partyId) {
        return storySegmentRepository.findByPartyIdOrderByPositionAsc(partyId).stream()
                .map(StorySegment::getText)
                .toList();
    }

    private List<SubmitTask> submitTasks(UUID partyId, int round, Instant deadline) {
        final var story = storyTexts(partyId);
        return membersById(partyId).values().stream()
                .filter(PartyMember::isBot)
                .map(bot -> new SubmitTask(
                        partyId,
                        round,
                        bot.getId(),
                        bot.getBotPersona(),
                        story,
                        round == props.totalRounds(),
                        props.maxSubmissionLength(),
                        deadline))
                .toList();
    }

    private List<VoteTask> voteTasks(UUID partyId, int round, List<RoundSubmission> submissions, Instant deadline) {
        final var story = storyTexts(partyId);
        return membersById(partyId).values().stream()
                .filter(PartyMember::isBot)
                .map(bot -> new VoteTask(
                        partyId,
                        round,
                        bot.getId(),
                        bot.getBotPersona(),
                        story,
                        submissions.stream()
                                .filter(s -> !s.getPlayerId().equals(bot.getId()))
                                .sorted(Comparator.comparingInt(RoundSubmission::getDisplayOrder))
                                .map(s -> new Candidate(s.getId(), s.getText()))
                                .toList(),
                        deadline))
                .filter(task -> !task.candidates().isEmpty())
                .toList();
    }

    private void deleteGameData(UUID partyId) {
        if (gameStateRepository.existsById(partyId)) {
            gameStateRepository.deleteById(partyId);
        }
        storySegmentRepository.deleteByPartyId(partyId);
        roundSubmissionRepository.deleteByPartyId(partyId);
        roundVoteRepository.deleteByPartyId(partyId);
        logger.debug("deleted game data for party {}", partyId);
    }
}
