package ma.ensa.aistudyassistant.study;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GenerateRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, RateState> states = new ConcurrentHashMap<>();
    private final int capacity;
    private final long windowMs;

    public GenerateRateLimitFilter(
            @Value("${ratelimit.generate.capacity:10}") long capacity,
            @Value("${ratelimit.generate.refill-seconds:60}") long refillSeconds
    ) {
        this.capacity = (int) Math.max(1, Math.min(Integer.MAX_VALUE, capacity));
        this.windowMs = Math.max(1000L, refillSeconds * 1000L);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/api/study/generate".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = resolveKey(request);

        if (tryConsume(key)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isBlank()) {
            return "user:" + auth.getName().toLowerCase();
        }

        String ip = request.getRemoteAddr();
        return ip == null ? "ip:unknown" : "ip:" + ip;
    }

    private boolean tryConsume(String key) {
        long now = System.currentTimeMillis();
        RateState state = states.computeIfAbsent(key, ignored -> new RateState(now));

        synchronized (state) {
            long windowStart = state.windowStartMs.get();
            if (now - windowStart >= windowMs) {
                state.windowStartMs.set(now);
                state.count.set(0);
            }

            int current = state.count.incrementAndGet();
            return current <= capacity;
        }
    }

    private static class RateState {
        private final AtomicLong windowStartMs;
        private final AtomicInteger count;

        private RateState(long now) {
            this.windowStartMs = new AtomicLong(now);
            this.count = new AtomicInteger(0);
        }
    }
}

