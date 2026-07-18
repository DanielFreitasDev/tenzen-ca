package dev.tenzen.ca.cert;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;

/**
 * OIDs da ICP-Brasil usados nos certificados (árvore 2.16.76.1).
 * Fontes: Leiaute dos Certificados da AC-RFB v5.0 e DOC-ICP-04 v8.3
 * (detalhes campo a campo em docs/standards.md).
 */
public final class IcpBrasilOids {

    /**
     * e-CPF: dados do titular PF (nascimento, CPF, NIS, RG, órgão/UF).
     */
    public static final ASN1ObjectIdentifier PF_DADOS_TITULAR =
            new ASN1ObjectIdentifier("2.16.76.1.3.1");
    /**
     * e-CNPJ: nome do responsável pelo certificado.
     */
    public static final ASN1ObjectIdentifier PJ_NOME_RESPONSAVEL =
            new ASN1ObjectIdentifier("2.16.76.1.3.2");
    /**
     * e-CNPJ: CNPJ da empresa titular.
     */
    public static final ASN1ObjectIdentifier PJ_CNPJ =
            new ASN1ObjectIdentifier("2.16.76.1.3.3");
    /**
     * e-CNPJ: dados PF do responsável (mesmo layout do 2.16.76.1.3.1, órgão/UF em 10 posições).
     */
    public static final ASN1ObjectIdentifier PJ_DADOS_RESPONSAVEL =
            new ASN1ObjectIdentifier("2.16.76.1.3.4");
    /**
     * e-CPF: título de eleitor (inscrição, zona, seção, município/UF).
     */
    public static final ASN1ObjectIdentifier PF_TITULO_ELEITOR =
            new ASN1ObjectIdentifier("2.16.76.1.3.5");
    /**
     * e-CPF: CEI/INSS (NIT) do titular.
     */
    public static final ASN1ObjectIdentifier PF_CEI =
            new ASN1ObjectIdentifier("2.16.76.1.3.6");
    /**
     * e-CNPJ: CEI da empresa.
     */
    public static final ASN1ObjectIdentifier PJ_CEI =
            new ASN1ObjectIdentifier("2.16.76.1.3.7");
    /**
     * Ramo das políticas de certificado (2.16.76.1.2.tipo.numero-da-AC).
     */
    public static final String POLICY_ARC = "2.16.76.1.2";
    /**
     * EKU opcional do e-CPF: Microsoft Smartcard Logon.
     */
    public static final ASN1ObjectIdentifier MS_SMARTCARD_LOGON =
            new ASN1ObjectIdentifier("1.3.6.1.4.1.311.20.2.2");
    /**
     * serialNumber do DN (nova geração, ETSI EN 319 412).
     */
    public static final ASN1ObjectIdentifier DN_SERIAL_NUMBER =
            new ASN1ObjectIdentifier("2.5.4.5");

    private IcpBrasilOids() {
    }
}
