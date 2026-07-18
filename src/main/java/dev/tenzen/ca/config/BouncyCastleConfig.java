package dev.tenzen.ca.config;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

/** Registra o provider Bouncy Castle uma única vez, antes de qualquer uso criptográfico. */
@Configuration
public class BouncyCastleConfig {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
