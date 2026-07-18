package dev.tenzen.ca.cert;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o subjectAltName com os otherNames da ICP-Brasil.
 *
 * <p>Cada otherName é {@code SEQUENCE { type-id OID, value [0] EXPLICIT OCTET STRING }};
 * o leiaute admite OCTET STRING ou PrintableString e aqui usamos OCTET STRING, o mais
 * comum em produção. Campos numéricos ausentes viram zeros na largura do campo; o
 * órgão emissor/UF fica vazio quando não há RG (leiaute v5.0). Larguras:
 * e-CPF legado .3.1 = 51 (órgão/UF 6); .3.4 e nova geração .3.1 = 55 (órgão/UF 10).</p>
 */
public final class SubjectAltNameBuilder {

    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("ddMMyyyy");

    private SubjectAltNameBuilder() {
    }

    public static GeneralNames build(CertificateProfile profile, SubjectData data) {
        List<GeneralName> names = new ArrayList<>();
        if (profile.holder() == CertificateProfile.Holder.PF) {
            names.add(otherName(IcpBrasilOids.PF_DADOS_TITULAR, personBlock(
                    data.birthDate() == null ? null : DDMMAAAA.format(data.birthDate()),
                    data.cpf(), data.nis(), data.rg(), data.rgIssuerUf(),
                    profile.rgIssuerWidth())));
            if (profile.legacy()) {
                names.add(otherName(IcpBrasilOids.PF_TITULO_ELEITOR, voterBlock(data)));
                names.add(otherName(IcpBrasilOids.PF_CEI, Texts.zeroPadLeft(data.ceiNit(), 12)));
            }
        } else {
            names.add(otherName(IcpBrasilOids.PJ_NOME_RESPONSAVEL,
                    Texts.upperAscii(data.responsibleName())));
            names.add(otherName(IcpBrasilOids.PJ_CNPJ, cnpjBlock(data.cnpj())));
            names.add(otherName(IcpBrasilOids.PJ_DADOS_RESPONSAVEL, personBlock(
                    data.responsibleBirthDate() == null ? null
                            : DDMMAAAA.format(data.responsibleBirthDate()),
                    data.responsibleCpf(), data.responsibleNis(), data.responsibleRg(),
                    data.responsibleRgIssuerUf(), 10)));
            names.add(otherName(IcpBrasilOids.PJ_CEI, Texts.zeroPadLeft(data.companyCei(), 12)));
        }
        if (!Texts.isBlank(data.email())) {
            names.add(new GeneralName(GeneralName.rfc822Name, data.email().trim()));
        }
        return new GeneralNames(names.toArray(new GeneralName[0]));
    }

    /**
     * Bloco de dados PF dos otherNames .3.1/.3.4: nascimento DDMMAAAA(8), CPF(11),
     * NIS(11), RG(15) e órgão emissor/UF ({@code rgIssuerWidth} posições; vazio sem RG).
     */
    static String personBlock(String birthDdmmaaaa, String cpf, String nis, String rg,
                              String rgIssuerUf, int rgIssuerWidth) {
        StringBuilder block = new StringBuilder(45 + rgIssuerWidth);
        block.append(Texts.zeroPadLeft(birthDdmmaaaa, 8));
        block.append(Texts.zeroPadLeft(cpf, 11));
        block.append(Texts.zeroPadLeft(nis, 11));
        boolean hasRg = !Texts.isBlank(rg);
        block.append(Texts.zeroPadLeft(rg, 15));
        if (hasRg) {
            block.append(Texts.spacePadRight(rgIssuerUf, rgIssuerWidth));
        }
        return block.toString();
    }

    /**
     * Título de eleitor (.3.5): inscrição(12), zona(3), seção(4), município/UF(22).
     */
    static String voterBlock(SubjectData data) {
        boolean hasVoter = !Texts.isBlank(data.voterId());
        StringBuilder block = new StringBuilder(41);
        block.append(Texts.zeroPadLeft(data.voterId(), 12));
        block.append(Texts.zeroPadLeft(data.voterZone(), 3));
        block.append(Texts.zeroPadLeft(data.voterSection(), 4));
        if (hasVoter) {
            String municipality = Texts.upperAscii(data.city());
            String uf = Texts.upperAscii(data.uf());
            block.append(Texts.spacePadRight(municipality + uf, 22));
        }
        return block.toString();
    }

    /**
     * CNPJ (.3.3): 14 posições; aceita alfanumérico (IN RFB 2.229/2024).
     */
    static String cnpjBlock(String cnpj) {
        String value = cnpj == null ? "" : cnpj.toUpperCase().replaceAll("[^0-9A-Z]", "");
        if (value.length() > 14) {
            value = value.substring(0, 14);
        }
        return "0".repeat(14 - value.length()) + value;
    }

    static GeneralName otherName(ASN1ObjectIdentifier oid, String value) {
        DERSequence sequence = new DERSequence(new ASN1Encodable[]{
                oid,
                new DERTaggedObject(true, 0,
                        new DEROctetString(value.getBytes(StandardCharsets.US_ASCII))),
        });
        return new GeneralName(GeneralName.otherName, sequence);
    }
}
