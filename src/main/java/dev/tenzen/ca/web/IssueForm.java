package dev.tenzen.ca.web;

import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.SubjectData;
import dev.tenzen.ca.identity.Cnpj;
import dev.tenzen.ca.identity.Cpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.validation.Errors;

/** Formulário de emissão. Campos exigidos variam com o perfil; ver {@link #validate}. */
public class IssueForm {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @NotBlank(message = "Escolha o perfil do certificado")
    private String profileId = CertificateProfile.RFB_ECPF_A1.id();

    /* pessoa física */
    private String nome;
    private String cpf;
    private String nascimento;
    private String nis;
    private String rg;
    private String rgOrgaoUf;
    private String tituloEleitor;
    private String tituloZona;
    private String tituloSecao;
    private String ceiNit;

    /* pessoa jurídica */
    private String razaoSocial;
    private String cnpj;
    private String cei;
    private String responsavelNome;
    private String responsavelCpf;
    private String responsavelNascimento;
    private String responsavelNis;
    private String responsavelRg;
    private String responsavelRgOrgaoUf;

    /* comuns */
    private String cidade;
    private String uf;
    private String email;
    private String validationType = "videoconferencia";
    private String domain;

    /* validade */
    private String validadeModo = "perfil";
    private Integer validadeAnos = 1;
    private Integer validadeDias = 365;

    /* exportação */
    @NotBlank(message = "Defina a senha do .p12")
    @Size(min = 4, max = 64, message = "A senha deve ter entre 4 e 64 caracteres")
    private String senha;

    private String senhaConfirma;

    @Size(max = 64, message = "Alias longo demais")
    private String alias = "certificado";

    public CertificateProfile profile() {
        return CertificateProfile.fromId(profileId);
    }

    public boolean laboratory() {
        return "laboratorio".equals(validadeModo);
    }

    /** Regras dependentes do perfil, aplicadas após as anotações. */
    public void validate(Errors errors) {
        CertificateProfile profile;
        try {
            profile = profile();
        } catch (IllegalArgumentException e) {
            errors.rejectValue("profileId", "perfil.invalido", "Perfil desconhecido");
            return;
        }

        if (profile.holder() == CertificateProfile.Holder.PF) {
            if (isBlank(nome)) {
                errors.rejectValue("nome", "obrigatorio", "Informe o nome do titular");
            }
            if (!Cpf.isValid(cpf)) {
                errors.rejectValue("cpf", "cpf.invalido", "CPF inválido (confira os dígitos)");
            }
            checkDate(errors, "nascimento", nascimento);
        } else {
            if (isBlank(razaoSocial)) {
                errors.rejectValue("razaoSocial", "obrigatorio", "Informe a razão social");
            }
            if (!Cnpj.isValid(cnpj)) {
                errors.rejectValue("cnpj", "cnpj.invalido", "CNPJ inválido (confira os dígitos)");
            }
            if (isBlank(responsavelNome)) {
                errors.rejectValue("responsavelNome", "obrigatorio",
                        "Informe o responsável pelo certificado");
            }
            if (!Cpf.isValid(responsavelCpf)) {
                errors.rejectValue("responsavelCpf", "cpf.invalido",
                        "CPF do responsável inválido");
            }
            checkDate(errors, "responsavelNascimento", responsavelNascimento);
            if (profile.legacy()) {
                if (isBlank(cidade)) {
                    errors.rejectValue("cidade", "obrigatorio",
                            "O e-CNPJ leva a cidade no DN (campo L)");
                }
                if (isBlank(uf)) {
                    errors.rejectValue("uf", "obrigatorio", "O e-CNPJ leva a UF no DN (campo ST)");
                }
            }
        }

        if (!isBlank(email) && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.rejectValue("email", "email.invalido", "E-mail inválido");
        }

        if (laboratory()) {
            if (validadeDias == null || validadeDias < 1
                    || validadeDias > dev.tenzen.ca.issuance.IssuanceService.MAX_LAB_DAYS) {
                errors.rejectValue("validadeDias", "validade.invalida",
                        "Entre 1 e 7300 dias no modo laboratório");
            }
        } else if (validadeAnos == null || validadeAnos < 1
                || validadeAnos > profile.maxValidityYears()) {
            errors.rejectValue("validadeAnos", "validade.invalida",
                    "Este perfil permite de 1 a " + profile.maxValidityYears() + " anos");
        }

        if (senha != null && !senha.equals(senhaConfirma)) {
            errors.rejectValue("senhaConfirma", "senha.diferente", "As senhas não conferem");
        }
    }

    private static void checkDate(Errors errors, String field, String value) {
        if (parseDate(value) == null && !isBlank(value)) {
            errors.rejectValue(field, "data.invalida", "Use o formato dd/mm/aaaa");
        }
    }

    public SubjectData toSubjectData() {
        return SubjectData.builder()
                .name(nome)
                .cpf(Cpf.strip(cpf))
                .birthDate(parseDate(nascimento))
                .nis(nis)
                .rg(rg)
                .rgIssuerUf(rgOrgaoUf)
                .voterId(tituloEleitor)
                .voterZone(tituloZona)
                .voterSection(tituloSecao)
                .ceiNit(ceiNit)
                .razaoSocial(razaoSocial)
                .cnpj(Cnpj.strip(cnpj))
                .companyCei(cei)
                .responsibleName(responsavelNome)
                .responsibleCpf(Cpf.strip(responsavelCpf))
                .responsibleBirthDate(parseDate(responsavelNascimento))
                .responsibleNis(responsavelNis)
                .responsibleRg(responsavelRg)
                .responsibleRgIssuerUf(responsavelRgOrgaoUf)
                .city(cidade)
                .uf(uf)
                .email(email)
                .validationType(validationType)
                .domain(domain)
                .build();
    }

    private static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), BR_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getProfileId() { return profileId; }
    public void setProfileId(String v) { this.profileId = v; }
    public String getNome() { return nome; }
    public void setNome(String v) { this.nome = v; }
    public String getCpf() { return cpf; }
    public void setCpf(String v) { this.cpf = v; }
    public String getNascimento() { return nascimento; }
    public void setNascimento(String v) { this.nascimento = v; }
    public String getNis() { return nis; }
    public void setNis(String v) { this.nis = v; }
    public String getRg() { return rg; }
    public void setRg(String v) { this.rg = v; }
    public String getRgOrgaoUf() { return rgOrgaoUf; }
    public void setRgOrgaoUf(String v) { this.rgOrgaoUf = v; }
    public String getTituloEleitor() { return tituloEleitor; }
    public void setTituloEleitor(String v) { this.tituloEleitor = v; }
    public String getTituloZona() { return tituloZona; }
    public void setTituloZona(String v) { this.tituloZona = v; }
    public String getTituloSecao() { return tituloSecao; }
    public void setTituloSecao(String v) { this.tituloSecao = v; }
    public String getCeiNit() { return ceiNit; }
    public void setCeiNit(String v) { this.ceiNit = v; }
    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String v) { this.razaoSocial = v; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String v) { this.cnpj = v; }
    public String getCei() { return cei; }
    public void setCei(String v) { this.cei = v; }
    public String getResponsavelNome() { return responsavelNome; }
    public void setResponsavelNome(String v) { this.responsavelNome = v; }
    public String getResponsavelCpf() { return responsavelCpf; }
    public void setResponsavelCpf(String v) { this.responsavelCpf = v; }
    public String getResponsavelNascimento() { return responsavelNascimento; }
    public void setResponsavelNascimento(String v) { this.responsavelNascimento = v; }
    public String getResponsavelNis() { return responsavelNis; }
    public void setResponsavelNis(String v) { this.responsavelNis = v; }
    public String getResponsavelRg() { return responsavelRg; }
    public void setResponsavelRg(String v) { this.responsavelRg = v; }
    public String getResponsavelRgOrgaoUf() { return responsavelRgOrgaoUf; }
    public void setResponsavelRgOrgaoUf(String v) { this.responsavelRgOrgaoUf = v; }
    public String getCidade() { return cidade; }
    public void setCidade(String v) { this.cidade = v; }
    public String getUf() { return uf; }
    public void setUf(String v) { this.uf = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getValidationType() { return validationType; }
    public void setValidationType(String v) { this.validationType = v; }
    public String getDomain() { return domain; }
    public void setDomain(String v) { this.domain = v; }
    public String getValidadeModo() { return validadeModo; }
    public void setValidadeModo(String v) { this.validadeModo = v; }
    public Integer getValidadeAnos() { return validadeAnos; }
    public void setValidadeAnos(Integer v) { this.validadeAnos = v; }
    public Integer getValidadeDias() { return validadeDias; }
    public void setValidadeDias(Integer v) { this.validadeDias = v; }
    public String getSenha() { return senha; }
    public void setSenha(String v) { this.senha = v; }
    public String getSenhaConfirma() { return senhaConfirma; }
    public void setSenhaConfirma(String v) { this.senhaConfirma = v; }
    public String getAlias() { return alias; }
    public void setAlias(String v) { this.alias = v; }
}
