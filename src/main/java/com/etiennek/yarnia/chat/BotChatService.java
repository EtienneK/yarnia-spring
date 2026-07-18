package com.etiennek.yarnia.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.etiennek.yarnia.game.BotBrain;
import com.etiennek.yarnia.game.GameEvents;
import com.etiennek.yarnia.game.repos.StorySegmentRepository;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;

/**
 * Lets AI players take part in the party chat. Bots react to human messages
 * (eagerly when addressed by name), comment on round results, and sign off at
 * game end - but the brain may always PASS, and cooldowns keep them from
 * dominating the room.
 */
@Service
public class BotChatService {
    private static final Logger logger = LoggerFactory.getLogger(BotChatService.class);
    private static final Random RANDOM = new Random();

    private static final Duration COOLDOWN = Duration.ofSeconds(25);
    private static final double REPLY_CHANCE = 0.35;
    private static final double MENTIONED_REPLY_CHANCE = 0.9;
    private static final double REVEAL_COMMENT_CHANCE = 0.35;
    private static final double FINISH_COMMENT_CHANCE = 0.8;

    private final TaskScheduler scheduler;
    private final ObjectProvider<ChatService> chatService; // lazy: ChatService <-> BotChatService
    private final PartyMemberRepository partyMemberRepository;
    private final StorySegmentRepository storySegmentRepository;
    private final BotBrain botBrain;
    private final Map<UUID, Instant> lastSpoke = new ConcurrentHashMap<>();

    BotChatService(TaskScheduler gameTaskScheduler, ObjectProvider<ChatService> chatService,
            PartyMemberRepository partyMemberRepository, StorySegmentRepository storySegmentRepository,
            BotBrain botBrain) {
        this.scheduler = gameTaskScheduler;
        this.chatService = chatService;
        this.partyMemberRepository = partyMemberRepository;
        this.storySegmentRepository = storySegmentRepository;
        this.botBrain = botBrain;
    }

    /** A human said something; a bot might answer (very likely when named). */
    public void onHumanChat(UUID partyId, String senderName, String text) {
        final var bots = bots(partyId);
        if (bots.isEmpty()) {
            return;
        }
        // A bot that was addressed by name answers almost always; otherwise one
        // random bot considers it.
        final var mentioned = bots.stream()
                .filter(b -> text.toLowerCase().contains(b.getName().toLowerCase()))
                .findFirst();
        final var bot = mentioned.orElse(bots.get(RANDOM.nextInt(bots.size())));
        final var chance = mentioned.isPresent() ? MENTIONED_REPLY_CHANCE : REPLY_CHANCE;
        maybeSay(partyId, bot, chance, senderName + " just said in the chat: \"" + text + "\"");
    }

    @EventListener
    public void onRoundRevealed(GameEvents.RoundRevealed event) {
        final var bots = bots(event.partyId());
        if (bots.isEmpty()) {
            return;
        }
        final var bot = bots.get(RANDOM.nextInt(bots.size()));
        maybeSay(event.partyId(), bot, REVEAL_COMMENT_CHANCE,
                "The round's votes were just revealed - the newest line of the story is the round winner.");
    }

    @EventListener
    public void onGameFinished(GameEvents.GameFinished event) {
        final var bots = bots(event.partyId());
        if (bots.isEmpty()) {
            return;
        }
        final var bot = bots.get(RANDOM.nextInt(bots.size()));
        final var winner = partyMemberRepository.findByPartyStateId(event.partyId()).stream()
                .max(Comparator.comparingInt(PartyMember::getScore))
                .map(PartyMember::getName)
                .orElse("someone");
        maybeSay(event.partyId(), bot, FINISH_COMMENT_CHANCE,
                "The game just ended. " + winner + " won. Time for a quick goodbye or a sore-loser remark.");
    }

    private void maybeSay(UUID partyId, PartyMember bot, double chance, String eventContext) {
        if (RANDOM.nextDouble() > chance) {
            return;
        }
        final var last = lastSpoke.get(bot.getId());
        if (last != null && last.plus(COOLDOWN).isAfter(Instant.now())) {
            return;
        }
        // Claim the slot immediately so overlapping triggers don't double-book the bot.
        lastSpoke.put(bot.getId(), Instant.now());

        final var delayMs = 2000 + RANDOM.nextInt(5000);
        scheduler.schedule(() -> {
            try {
                final var story = storySegmentRepository.findByPartyIdOrderByPositionAsc(partyId).stream()
                        .map(s -> s.getText())
                        .toList();
                final var recentChat = chatService.getObject().recentChatLines(partyId);
                final var reply = botBrain.chatReply(bot.getBotPersona(), story, recentChat, eventContext);
                if (reply != null && !reply.isBlank()) {
                    chatService.getObject().send(partyId, bot.getId(), reply);
                }
            } catch (Exception e) {
                logger.warn("bot chat failed for {}: {}", bot.getName(), e.getMessage());
            }
        }, Instant.now().plusMillis(delayMs));
    }

    private List<PartyMember> bots(UUID partyId) {
        return partyMemberRepository.findByPartyStateId(partyId).stream()
                .filter(PartyMember::isBot)
                .toList();
    }
}
