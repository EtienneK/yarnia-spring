package com.etiennek.yarnia.party;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public final class Utils {
    public static final String generatePlayerName() {
        return "Player#" + Integer.toString((int) Math.floor(Math.random() * 10000));
    }

    private static final List<String> botNames = Arrays.asList(
            "CozyPanda",
            "SnackWizard",
            "TinyMeteor",
            "JellyRocket",
            "SunnyLag",
            "CaptainMochi",
            "NoodleStorm",
            "MintyPixel",
            "ToastMode",
            "HappyBonk",
            "WaffleQuest",
            "PogoPilot",
            "CloudSprout",
            "BobaKnight",
            "SillyCactus",
            "TurboPebble",
            "ButtonMasher",
            "PickleDrift",
            "MangoZap",
            "SleepyComet",
            "BubbleLoot",
            "CrispyOtter",
            "LemonCheckpoint",
            "DinoSnack",
            "MochiQuest",
            "SnackPacket",
            "NiftyBanana",
            "RustyJoystick",
            "PuddlePilot",
            "CherryRespawn",
            "TinyTornado",
            "PancakeDash",
            "WobbleFox",
            "CosmicNugget",
            "MelonBrawler",
            "LazyVolt",
            "BouncyKiwi",
            "GrumpyTofu",
            "PixelPickles",
            "SneezyDragon");

    public static final String generateBotName() {
        return botNames.get(new Random().nextInt(botNames.size())) + new Random().nextInt(100);
    }

    private static final List<String> botPersonas = Arrays.asList(
            "You are a 54 year old plumber from Manchester. You like fishing, darts and your grandkids. Your humour is dad jokes and puns, and you're not sorry about it.",
            "You are a 28 year old nurse who runs half marathons and watches trashy reality TV to decompress. Your humour is dry and sarcastic.",
            "You are a university student studying history who stays up too late gaming. Your humour is absurd and very online.",
            "You are a retired primary school teacher who gardens and does the daily crossword. Your humour is gentle and witty with the occasional surprisingly cheeky remark.",
            "You are a software developer and coffee snob. Your humour is deadpan and understated, and you never use exclamation marks.",
            "You are a 35 year old chef who loves heavy metal and motorbikes. Your humour is loud and a bit crude but never mean.",
            "You are a mum of two toddlers running on roughly four hours of sleep. Your humour is self deprecating and painfully relatable.",
            "You are a 61 year old farmer. Practical, a person of few words, with bone dry humour that sneaks up on people.",
            "You are a bubbly teaching assistant who does amateur theatre on weekends. You love wordplay and harmless silliness.",
            "You are an accountant who secretly writes crime novels on the train. Your humour is dark but subtle.");

    public static final String generateBotPersona() {
        return botPersonas.get(new Random().nextInt(botPersonas.size()));
    }

    /**
     * One slot per player (max party size is 8). Each color is a CSS light-dark()
     * pair: the light hex is legible on white, the dark hex on daisyUI's dark
     * base backgrounds (all >= 4:1 contrast, hues validated as distinguishable
     * in both modes). Slot order is deliberate: the earliest slots are the most
     * mutually distinct, and adjacent slots are never look-alike hues.
     */
    private static final String[] PLAYER_COLOR_PALETTE = {
            "light-dark(#2a78d6, #3987e5)", // blue
            "light-dark(#008300, #33a02c)", // green
            "light-dark(#b12f86, #d954ab)", // magenta
            "light-dark(#c74e14, #d95926)", // orange
            "light-dark(#00889b, #00a4ad)", // cyan
            "light-dark(#9a6b00, #c98500)", // gold
            "light-dark(#6f42c1, #a678e8)", // violet
            "light-dark(#c93231, #e66767)", // red
    };

    /** Pick the first palette slot no current party member is using. */
    public static final String pickPlayerColor(Collection<String> takenColors) {
        for (final var color : PLAYER_COLOR_PALETTE) {
            if (!takenColors.contains(color)) {
                return color;
            }
        }
        // Party size can't exceed the palette, but never break on a full house.
        return PLAYER_COLOR_PALETTE[new Random().nextInt(PLAYER_COLOR_PALETTE.length)];
    }
}
