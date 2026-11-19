package fi.haagahelia.smarthhealth.config;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import fi.haagahelia.smarthhealth.repository.AuthSessionRepository;
import fi.haagahelia.smarthhealth.service.TokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private final AuthSessionRepository sessions;

    public BearerTokenFilter(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String rawToken = authorization.substring(7).trim();
            sessions.findFirstByTokenHashAndExpiresAtAfter(TokenUtil.sha256(rawToken), Instant.now())
                    .ifPresent(session -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    session.getUser().getEmail(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + session.getUser().getRole().name())))));
        }
        filterChain.doFilter(request, response);
    }
}