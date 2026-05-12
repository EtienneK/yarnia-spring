package com.etiennek.yarnia.party;

import java.util.UUID;

public final class Utils {
    public static final String generatePlayerName() {
        return "Player#" + Integer.toString((int) Math.floor(Math.random() * 10000));
    }

    private static final String[] PLAYER_COLOR_PALETTE = {
            "#ff6b6b",
            "#4ecdc4",
            "#45b7d1",
            "#f7b801",
            "#5c7cfa",
            "#20c997",
            "#f06595",
            "#ffa94d",
            "#74c0fc",
            "#94d82d",
            "#e599f7",
            "#ffd43b",
    };

    public static final String getPlayerColor(UUID playerId) {
        final var index = ((playerId.hashCode() % PLAYER_COLOR_PALETTE.length) + PLAYER_COLOR_PALETTE.length)
                % PLAYER_COLOR_PALETTE.length;
        return PLAYER_COLOR_PALETTE[index];
    }
}
