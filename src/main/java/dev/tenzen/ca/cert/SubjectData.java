package dev.tenzen.ca.cert;

import java.time.LocalDate;

/**
 * Dados do titular informados no formulário. Campos não aplicáveis ao perfil
 * escolhido ficam nulos; os builders de DN/SAN aplicam zero-fill conforme o leiaute.
 */
public record SubjectData(
        /* pessoa física (e-CPF / nova geração PF) */
        String name,
        String cpf,
        LocalDate birthDate,
        String nis,
        String rg,
        String rgIssuerUf,
        String voterId,
        String voterZone,
        String voterSection,
        String ceiNit,

        /* pessoa jurídica (e-CNPJ / Selo Eletrônico) */
        String razaoSocial,
        String cnpj,
        String companyCei,
        String responsibleName,
        String responsibleCpf,
        LocalDate responsibleBirthDate,
        String responsibleNis,
        String responsibleRg,
        String responsibleRgIssuerUf,

        /* comuns */
        String city,
        String uf,
        String email,
        /** OU② do DN legado: presencial, videoconferencia ou certificado digital. */
        String validationType,
        /** OU⑤ do e-CPF legado: domínio; se ausente, entra o literal "EM BRANCO". */
        String domain) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String cpf;
        private LocalDate birthDate;
        private String nis;
        private String rg;
        private String rgIssuerUf;
        private String voterId;
        private String voterZone;
        private String voterSection;
        private String ceiNit;
        private String razaoSocial;
        private String cnpj;
        private String companyCei;
        private String responsibleName;
        private String responsibleCpf;
        private LocalDate responsibleBirthDate;
        private String responsibleNis;
        private String responsibleRg;
        private String responsibleRgIssuerUf;
        private String city;
        private String uf;
        private String email;
        private String validationType = "videoconferencia";
        private String domain;

        public Builder name(String v) { this.name = v; return this; }
        public Builder cpf(String v) { this.cpf = v; return this; }
        public Builder birthDate(LocalDate v) { this.birthDate = v; return this; }
        public Builder nis(String v) { this.nis = v; return this; }
        public Builder rg(String v) { this.rg = v; return this; }
        public Builder rgIssuerUf(String v) { this.rgIssuerUf = v; return this; }
        public Builder voterId(String v) { this.voterId = v; return this; }
        public Builder voterZone(String v) { this.voterZone = v; return this; }
        public Builder voterSection(String v) { this.voterSection = v; return this; }
        public Builder ceiNit(String v) { this.ceiNit = v; return this; }
        public Builder razaoSocial(String v) { this.razaoSocial = v; return this; }
        public Builder cnpj(String v) { this.cnpj = v; return this; }
        public Builder companyCei(String v) { this.companyCei = v; return this; }
        public Builder responsibleName(String v) { this.responsibleName = v; return this; }
        public Builder responsibleCpf(String v) { this.responsibleCpf = v; return this; }
        public Builder responsibleBirthDate(LocalDate v) { this.responsibleBirthDate = v; return this; }
        public Builder responsibleNis(String v) { this.responsibleNis = v; return this; }
        public Builder responsibleRg(String v) { this.responsibleRg = v; return this; }
        public Builder responsibleRgIssuerUf(String v) { this.responsibleRgIssuerUf = v; return this; }
        public Builder city(String v) { this.city = v; return this; }
        public Builder uf(String v) { this.uf = v; return this; }
        public Builder email(String v) { this.email = v; return this; }
        public Builder validationType(String v) { this.validationType = v; return this; }
        public Builder domain(String v) { this.domain = v; return this; }

        public SubjectData build() {
            return new SubjectData(name, cpf, birthDate, nis, rg, rgIssuerUf, voterId,
                    voterZone, voterSection, ceiNit, razaoSocial, cnpj, companyCei,
                    responsibleName, responsibleCpf, responsibleBirthDate, responsibleNis,
                    responsibleRg, responsibleRgIssuerUf, city, uf, email, validationType,
                    domain);
        }
    }
}
