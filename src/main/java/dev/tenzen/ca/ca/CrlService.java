package dev.tenzen.ca.ca;

import dev.tenzen.ca.issuance.IssuanceRepository;
import dev.tenzen.ca.issuance.IssuedCertificate;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.X509CRL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * CRLs da cadeia: a da Intermediária lista os titulares revogados (reasonCode por
 * entrada, AKI, CRLNumber monotônico persistido, nextUpdate de 24h) e a da Raiz cobre
 * a Intermediária (vazia, nextUpdate de 30 dias). CRL inicial vazia no bootstrap,
 * regeneração a cada revogação e agendada antes do vencimento; publicação por troca
 * atômica de referência.
 */
@Service
public class CrlService implements InitializingBean {

    public static final Duration CA_CRL_LIFETIME = Duration.ofHours(24);
    public static final Duration ROOT_CRL_LIFETIME = Duration.ofDays(30);
    private static final Duration RENEW_MARGIN = Duration.ofHours(2);

    private static final Logger log = LoggerFactory.getLogger(CrlService.class);

    private final CaMaterialManager caMaterial;
    private final IssuanceRepository issuanceRepository;
    private final CrlStateRepository stateRepository;

    private volatile byte[] caCrlBytes;
    private volatile byte[] rootCrlBytes;
    private volatile Instant caCrlNextUpdate = Instant.EPOCH;
    private volatile Instant rootCrlNextUpdate = Instant.EPOCH;

    public CrlService(CaMaterialManager caMaterial, IssuanceRepository issuanceRepository,
                      CrlStateRepository stateRepository) {
        this.caMaterial = caMaterial;
        this.issuanceRepository = issuanceRepository;
        this.stateRepository = stateRepository;
    }

    private static byte[] sign(JcaX509v2CRLBuilder builder, PrivateKey key) throws Exception {
        X509CRLHolder holder = builder.build(new JcaContentSignerBuilder(
                CaCertificateFactory.CA_SIGNATURE_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(key));
        return holder.getEncoded();
    }

    @Override
    public void afterPropertiesSet() {
        rebuildCaCrl();
        rebuildRootCrl();
    }

    public byte[] caCrl() {
        return caCrlBytes;
    }

    public byte[] rootCrl() {
        return rootCrlBytes;
    }

    /**
     * Reconstrói a CRL da Intermediária com todos os revogados do histórico.
     */
    @Transactional
    public synchronized void rebuildCaCrl() {
        try {
            Instant now = Instant.now();
            Instant nextUpdate = now.plus(CA_CRL_LIFETIME);
            JcaX509v2CRLBuilder builder = new JcaX509v2CRLBuilder(
                    caMaterial.issuingCertificate(), Date.from(now));
            builder.setNextUpdate(Date.from(nextUpdate));

            List<IssuedCertificate> revoked =
                    issuanceRepository.findByStatusOrderByIssuedAtDesc(
                            IssuedCertificate.Status.REVOKED);
            for (IssuedCertificate cert : revoked) {
                builder.addCRLEntry(new BigInteger(cert.getSerialHex(), 16),
                        Date.from(cert.getRevokedAt()),
                        cert.getRevocationReasonCode() == null ? 0
                                : cert.getRevocationReasonCode());
            }

            long number = nextCrlNumber("ca");
            builder.addExtension(Extension.cRLNumber, false,
                    new CRLNumber(BigInteger.valueOf(number)));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(
                            caMaterial.issuingCertificate().getPublicKey()));

            caCrlBytes = sign(builder, caMaterial.issuingKey());
            caCrlNextUpdate = nextUpdate;
            log.info("CRL da AC publicada: CRLNumber={}, {} revogado(s), nextUpdate={}",
                    number, revoked.size(), nextUpdate);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar a CRL da AC", e);
        }
    }

    /**
     * CRL da Raiz (cobre a Intermediária; sempre vazia nesta AC de teste).
     */
    @Transactional
    public synchronized void rebuildRootCrl() {
        try {
            Instant now = Instant.now();
            Instant nextUpdate = now.plus(ROOT_CRL_LIFETIME);
            JcaX509v2CRLBuilder builder = new JcaX509v2CRLBuilder(
                    caMaterial.rootCertificate(), Date.from(now));
            builder.setNextUpdate(Date.from(nextUpdate));
            builder.addExtension(Extension.cRLNumber, false,
                    new CRLNumber(BigInteger.valueOf(nextCrlNumber("root"))));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(
                            caMaterial.rootCertificate().getPublicKey()));

            rootCrlBytes = sign(builder, caMaterial.rootKey());
            rootCrlNextUpdate = nextUpdate;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar a CRL da Raiz", e);
        }
    }

    /**
     * Renova as CRLs antes de {@code nextUpdate} vencer.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000L)
    public void renewIfNearExpiry() {
        Instant threshold = Instant.now().plus(RENEW_MARGIN);
        if (caCrlNextUpdate.isBefore(threshold)) {
            rebuildCaCrl();
        }
        if (rootCrlNextUpdate.isBefore(threshold)) {
            rebuildRootCrl();
        }
    }

    private long nextCrlNumber(String issuer) {
        CrlState state = stateRepository.findById(issuer).orElseGet(() -> new CrlState(issuer));
        long number = state.next();
        stateRepository.save(state);
        return number;
    }

    /**
     * Versão parseada, para testes e diagnósticos.
     */
    public X509CRL caCrlParsed() {
        try {
            return new JcaX509CRLConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCRL(new X509CRLHolder(caCrlBytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
