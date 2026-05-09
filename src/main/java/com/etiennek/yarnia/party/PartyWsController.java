package com.etiennek.yarnia.party;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.etiennek.yarnia.Greeting;

@Controller
public class PartyWsController {
    @MessageMapping("/party/{partyId}/snapshot")
    @SendToUser(destinations = "/user/{username}/snapshot", broadcast = false)
    public Greeting getSnapshot(@DestinationVariable String partyId) {
        System.out.println("WTF?????????????");
        return new Greeting("Hello, " + partyId + "!");
    }
}
