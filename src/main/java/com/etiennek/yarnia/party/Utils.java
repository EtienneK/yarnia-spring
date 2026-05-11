package com.etiennek.yarnia.party;

public final class Utils {
    public static final String generatePlayerName() {
        return "Player#" + Integer.toString((int) Math.floor(Math.random() * 10000));
    }
}
