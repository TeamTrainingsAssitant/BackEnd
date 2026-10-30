package de.trainingsplan.backend.security;

import de.trainingsplan.backend.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Liest {@code Authorization: Bearer <token>} und authentifiziert den zugehörigen User.
 * Fehlt das Token oder ist es ungültig, bleibt die Anfrage unauthentifiziert; ob das erlaubt ist,
 * entscheidet die Autorisierung in der {@code SecurityConfig}.
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public TokenAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = header.substring(BEARER_PREFIX.length()).trim();
        }
        if (token == null && request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("capsule_session".equals(cookie.getName())) token = cookie.getValue();
            }
        }
        tokenService.findUserByToken(token).ifPresent(user ->
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())));
        chain.doFilter(request, response);
    }
}
