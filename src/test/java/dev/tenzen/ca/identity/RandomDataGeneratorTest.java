package dev.tenzen.ca.identity;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RandomDataGeneratorTest {

    // fonte independente da lista do gerador: a UF real de cada município sorteável
    private static final Map<String, String> UF_POR_CIDADE = Map.ofEntries(
            Map.entry("Fortaleza", "CE"),
            Map.entry("Sao Paulo", "SP"),
            Map.entry("Rio de Janeiro", "RJ"),
            Map.entry("Belo Horizonte", "MG"),
            Map.entry("Recife", "PE"),
            Map.entry("Salvador", "BA"),
            Map.entry("Curitiba", "PR"),
            Map.entry("Porto Alegre", "RS"),
            Map.entry("Manaus", "AM"),
            Map.entry("Belem", "PA"),
            Map.entry("Goiania", "GO"),
            Map.entry("Natal", "RN"),
            Map.entry("Teresina", "PI"),
            Map.entry("Joao Pessoa", "PB"),
            Map.entry("Maceio", "AL"),
            Map.entry("Aracaju", "SE"),
            Map.entry("Campinas", "SP"),
            Map.entry("Sobral", "CE"),
            Map.entry("Juazeiro do Norte", "CE"));

    private final RandomDataGenerator generator = new RandomDataGenerator();

    @Test
    void pessoaSorteiaCidadeCoerenteComUf() {
        for (int i = 0; i < 300; i++) {
            RandomDataGenerator.PersonData p = generator.person();
            assertEquals(UF_POR_CIDADE.get(p.city()), p.uf(),
                    "cidade e UF divergem: " + p.city() + "/" + p.uf());
            assertTrue(p.rgIssuerUf().endsWith(p.uf()),
                    "órgão emissor do RG com UF divergente: " + p.rgIssuerUf());
        }
    }

    @Test
    void pessoaGeraCeiNitComDozeDigitos() {
        for (int i = 0; i < 100; i++) {
            String ceiNit = generator.person().ceiNit();
            assertTrue(ceiNit.matches("\\d{12}"), "CEI/NIT fora do formato: " + ceiNit);
        }
    }

    @Test
    void empresaSorteiaCidadeCoerenteComUf() {
        for (int i = 0; i < 300; i++) {
            RandomDataGenerator.CompanyData c = generator.company(false);
            assertEquals(UF_POR_CIDADE.get(c.city()), c.uf(),
                    "cidade e UF divergem: " + c.city() + "/" + c.uf());
        }
    }
}
