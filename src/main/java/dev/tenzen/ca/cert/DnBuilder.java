package dev.tenzen.ca.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;

/**
 * Subject DN por geração.
 *
 * <p>Legado RFB (Leiaute v5.0): {@code CN=<NOME>:<doc>} com a cadeia de OUs do leiaute;
 * e-CNPJ leva ainda L (cidade sem acentos) e ST (UF). Nova geração (DOC-ICP-04 v8.3):
 * {@code CN=<nome>} + {@code serialNumber=<doc>} (ETSI EN 319 412-2/-3).</p>
 */
public final class DnBuilder {

    /**
     * CNPJ fictício da AR simulada (base 99999999, DV válido) para a OU① do DN legado.
     */
    public static final String AR_CNPJ = "99999999000191";

    /**
     * String oficial do leiaute, reproduzida verbatim (com travessão).
     */
    public static final String RFB_OU = "Secretaria da Receita Federal do Brasil – RFB";

    public static final String OU_TESTES = "Tenzen CA de Testes";

    /**
     * CN legado: nome em até 52 posições + ':' + CPF(11); razão em até 49 + ':' + CNPJ(14).
     */
    private static final int MAX_PF_NAME = 52;
    private static final int MAX_PJ_NAME = 49;

    private DnBuilder() {
    }

    public static X500Name build(CertificateProfile profile, SubjectData data) {
        return switch (profile.generation()) {
            case LEGACY_RFB -> profile.holder() == CertificateProfile.Holder.PF
                    ? legacyPf(profile, data)
                    : legacyPj(profile, data);
            case NEW_GEN -> profile.holder() == CertificateProfile.Holder.PF
                    ? newGenPf(data)
                    : newGenPj(data);
        };
    }

    private static X500Name legacyPf(CertificateProfile profile, SubjectData data) {
        String name = truncate(Texts.upperAscii(data.name()), MAX_PF_NAME);
        String cn = name + ":" + Texts.zeroPadLeft(data.cpf(), 11);
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BR")
                .addRDN(BCStyle.O, "ICP-Brasil")
                .addRDN(BCStyle.OU, AR_CNPJ)
                .addRDN(BCStyle.OU, validationType(data))
                .addRDN(BCStyle.OU, RFB_OU)
                .addRDN(BCStyle.OU, "RFB e-CPF " + profile.typeLabel())
                .addRDN(BCStyle.OU, Texts.isBlank(data.domain())
                        ? "EM BRANCO" : data.domain().trim())
                .addRDN(BCStyle.CN, cn)
                .build();
    }

    private static X500Name legacyPj(CertificateProfile profile, SubjectData data) {
        String razao = truncate(Texts.upperAscii(data.razaoSocial()), MAX_PJ_NAME);
        String cn = razao + ":" + SubjectAltNameBuilder.cnpjBlock(data.cnpj());
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BR")
                .addRDN(BCStyle.O, "ICP-Brasil")
                .addRDN(BCStyle.ST, Texts.upperAscii(data.uf()))
                .addRDN(BCStyle.L, Texts.upperAscii(data.city()))
                .addRDN(BCStyle.OU, AR_CNPJ)
                .addRDN(BCStyle.OU, validationType(data))
                .addRDN(BCStyle.OU, "RFB e-CNPJ " + profile.typeLabel())
                .addRDN(BCStyle.OU, RFB_OU)
                .addRDN(BCStyle.CN, cn)
                .build();
    }

    private static X500Name newGenPf(SubjectData data) {
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BR")
                .addRDN(BCStyle.O, "ICP-Brasil")
                .addRDN(BCStyle.OU, OU_TESTES)
                .addRDN(BCStyle.OU, validationType(data))
                .addRDN(IcpBrasilOids.DN_SERIAL_NUMBER, Texts.zeroPadLeft(data.cpf(), 11))
                .addRDN(BCStyle.CN, data.name() == null ? "" : data.name().trim())
                .build();
    }

    private static X500Name newGenPj(SubjectData data) {
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BR")
                .addRDN(BCStyle.O, "ICP-Brasil")
                .addRDN(BCStyle.OU, OU_TESTES)
                .addRDN(IcpBrasilOids.DN_SERIAL_NUMBER, SubjectAltNameBuilder.cnpjBlock(data.cnpj()))
                .addRDN(BCStyle.CN, data.razaoSocial() == null ? "" : data.razaoSocial().trim())
                .build();
    }

    private static String validationType(SubjectData data) {
        return Texts.isBlank(data.validationType()) ? "videoconferencia"
                : data.validationType().trim();
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
