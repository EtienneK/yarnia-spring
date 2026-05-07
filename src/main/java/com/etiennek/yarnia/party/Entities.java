package com.etiennek.yarnia.party;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

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
}
