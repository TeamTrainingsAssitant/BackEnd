package de.trainingsplan.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.service.TokenService;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class TokenAuthenticationFilterTest {

    private TokenService tokenService;
    private TokenAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        tokenService = mock(TokenService.class);
        filter = new TokenAuthenticationFilter(tokenService);
        user = new User("max", "max@mosbach.de", "hash");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void validBearerTokenAuthenticatesUser() throws Exception {
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));

        MockFilterChain chain = run("Bearer abc");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isSameAs(user);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void schemeIsCaseInsensitive() throws Exception {
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));

        run("bearer abc");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void invalidTokenLeavesRequestUnauthenticatedButContinues() throws Exception {
        when(tokenService.findUserByToken("bad")).thenReturn(Optional.empty());

        MockFilterChain chain = run("Bearer bad");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingHeaderLeavesRequestUnauthenticated() throws Exception {
        MockFilterChain chain = run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void otherAuthSchemeIsIgnored() throws Exception {
        run("Basic abc");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void upperCaseSchemeIsAccepted() throws Exception {
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));

        run("BEARER abc");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void emptyTokenAfterPrefixIsNotAuthenticated() throws Exception {
        // Der Filter reicht auch ein leeres Token weiter; die Prüfung auf "leer" macht der TokenService
        run("Bearer ");
        run("Bearer     ");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenService, times(2)).findUserByToken("");
    }

    @Test
    void headerWithoutSpaceAfterBearerIsIgnored() throws Exception {
        run("Bearer");
        run("Bearerabc");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void tokenIsTrimmedBeforeLookup() throws Exception {
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));

        run("Bearer    abc   ");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(tokenService).findUserByToken("abc");
    }

    @Test
    void authenticatedUserHasNoAuthorities() throws Exception {
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));

        run("Bearer abc");

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
    }

    @Test
    void invalidTokenDoesNotClearExistingAuthentication() throws Exception {
        Authentication existing = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(tokenService.findUserByToken("bad")).thenReturn(Optional.empty());

        run("Bearer bad");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    @Test
    void validTokenReplacesExistingAuthentication() throws Exception {
        User other = new User("anna", "anna@mosbach.de", "hash");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(other, null, List.of()));
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));

        run("Bearer abc");

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Bearer abc", "Bearer bad", "Basic abc", "Bearer "})
    void chainContinuesExactlyOnceInEveryCase(String header) throws Exception {
        when(tokenService.findUserByToken("abc")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("Authorization", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}
