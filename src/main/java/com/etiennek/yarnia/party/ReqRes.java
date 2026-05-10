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
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class CreatePartyRequest {
        @Length(min = 1, max = 12)
        private final String playerName;
    }

    @Data
    @RequiredArgsConstructor
    public static class CreatePartyResponse {
        @NonNull
        private final UUID partyId;
        @NonNull
        private final UUID playerId;
        @NonNull
        private final String playerName;
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
        @Length(min = 1, max = 12)
        private final String playerName;
    }

    @Data
    @RequiredArgsConstructor
    public static class JoinPartyResponse {
        @NonNull
        private final UUID partyId;
        @NonNull
        private final UUID playerId;
        @NonNull
        private final String playerName;
        @NonNull
        private final UUID joinToken;
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class VerifyJoinRequest {
        @NotNull
        private final UUID partyId;
        @NotNull
        private final UUID playerId;
        @NotNull
        private final UUID joinToken;

        public VerifyJoinRequest(String partyId, String playerId, String joinToken) {
            this.partyId = UUID.fromString(partyId);
            this.playerId = UUID.fromString(playerId);
            this.joinToken = UUID.fromString(joinToken);
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class VerifyJoinResponse {
        private final boolean allowed;
    }

    @Data
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @RequiredArgsConstructor
    public static class ClosePartyRequest {
        @NotNull
        private final UUID partyId;
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
}
