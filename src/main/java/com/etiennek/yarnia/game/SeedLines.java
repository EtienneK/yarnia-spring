package com.etiennek.yarnia.game;

import java.util.List;
import java.util.Random;

/** Random opening lines that seed round 1. */
public final class SeedLines {
    private static final Random RANDOM = new Random();

    private static final List<String> LINES = List.of(
            "Long, long ago, in a land far away,",
            "It was a dark and stormy night, and",
            "Once upon a time, deep in the forest,",
            "Nobody believed the rumours about the old lighthouse until",
            "On the morning of the great cheese festival,",
            "The day the internet stopped working,",
            "Captain Zog stepped off the spaceship and immediately",
            "In the sleepy village of Muddlewick,",
            "The wizard woke up late, again, and",
            "At the bottom of the ocean, a tiny crab",
            "The year is 3024, and humanity's last hope is",
            "Grandma always said never to open the cellar door, but",
            "The world's worst detective finally got a case:",
            "Every single pigeon in the city suddenly",
            "According to the ancient prophecy,");

    public static String random() {
        return LINES.get(RANDOM.nextInt(LINES.size()));
    }

    private SeedLines() {
    }
}
