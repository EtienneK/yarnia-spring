package com.etiennek.yarnia.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;

public final class GameEntities {

    /** One row per running game; id == partyId. */
    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @With
    public static class GameState {
        @Id
        private final UUID id;

        @Column(nullable = false)
        private final int roundNumber;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private final GamePhase phase;

        @Column(nullable = false)
        private final Instant phaseEndsAt;
    }

    /** A line of the canonical story. Position 0 is the seed line (authorId null). */
    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @With
    @Table(indexes = {
            @Index(columnList = "partyId"),
            @Index(columnList = "partyId, position", unique = true),
    })
    public static class StorySegment {
        @Id
        private final UUID id;

        @Column(nullable = false)
        private final UUID partyId;

        @Column(nullable = false)
        private final int position;

        @Column(nullable = false, length = 1000)
        private final String text;

        @Column
        private final UUID authorId;

        @Column(nullable = false)
        private final boolean moral;
    }

    /** A player's entry for one round. Editable until the submit deadline. */
    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @With
    @Table(indexes = {
            @Index(columnList = "partyId, roundNumber"),
            @Index(columnList = "partyId, roundNumber, playerId", unique = true),
    })
    public static class RoundSubmission {
        @Id
        private final UUID id;

        @Column(nullable = false)
        private final UUID partyId;

        @Column(nullable = false)
        private final int roundNumber;

        @Column(nullable = false)
        private final UUID playerId;

        @Column(nullable = false, length = 1000)
        private final String text;

        @Column(nullable = false)
        private final Instant submittedAt;

        /** Shuffled order in which submissions are shown during voting. */
        @Column(nullable = false)
        private final int displayOrder;
    }

    /** A player's vote for one round. Changeable until the vote deadline. */
    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @With
    @Table(indexes = {
            @Index(columnList = "partyId, roundNumber"),
            @Index(columnList = "partyId, roundNumber, voterId", unique = true),
    })
    public static class RoundVote {
        @Id
        private final UUID id;

        @Column(nullable = false)
        private final UUID partyId;

        @Column(nullable = false)
        private final int roundNumber;

        @Column(nullable = false)
        private final UUID voterId;

        @Column(nullable = false)
        private final UUID submissionId;
    }
}
