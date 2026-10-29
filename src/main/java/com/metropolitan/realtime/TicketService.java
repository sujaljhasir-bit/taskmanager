package com.metropolitan.realtime;

import com.metropolitan.security.UserPrincipal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * One-time, 30-second WebSocket tickets. The browser first calls the authenticated REST endpoint,
 * then opens the socket with the ticket, so the long-lived JWT never appears in a URL.
 */
@Service
public class TicketService {
    public record Ticket(Long userId, boolean admin, Instant expires) {}

    private static final long TTL_SECONDS = 30;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(UserPrincipal p) {
        Instant now = Instant.now();
        tickets.values().removeIf(t -> t.expires().isBefore(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(id, new Ticket(p.id(), p.admin(), now.plusSeconds(TTL_SECONDS)));
        return id;
    }

    /** Single use: a redeemed ticket is removed. */
    public Optional<Ticket> redeem(String id) {
        if (id == null) return Optional.empty();
        Ticket t = tickets.remove(id);
        return t == null || t.expires().isBefore(Instant.now()) ? Optional.empty() : Optional.of(t);
    }
}
