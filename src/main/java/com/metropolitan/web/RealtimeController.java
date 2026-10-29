package com.metropolitan.web;

import com.metropolitan.realtime.TicketService;
import com.metropolitan.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {
    private final TicketService tickets;

    public RealtimeController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/ticket")
    public Map<String, String> ticket(@AuthenticationPrincipal UserPrincipal p) {
        return Map.of("ticket", tickets.issue(p));
    }
}
