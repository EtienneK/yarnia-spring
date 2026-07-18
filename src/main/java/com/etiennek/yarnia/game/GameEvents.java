package com.etiennek.yarnia.game;

import java.util.UUID;

public final class GameEvents {
    /** A round's votes were just revealed (the winning line is the story's newest). */
    public record RoundRevealed(UUID partyId) {
    }

    /** The game just ended. */
    public record GameFinished(UUID partyId) {
    }
}
