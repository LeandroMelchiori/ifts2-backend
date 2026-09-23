package ar.edu.ifts2.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors,
                                           UsuarioJwtAuthenticationConverter authentication) throws Exception {
        return http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                // El token se envia exclusivamente por Authorization; no se autentica con cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/destacados", "/api/galeria", "/api/eventos/*/fotos").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/destacados", "/api/galeria", "/api/eventos/*/fotos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/noticias", "/api/noticias/*").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/noticias", "/api/noticias/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/eventos", "/api/eventos/*", "/api/carreras", "/api/carreras/*",
                                "/api/autoridades", "/api/autoridades/*", "/api/enlaces", "/api/enlaces/*",
                                "/api/documentos", "/api/documentos/*", "/api/institucion").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/eventos", "/api/eventos/*", "/api/carreras", "/api/carreras/*",
                                "/api/autoridades", "/api/autoridades/*", "/api/enlaces", "/api/enlaces/*",
                                "/api/documentos", "/api/documentos/*", "/api/institucion").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/admin/usuarios", "/api/admin/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin", "/api/admin/**").hasAnyRole("ADMIN", "EDITOR")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
