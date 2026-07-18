package dev.tenzen.ca.web;

import dev.tenzen.ca.identity.RandomDataGenerator;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dados fictícios para o formulário de emissão (evita digitar dados pessoais reais). */
@RestController
public class RandomDataController {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RandomDataGenerator generator;

    public RandomDataController(RandomDataGenerator generator) {
        this.generator = generator;
    }

    @GetMapping("/api/dados-aleatorios/pessoa")
    public Map<String, String> person() {
        RandomDataGenerator.PersonData p = generator.person();
        return Map.ofEntries(
                Map.entry("nome", p.name().toUpperCase()),
                Map.entry("cpf", p.cpf()),
                Map.entry("nascimento", BR_DATE.format(p.birthDate())),
                Map.entry("rg", p.rg()),
                Map.entry("rgOrgaoUf", p.rgIssuerUf()),
                Map.entry("nis", p.nis()),
                Map.entry("tituloEleitor", p.voterId()),
                Map.entry("tituloZona", p.voterZone()),
                Map.entry("tituloSecao", p.voterSection()),
                Map.entry("cidade", p.city()),
                Map.entry("uf", p.uf()),
                Map.entry("email", p.email()));
    }

    @GetMapping("/api/dados-aleatorios/empresa")
    public Map<String, String> company(
            @RequestParam(name = "cnpjAlfanumerico", defaultValue = "false") boolean alphanumeric) {
        RandomDataGenerator.CompanyData c = generator.company(alphanumeric);
        RandomDataGenerator.PersonData responsible = generator.person();
        return Map.ofEntries(
                Map.entry("razaoSocial", c.razaoSocial().toUpperCase()),
                Map.entry("cnpj", c.cnpj()),
                Map.entry("cei", c.cei()),
                Map.entry("cidade", c.city()),
                Map.entry("uf", c.uf()),
                Map.entry("email", c.email()),
                Map.entry("responsavelNome", responsible.name().toUpperCase()),
                Map.entry("responsavelCpf", responsible.cpf()),
                Map.entry("responsavelNascimento", BR_DATE.format(responsible.birthDate())),
                Map.entry("responsavelRg", responsible.rg()),
                Map.entry("responsavelRgOrgaoUf", responsible.rgIssuerUf()),
                Map.entry("responsavelNis", responsible.nis()));
    }
}
