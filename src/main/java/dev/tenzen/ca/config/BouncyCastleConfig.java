package dev.tenzen.ca.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

/**
 * Registra o provider Bouncy Castle uma única vez, antes de qualquer uso criptográfico.
 */
@Configuration
public class BouncyCastleConfig {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
