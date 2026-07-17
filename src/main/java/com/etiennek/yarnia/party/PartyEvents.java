package com.etiennek.yarnia.party;

import java.util.UUID;

public final class PartyEvents {
    /** Published when the host starts the game (party moved WAITING -> PLAYING). */
    public record GameStartedEvent(UUID partyId) {
    }

    /** Published when the party is being fully deleted; listeners must clean up their own state. */
    public record PartyDeletedEvent(UUID partyId) {
    }

    /** Published when a member disconnects during a game (so remaining players aren't kept waiting). */
    public record MemberDisconnectedEvent(UUID partyId) {
    }
}
