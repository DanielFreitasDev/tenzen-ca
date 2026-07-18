package dev.tenzen.ca.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gera um nonce por requisição e publica a CSP. Inline scripts só rodam com o nonce
 * (usado pelo anti-FOUC do tema em layout/base.html, via atributo de request {@code cspNonce}).
 */
@Component
public class CspNonceFilter extends OncePerRequestFilter {

    public static final String ATTR = "cspNonce";

    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute(ATTR, nonce);
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'nonce-" + nonce + "'; "
                + "style-src 'self'; img-src 'self' data:; font-src 'self'; "
                + "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; "
                + "base-uri 'self'; form-action 'self'");
        filterChain.doFilter(request, response);
    }
}
