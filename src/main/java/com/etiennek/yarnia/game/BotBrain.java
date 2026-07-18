package com.etiennek.yarnia.game;

import java.util.List;

/**
 * The "mind" of an AI player. Implementations must be provider-agnostic;
 * the LLM-backed implementation goes through Spring AI's ChatModel so the
 * actual provider (Anthropic, OpenAI, Ollama, ...) is a config concern.
 */
public interface BotBrain {

    /** Return the bot's continuation of the story (or its moral, on the final round). */
    String continueStory(String persona, List<String> story, boolean moralRound, int maxLen);

    /** Return the index of the candidate the bot votes for, or -1 to let the caller pick randomly. */
    int pickVote(String persona, List<String> story, List<String> candidates);

    /**
     * Optionally say something in the party chat. eventContext describes what just
     * happened (a player's message, a round result, the game ending). Return null
     * to stay silent - bots should only speak when they have something to say.
     */
    String chatReply(String persona, List<String> story, List<String> recentChat, String eventContext);
}
