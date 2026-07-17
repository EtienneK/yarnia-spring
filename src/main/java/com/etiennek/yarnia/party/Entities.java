package com.etiennek.yarnia.party;

import java.util.UUID;

import com.etiennek.yarnia.party.Constants.PartyPhase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;

public final class Entities {
    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @Table(indexes = {
        @Index(columnList = "joinCode", unique = true),
    })
    public static class Party {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(nullable = false, unique = true)
        private final String joinCode;

        @Column(nullable = false)
        private final int playerCount;
    }

    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @Table(indexes = {
        @Index(columnList = "partyId"),
        @Index(columnList = "id, partyId, playerId", unique = true)
    })
    public static class PartyJoinToken {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(nullable = false)
        private final UUID partyId;

        @Column(nullable = false)
        private final UUID playerId;
    }

    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @With
    public static class PartyMember {
        @Id
        private final UUID id;

        @Column(nullable = false)
        private final String name;

        @Column(nullable = false)
        private final String color;

        @Column(nullable = false)
        private final boolean isHost;

        @Column(nullable = false)
        private final boolean isReady;

        @Column(nullable = false)
        private final boolean connected;

        @Column(nullable = false)
        private final boolean isBot;

        @Column
        private final String botPersona;

        @Column(nullable = false)
        private final int score;

        @ManyToOne(optional = false)
        private final PartyState partyState;
    }

    @Entity
    @Data
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    @RequiredArgsConstructor
    @With
    public static class PartyState {
        @Id
        private final UUID id;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private final PartyPhase partyPhase;
    }

}
