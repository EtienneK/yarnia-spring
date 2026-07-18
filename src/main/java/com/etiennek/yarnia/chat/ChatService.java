package com.etiennek.yarnia.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.etiennek.yarnia.chat.ChatDtos.ChatMessageView;
import com.etiennek.yarnia.chat.repos.ChatMessageRepository;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.PartyEvents;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;

@Service
@Transactional
public class ChatService {
    public static final int MAX_CHAT_LENGTH = 300;

    private @Autowired ChatMessageRepository chatMessageRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired SimpMessagingTemplate template;
    private @Autowired BotChatService botChatService;

    /** Store + broadcast a message; human messages may provoke a bot reply. */
    public void send(UUID partyId, UUID senderId, String rawText) {
        final var sender = partyMemberRepository.findById(senderId)
                .filter(m -> m.getPartyState().getId().equals(partyId))
                .orElse(null);
        if (sender == null) {
            return;
        }
        var text = rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_CHAT_LENGTH) {
            text = text.substring(0, MAX_CHAT_LENGTH);
        }

        final var message = new ChatMessage(UUID.randomUUID(), partyId, senderId, text, Instant.now());
        chatMessageRepository.save(message);
        template.convertAndSend("/topic/party/" + partyId + "/chat", toView(message, membersById(partyId)));

        if (!sender.isBot()) {
            botChatService.onHumanChat(partyId, sender.getName(), text);
        }
    }

    public List<ChatMessageView> history(UUID partyId) {
        final var members = membersById(partyId);
        final var messages = new ArrayList<>(chatMessageRepository.findTop100ByPartyIdOrderByCreatedAtDesc(partyId));
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return messages.stream().map(m -> toView(m, members)).toList();
    }

    /** Recent messages as "Name: text" lines, oldest first — context for bots. */
    public List<String> recentChatLines(UUID partyId) {
        final var members = membersById(partyId);
        final var messages = new ArrayList<>(chatMessageRepository.findTop12ByPartyIdOrderByCreatedAtDesc(partyId));
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return messages.stream()
                .map(m -> memberName(m.getSenderId(), members) + ": " + m.getText())
                .toList();
    }

    @EventListener
    public void onPartyDeleted(PartyEvents.PartyDeletedEvent event) {
        chatMessageRepository.deleteByPartyId(event.partyId());
    }

    private ChatMessageView toView(ChatMessage message, Map<UUID, PartyMember> members) {
        final var sender = members.get(message.getSenderId());
        return new ChatMessageView(
                message.getId(),
                message.getSenderId(),
                memberName(message.getSenderId(), members),
                sender == null ? null : sender.getColor(),
                sender != null && sender.isBot(),
                message.getText(),
                message.getCreatedAt().toEpochMilli());
    }

    private String memberName(UUID senderId, Map<UUID, PartyMember> members) {
        final var member = members.get(senderId);
        return member == null ? "(left)" : member.getName();
    }

    private Map<UUID, PartyMember> membersById(UUID partyId) {
        return partyMemberRepository.findByPartyStateId(partyId).stream()
                .collect(Collectors.toMap(PartyMember::getId, m -> m));
    }
}
