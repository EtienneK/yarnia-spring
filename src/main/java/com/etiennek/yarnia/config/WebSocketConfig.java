package com.etiennek.yarnia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.etiennek.yarnia.party.PartyService;
import com.etiennek.yarnia.party.ReqRes.VerifyJoinRequest;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private @Autowired PartyService partyService;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new MyChannelInterceptor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "https://3k.local.etkhome.com")
                .withSockJS();
    }

    public class MyChannelInterceptor implements ChannelInterceptor {
        private static final Logger logger = LoggerFactory.getLogger(MyChannelInterceptor.class);

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            StompCommand command = accessor.getCommand();

            if (StompCommand.CONNECT.equals(command)) {
                final var partyId = accessor.getFirstNativeHeader("partyId");
                final var joinToken = accessor.getFirstNativeHeader("joinToken");
                final var playerId = accessor.getFirstNativeHeader("playerId");

                if (partyId != null && joinToken != null && playerId != null) {
                    final var attributes = accessor.getSessionAttributes();
                    attributes.put(partyId + ".playerId", playerId);
                    attributes.put(partyId + ".joinToken", joinToken);
                    accessor.setSessionAttributes(attributes);
                }
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                final var destination = accessor.getDestination();
                if (destination.startsWith("/topic/party") && destination.endsWith("/snapshot")) {
                    final var partyId = destination
                            .replaceFirst("\\/topic\\/party\\/", "")
                            .replaceFirst("\\/join", "");
                    final var attributes = accessor.getSessionAttributes();
                    final var playerId = (String) attributes.get(partyId + ".playerId");
                    final var joinToken = (String) attributes.get(partyId + ".joinToken");

                    var isAllowed = false;
                    if (partyId != null && playerId != null && joinToken != null) {
                        try {
                            final var verifyJoinResponse = partyService.verifyJoin(
                                    new VerifyJoinRequest(partyId, playerId, joinToken));
                            isAllowed = verifyJoinResponse.isAllowed();
                        } catch (IllegalArgumentException e) {
                            isAllowed = false;
                        }
                    }

                    if (!isAllowed) {
                        logger.warn("unallowed subscribe attempt at destination: " + destination);
                        throw new MessagingException("forbidden");
                    }
                }
            }

            return message;
        }
    }
}
