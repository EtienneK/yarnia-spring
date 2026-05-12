package com.etiennek.yarnia.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.validator.constraints.Length;

import com.etiennek.yarnia.party.Constants.PartyPhase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public final class ReqRes {
    @Data
    @RequiredArgsConstructor
    public static class CreatePartyResponse {
        @NonNull
        private final UUID partyId;
        @NonNull
        private final UUID playerId;
        @NonNull
        private final String joinCode;
        @NonNull
        private final UUID joinToken;
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class JoinPartyRequest {
        @NotBlank
        @Length(min = 6, max = 6)
        private final String joinCode;
    }

    @Data
    @RequiredArgsConstructor
    public static class JoinPartyResponse {
        @NonNull
        private final UUID partyId;
        @NonNull
        private final UUID playerId;
        @NonNull
        private final UUID joinToken;
    }

    @Data
    @RequiredArgsConstructor
    public static class PartyMemberSnapshotResponse {
        @NonNull
        private final String name;
        @NonNull
        private final String color;
        private final boolean isHost;
        private final boolean isReady;
        private final boolean connected;
        private final boolean isBot;
        @NonNull
        private final UUID partyId;
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class GetPartySnapshotRequest {
        @NotNull
        private final UUID partyId;

        public GetPartySnapshotRequest(String partyId) {
            this.partyId = UUID.fromString(partyId);
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class GetPartySnapshotResponse {
        @NonNull
        private final PartyPhase partyPhase;
        @NonNull
        private final Map<UUID, PartyMemberSnapshotResponse> members = new HashMap<>();
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class AddMemberRequest {
        @NotNull
        private final UUID partyId;
        @NotNull
        private final UUID playerId;
        @NotNull
        private final String playerName;
        private final boolean isBot;
        private String botPersona;

        public AddMemberRequest(String partyId, String playerId, String playerName, boolean isBot) {
            this.partyId = UUID.fromString(partyId);
            this.playerId = UUID.fromString(playerId);
            this.playerName = playerName;
            this.isBot = isBot;
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class AddMemberResponse {
        private final boolean added;
        private final String errorCode;
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class AddBotRequest {
        @NotNull
        private final UUID partyId;

        public AddBotRequest(String partyId) {
            this.partyId = UUID.fromString(partyId);
        }
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class RemoveMemberRequest {
        @NotNull
        private final UUID partyId;
        @NotNull
        private final UUID playerId;

        public RemoveMemberRequest(String partyId, String playerId) {
            this.partyId = UUID.fromString(partyId);
            this.playerId = UUID.fromString(playerId);
        }
    }
}
