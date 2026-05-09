package com.etiennek.yarnia.party;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.etiennek.yarnia.Greeting;

@Controller
@MessageMapping("/topic/party")
public class PartyWsController {
    @MessageMapping("{partyId}/snapshot")
    public Greeting getSnapshot(@DestinationVariable String partyId) {
        return new Greeting("Hello, " + partyId + "!");
    }
}
