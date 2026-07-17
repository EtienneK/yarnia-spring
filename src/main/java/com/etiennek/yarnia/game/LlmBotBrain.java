package com.etiennek.yarnia.game;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * LLM-backed brain via Spring AI's provider-agnostic ChatModel. The concrete
 * provider (Anthropic, OpenAI, Ollama, ...) is chosen by the starter on the
 * classpath + spring.ai.* config. Falls back to CannedBotBrain when no model
 * is configured or a call fails, so games always keep moving.
 */
@Component
@Primary
public class LlmBotBrain implements BotBrain {
    private static final Logger logger = LoggerFactory.getLogger(LlmBotBrain.class);

    private final ObjectProvider<ChatModel> chatModel;
    private final CannedBotBrain fallback;

    LlmBotBrain(ObjectProvider<ChatModel> chatModel, CannedBotBrain fallback) {
        this.chatModel = chatModel;
        this.fallback = fallback;
    }

    @Override
    public String continueStory(String persona, List<String> story, boolean moralRound, int maxLen) {
        final var model = chatModel.getIfAvailable();
        if (model == null) {
            return fallback.continueStory(persona, story, moralRound, maxLen);
        }
        try {
            final var instruction = moralRound
                    ? "It is the final round: write \"the moral of the story\" for the story so far."
                    : "Write the next short continuation of the story. It must flow naturally from the last line.";
            final var response = model.call(
                    systemPrompt(persona) + "\n\n"
                            + "The story so far:\n" + String.join("\n", story) + "\n\n"
                            + instruction + "\n"
                            + "Reply with ONLY your submission text - no quotes, no explanation. "
                            + "At most " + maxLen + " characters. Be funny and a little unexpected.");
            final var text = clean(response, maxLen);
            if (text.isEmpty()) {
                return fallback.continueStory(persona, story, moralRound, maxLen);
            }
            return text;
        } catch (Exception e) {
            logger.warn("LLM continueStory failed, using canned fallback: {}", e.getMessage());
            return fallback.continueStory(persona, story, moralRound, maxLen);
        }
    }

    @Override
    public int pickVote(String persona, List<String> story, List<String> candidates) {
        final var model = chatModel.getIfAvailable();
        if (model == null) {
            return fallback.pickVote(persona, story, candidates);
        }
        try {
            final var sb = new StringBuilder();
            for (var i = 0; i < candidates.size(); i++) {
                sb.append(i + 1).append(". ").append(candidates.get(i)).append("\n");
            }
            final var response = model.call(
                    systemPrompt(persona) + "\n\n"
                            + "The story so far:\n" + String.join("\n", story) + "\n\n"
                            + "These are the candidate continuations:\n" + sb
                            + "\nWhich one is the funniest / best? Reply with ONLY its number.");
            final var pick = Integer.parseInt(response.replaceAll("[^0-9]", "").trim()) - 1;
            return (pick >= 0 && pick < candidates.size()) ? pick : fallback.pickVote(persona, story, candidates);
        } catch (Exception e) {
            logger.warn("LLM pickVote failed, using canned fallback: {}", e.getMessage());
            return fallback.pickVote(persona, story, candidates);
        }
    }

    private String systemPrompt(String persona) {
        final var who = (persona == null || persona.isBlank())
                ? "You are an ordinary person with a good sense of humour."
                : persona;
        return who + " You are playing Yarnia, an online party game where players write a silly story"
                + " together, one short line at a time, then vote on the best line.\n\n"
                + "You are imitating a real human player typing on their phone. Write like one:\n"
                + "- Casual, natural typing. The occasional small typo, missing apostrophe, lowercase"
                + " sentence start or slightly loose grammar is fine and expected. At most one small"
                + " mistake per message, and plenty of messages have none. Never make big or"
                + " constant mistakes.\n"
                + "- Use ONLY characters found on a normal keyboard: straight quotes, regular hyphens,"
                + " three dots for an ellipsis. Never use em dashes, en dashes, curly quotes, or any"
                + " special symbols or emoji.\n"
                + "- Stay in character. Your personality, interests and sense of humour should colour"
                + " what you write. Never mention being an AI or break character.";
    }

    private String clean(String response, int maxLen) {
        if (response == null) {
            return "";
        }
        var text = response.replaceAll("\\s+", " ").trim();
        // Enforce "normal keyboard" characters even when the model slips: typographic
        // punctuation is a dead LLM giveaway.
        text = text
                .replaceAll("[‘’‚ʼ]", "'")
                .replaceAll("[“”„]", "\"")
                .replaceAll("[–—―]", "-")
                .replace("…", "...")
                .replaceAll("[^\\x20-\\x7E]", "");
        // Strip wrapping quotes the model sometimes adds despite instructions.
        if (text.length() >= 2 && (text.startsWith("\"") && text.endsWith("\"")
                || text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text.length() > maxLen ? text.substring(0, maxLen).trim() : text;
    }
}
