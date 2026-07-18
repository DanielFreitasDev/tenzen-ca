package dev.tenzen.ca.cert;

/**
 * Perfis emitíveis: geração × pessoa × tipo. O legado RFB (emissão permitida até
 * 02/03/2029) é o default por ser o que as aplicações consomem hoje; a nova geração
 * (DOC-ICP-04 v8.3, desde 01/11/2024) usa CN=nome + serialNumber=documento.
 */
public enum CertificateProfile {

    RFB_ECPF_A1("rfb-ecpf-a1", "RFB e-CPF A1", Generation.LEGACY_RFB, Holder.PF,
            "A1", "1", 2048, 1, 3),
    RFB_ECPF_A3("rfb-ecpf-a3", "RFB e-CPF A3", Generation.LEGACY_RFB, Holder.PF,
            "A3", "3", 2048, 1, 5),
    RFB_ECNPJ_A1("rfb-ecnpj-a1", "RFB e-CNPJ A1", Generation.LEGACY_RFB, Holder.PJ,
            "A1", "1", 2048, 1, 3),
    RFB_ECNPJ_A3("rfb-ecnpj-a3", "RFB e-CNPJ A3", Generation.LEGACY_RFB, Holder.PJ,
            "A3", "3", 2048, 1, 5),
    NG_PF_A3("ng-pf-a3", "Nova geração PF A3", Generation.NEW_GEN, Holder.PF,
            "A3", "3", 2048, 1, 5),
    NG_PF_A4("ng-pf-a4", "Nova geração PF A4", Generation.NEW_GEN, Holder.PF,
            "A4", "4", 4096, 1, 6),
    NG_PJ_SE_S("ng-pj-se-s", "Selo Eletrônico SE-S", Generation.NEW_GEN, Holder.PJ,
            "SE-S", "201", 2048, 1, 1),
    NG_PJ_SE_H("ng-pj-se-h", "Selo Eletrônico SE-H", Generation.NEW_GEN, Holder.PJ,
            "SE-H", "202", 2048, 1, 5);

    private final String id;
    private final String label;
    private final Generation generation;
    private final Holder holder;
    private final String typeLabel;
    private final String policyBranch;
    private final int keyBits;
    private final int defaultValidityYears;
    private final int maxValidityYears;

    CertificateProfile(String id, String label, Generation generation, Holder holder,
                       String typeLabel, String policyBranch, int keyBits, int defaultValidityYears,
                       int maxValidityYears) {
        this.id = id;
        this.label = label;
        this.generation = generation;
        this.holder = holder;
        this.typeLabel = typeLabel;
        this.policyBranch = policyBranch;
        this.keyBits = keyBits;
        this.defaultValidityYears = defaultValidityYears;
        this.maxValidityYears = maxValidityYears;
    }

    public static CertificateProfile fromId(String id) {
        for (CertificateProfile profile : values()) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Perfil desconhecido: " + id);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Generation generation() {
        return generation;
    }

    public Holder holder() {
        return holder;
    }

    /**
     * A1, A3, A4, SE-S ou SE-H; compõe o rótulo de OU no DN legado.
     */
    public String typeLabel() {
        return typeLabel;
    }

    /**
     * Ramo do policy OID: 2.16.76.1.2.&lt;branch&gt;.&lt;numero-da-AC&gt;.
     */
    public String policyBranch() {
        return policyBranch;
    }

    public int keyBits() {
        return keyBits;
    }

    public int defaultValidityYears() {
        return defaultValidityYears;
    }

    /**
     * Teto normativo (Tabela 6 da Res. 212/2025 para a nova geração; PC históricas no legado).
     */
    public int maxValidityYears() {
        return maxValidityYears;
    }

    public boolean legacy() {
        return generation == Generation.LEGACY_RFB;
    }

    /**
     * EKU Microsoft Smartcard Logon: opcional e restrito ao e-CPF legado.
     */
    public boolean includeSmartcardLogon() {
        return legacy() && holder == Holder.PF;
    }

    /**
     * Largura do campo órgão emissor/UF nos otherNames .3.1/.3.4 (6 no e-CPF legado, 10 nos demais).
     */
    public int rgIssuerWidth() {
        return legacy() && holder == Holder.PF ? 6 : 10;
    }

    public enum Generation {LEGACY_RFB, NEW_GEN}

    public enum Holder {PF, PJ}
}
