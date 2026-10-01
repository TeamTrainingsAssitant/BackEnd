package de.trainingsplan.backend.config;

import de.trainingsplan.backend.security.TokenAuthenticationFilter;
import de.trainingsplan.backend.service.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private static final String NOT_AUTHENTICATED = "{\"message\":\"Not authenticated.\"}";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Öffentlich sind nur Registrierung und Login/Logout (das Logout-Token steht im Body).
     * Alle anderen Endpunkte erfordern ein gültiges Bearer-Token.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/auth").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> unauthorized(response))
                        .accessDeniedHandler((request, response, ex) -> unauthorized(response)))
                .addFilterBefore(new TokenAuthenticationFilter(tokenService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(NOT_AUTHENTICATED);
    }
}
