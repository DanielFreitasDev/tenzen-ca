package dev.tenzen.ca.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * CSRF ativo em todos os POSTs; headers de segurança; acesso liberado por default
 * (app roda em 127.0.0.1). Basic-auth opcional via app.security.basic-auth.* para
 * quando a aplicação for exposta além de localhost.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties props) throws Exception {
        AppProperties.BasicAuth basicAuth = props.security().basicAuth();
        if (basicAuth.enabled()) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/css/**", "/js/**", "/img/**", "/fonts/**",
                                    "/crl/**", "/crl2/**", "/aia/**")
                            .permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(withDefaults());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        http.headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AppProperties props) {
        AppProperties.BasicAuth basicAuth = props.security().basicAuth();
        if (!basicAuth.enabled()) {
            return new InMemoryUserDetailsManager();
        }
        if (basicAuth.password().isBlank()) {
            throw new IllegalStateException(
                    "app.security.basic-auth.enabled=true exige app.security.basic-auth.password");
        }
        return new InMemoryUserDetailsManager(User.builder()
                .username(basicAuth.username())
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                        .encode(basicAuth.password()))
                .build());
    }
}
