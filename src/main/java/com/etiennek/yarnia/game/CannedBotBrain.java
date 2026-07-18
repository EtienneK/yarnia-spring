package com.etiennek.yarnia.game;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Fallback brain used when no LLM is configured (or the LLM call fails).
 * Picks a random generic-but-fun line so a game is always playable.
 */
@Component
public class CannedBotBrain implements BotBrain {
    private static final Random RANDOM = new Random();

    private static final List<String> CONTINUATIONS = List.of(
            "and then everything went horribly, hilariously wrong.",
            "but nobody expected the llama.",
            "which, as everyone knows, is illegal on Tuesdays.",
            "so they did the only sensible thing: panic.",
            "and that's when the cheese began to whisper.",
            "unfortunately, the map was upside down the whole time.",
            "and suddenly, out of nowhere, a suspiciously polite pirate appeared.",
            "then the ground rumbled, and a giant snail slowly gave chase.",
            "but the wizard had already eaten all the snacks.",
            "and the crowd went absolutely mild.",
            "meanwhile, three ducks were plotting something big.",
            "so naturally, they formed a committee about it.",
            "and to everyone's surprise, it started raining soup.",
            "but their phone was at 1%, so things got serious.",
            "and the dragon just wanted a hug, honestly.");

    private static final List<String> MORALS = List.of(
            "Never trust a talking cat.",
            "The real treasure was the snacks we ate along the way.",
            "Always read the fine print, especially on cursed scrolls.",
            "If in doubt, blame the llama.",
            "A committee is no substitute for courage.",
            "Never bring soup to a sword fight.",
            "The early bird gets the worm, but the late snail sees the sunrise.",
            "Home is wherever the wifi connects automatically.");

    @Override
    public String continueStory(String persona, List<String> story, boolean moralRound, int maxLen) {
        final var lines = moralRound ? MORALS : CONTINUATIONS;
        final var text = lines.get(RANDOM.nextInt(lines.size()));
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    @Override
    public int pickVote(String persona, List<String> story, List<String> candidates) {
        return -1; // let the coordinator pick randomly
    }

    private static final List<String> CHAT_LINES = List.of(
            "lol",
            "haha nice",
            "ok that was a good one",
            "robbed. absolutely robbed",
            "no way that won",
            "gg",
            "im using that one next time",
            "this story took a turn",
            "cant believe you all voted for that");

    @Override
    public String chatReply(String persona, List<String> story, List<String> recentChat, String eventContext) {
        // Stay quiet most of the time; canned bots have little to say.
        if (RANDOM.nextDouble() < 0.65) {
            return null;
        }
        return CHAT_LINES.get(RANDOM.nextInt(CHAT_LINES.size()));
    }
}
