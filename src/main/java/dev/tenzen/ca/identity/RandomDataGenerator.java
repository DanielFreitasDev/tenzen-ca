package dev.tenzen.ca.identity;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Gera dados fictícios plausíveis para preencher o formulário de emissão.
 * Existe para que ninguém precise digitar dados pessoais reais num certificado de teste.
 */
@Component
public class RandomDataGenerator {

    private static final List<String> FIRST_NAMES = List.of(
            "Ana", "Beatriz", "Bruno", "Camila", "Carlos", "Cecília", "Daniel", "Eduarda",
            "Felipe", "Fernanda", "Gabriel", "Helena", "Igor", "Joana", "João", "Larissa",
            "Lucas", "Mariana", "Mateus", "Otávio", "Paula", "Rafael", "Renata", "Sofia",
            "Thiago", "Valentina", "Vinícius", "Vitória");

    private static final List<String> SURNAMES = List.of(
            "Almeida", "Barbosa", "Cardoso", "Carvalho", "Castro", "Costa", "Dias", "Ferreira",
            "Fonseca", "Gomes", "Lima", "Martins", "Medeiros", "Mendes", "Monteiro", "Moreira",
            "Nogueira", "Oliveira", "Pereira", "Ramos", "Ribeiro", "Rocha", "Santana", "Santos",
            "Silva", "Souza", "Teixeira", "Vieira");

    private static final List<String> COMPANY_CORES = List.of(
            "Horizonte", "Aurora", "Vetor", "Cerrado", "Litoral", "Mirante", "Jangada",
            "Alvorada", "Baobá", "Sertão", "Maracatu", "Delta", "Quartzo", "Ipê", "Mandacaru");

    private static final List<String> COMPANY_SEGMENTS = List.of(
            "Tecnologia", "Logística", "Alimentos", "Engenharia", "Consultoria",
            "Distribuidora", "Comércio", "Serviços Digitais", "Construções", "Transportes");

    private static final List<String> COMPANY_SUFFIXES = List.of("LTDA", "S.A.", "ME", "EIRELI");

    private static final List<String> RG_ISSUERS = List.of("SSP", "PC", "DETRAN", "IFP");

    private static final List<String> UFS = List.of(
            "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT",
            "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");

    private static final List<String> CITIES = List.of(
            "Fortaleza", "Sao Paulo", "Rio de Janeiro", "Belo Horizonte", "Recife", "Salvador",
            "Curitiba", "Porto Alegre", "Manaus", "Belem", "Goiania", "Natal", "Teresina",
            "Joao Pessoa", "Maceio", "Aracaju", "Campinas", "Sobral", "Juazeiro do Norte");

    private final RandomGenerator random = new SecureRandom();

    private static String slug(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    public PersonData person() {
        String first = pick(FIRST_NAMES);
        String middle = pick(SURNAMES);
        String last = pick(SURNAMES);
        while (last.equals(middle)) {
            last = pick(SURNAMES);
        }
        String name = first + " " + middle + " " + last;
        LocalDate birth = LocalDate.of(1955 + random.nextInt(50), 1 + random.nextInt(12),
                1 + random.nextInt(28));
        String uf = pick(UFS);
        return new PersonData(
                name,
                Cpf.generate(random),
                birth,
                digits(9),
                pick(RG_ISSUERS) + uf,
                digits(11),
                digits(12),
                digits(3),
                digits(4),
                pick(CITIES),
                uf,
                email(name));
    }

    public CompanyData company(boolean alphanumericCnpj) {
        String core = pick(COMPANY_CORES);
        String segment = pick(COMPANY_SEGMENTS);
        String razao = core + " " + segment + " " + pick(COMPANY_SUFFIXES);
        String cnpj = alphanumericCnpj
                ? Cnpj.generateAlphanumeric(random)
                : Cnpj.generateNumeric(random);
        String city = pick(CITIES);
        return new CompanyData(razao, cnpj, digits(12), city, pick(UFS),
                "contato@" + slug(core) + slug(segment.split(" ")[0]) + ".com.br");
    }

    private String email(String name) {
        String[] parts = name.split(" ");
        return slug(parts[0]) + "." + slug(parts[parts.length - 1]) + "@exemplo.com.br";
    }

    private String digits(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    public record PersonData(String name, String cpf, LocalDate birthDate, String rg,
                             String rgIssuerUf, String nis, String voterId, String voterZone,
                             String voterSection, String city, String uf, String email) {
    }

    public record CompanyData(String razaoSocial, String cnpj, String cei, String city,
                              String uf, String email) {
    }
}
