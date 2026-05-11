package com.etiennek.yarnia.config;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import org.apache.catalina.realm.GenericPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.WebUtils;

import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.ReqRes.AddMemberRequest;
import com.etiennek.yarnia.party.ReqRes.AddMemberResponse;
import com.etiennek.yarnia.party.repos.PartyJoinTokenRepository;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;
import com.etiennek.yarnia.party.repos.PartyStateRepository;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    private @Autowired PartyStateRepository partyStateRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired PartyJoinTokenRepository partyJoinTokenRepository;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new MyChannelInterceptor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "https://3k.local.etkhome.com")
                .setHandshakeHandler(new CustomHandshakeHandler())
                .withSockJS();
    }

    // TOREAD:
    // https://stackoverflow.com/questions/21312222/how-to-reply-to-unauthenticated-user-in-spring-4-stomp-over-websocket-configurat
    // https://stackoverflow.com/questions/25082148/spring-websockets-sendtouser-without-login

    class CustomHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {

            final var partyId = getCookieValue(request, "partyId");
            final var playerId = getCookieValue(request, "playerId");
            final var joinToken = getCookieValue(request, "joinToken");

            try {
                verifyJoinRequest(partyId, playerId, joinToken, null, null);
                return new GenericPrincipal(playerId);
            } catch (MessagingException e) {
                logger.warn("failed to verify user based on cookies: " + e.getMessage());
                return null;
            }
        }

        private String getCookieValue(ServerHttpRequest request, String name) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                final var cookie = WebUtils.getCookie(servletRequest.getServletRequest(), name);
                if (cookie == null)
                    return null;
                return cookie.getValue();
            }
            return null;
        }
    }

    public class MyChannelInterceptor implements ChannelInterceptor {
        private static final Logger logger = LoggerFactory.getLogger(MyChannelInterceptor.class);

        private static final AntPathMatcher patternMatcher = new AntPathMatcher();
        private static final String subscribePartyDestinationPattern = "/topic/party/{partyId}/{route}";

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            StompCommand command = accessor.getCommand();

            if (StompCommand.CONNECT.equals(command)) {
                final var partyId = accessor.getFirstNativeHeader("partyId");
                final var joinToken = accessor.getFirstNativeHeader("joinToken");
                final var playerId = accessor.getFirstNativeHeader("playerId");

                if (partyId != null && joinToken != null && playerId != null) {
                    verifyJoinRequest(partyId, playerId, joinToken, command, null);

                    final var attributes = accessor.getSessionAttributes();
                    attributes.put(partyId + ".playerId", playerId);
                    attributes.put(partyId + ".joinToken", joinToken);
                    accessor.setSessionAttributes(attributes);

                    final var playerName = accessor.getFirstNativeHeader("playerName");
                    final var addMemberResponse = addMember(new AddMemberRequest(
                            partyId,
                            playerId,
                            playerName));
                    if (!addMemberResponse.isAdded()) {
                        throw new MessagingException(addMemberResponse.getErrorCode());
                    }
                } else {
                    logger.warn("missing attributes for CONNECT command");
                    throw new MessagingException("forbidden");
                }
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                final var destination = accessor.getDestination();

                if (destination.startsWith("/topic/party")) {
                    final var templateVariables = patternMatcher
                            .extractUriTemplateVariables(subscribePartyDestinationPattern, destination);
                    final var partyId = templateVariables.get("partyId");
                    final var attributes = accessor.getSessionAttributes();
                    final var playerId = (String) attributes.get(partyId + ".playerId");
                    final var joinToken = (String) attributes.get(partyId + ".joinToken");

                    verifyJoinRequest(partyId, playerId, joinToken, command, destination);
                }
            }

            return message;
        }

        public AddMemberResponse addMember(AddMemberRequest request) {
            final var partyId = request.getPartyId();
            final var playerId = request.getPlayerId();
            // Pessimistic lock
            final var partyState = partyStateRepository
                    .findById(partyId)
                    .orElseThrow(() -> new IllegalStateException("party state does not exist"));

            final var members = partyMemberRepository.findByPartyStateId(partyId);

            if (members.size() >= 8) {
                return new AddMemberResponse(false, "full");
            }

            final var playerName = request.getPlayerName() == null ? generatePlayerName() : request.getPlayerName();
            final var isHost = members.stream().filter(m -> m.isHost() && !m.getId().equals(playerId)).count() <= 0;

            partyMemberRepository.save(new PartyMember(
                    playerId,
                    playerName,
                    getPlayerColor(playerId),
                    isHost,
                    false,
                    true,
                    partyState));

            return new AddMemberResponse(true, null);
        }

    }

    private void verifyJoinRequest(String partyId, String playerId, String joinToken, StompCommand command,
            String destination) {
        var isAllowed = false;
        if (partyId != null && playerId != null && joinToken != null) {
            try {
                isAllowed = partyJoinTokenRepository.existsByIdAndPartyIdAndPlayerId(
                UUID.fromString(joinToken),
                UUID.fromString(partyId),
                UUID.fromString(playerId));
            } catch (IllegalArgumentException e) {
                isAllowed = false;
            }
        }

        if (!isAllowed) {
            logger.warn("user unauthorized for command [" + command + "] at destination [" + destination + "]");
            throw new MessagingException("forbidden");
        }
    }

    private String generatePlayerName() {
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

    private String getPlayerColor(UUID playerId) {
        final var index = ((playerId.hashCode() % PLAYER_COLOR_PALETTE.length) + PLAYER_COLOR_PALETTE.length)
                % PLAYER_COLOR_PALETTE.length;
        return PLAYER_COLOR_PALETTE[index];
    }
}
