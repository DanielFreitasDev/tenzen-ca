package dev.tenzen.ca.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CpfCnpjTest {

    @Test
    void cpfComDvCorretoEValido() {
        // o exemplo do leiaute (01672780838) é ilustrativo e não fecha o DV;
        // aqui usamos um CPF com DV matematicamente correto
        assertTrue(Cpf.isValid("12345678909"));
        assertTrue(Cpf.isValid("123.456.789-09"));
    }

    @Test
    void cpfComDvErradoEInvalido() {
        assertFalse(Cpf.isValid("01672780839"));
        assertFalse(Cpf.isValid("11111111111"));
        assertFalse(Cpf.isValid("123"));
    }

    @Test
    void cpfGeradoSempreValida() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            String cpf = Cpf.generate(random);
            assertTrue(Cpf.isValid(cpf), "CPF gerado inválido: " + cpf);
        }
    }

    @Test
    void cpfFormata() {
        assertEquals("016.727.808-38", Cpf.format("01672780838"));
    }

    @Test
    void cnpjNumericoConhecidoEValido() {
        // CNPJ da AR fictícia usada no DN (base 99999999, DV calculado módulo 11)
        assertTrue(Cnpj.isValid("99999999000191"));
        assertTrue(Cnpj.isValid("99.999.999/0001-91"));
    }

    @Test
    void cnpjAlfanumericoCalculaDvSobreAscii48() {
        // regra IN RFB 2.229/2024: valor = código ASCII - 48 (A=17 ... Z=42)
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            String cnpj = Cnpj.generateAlphanumeric(random);
            assertTrue(Cnpj.isValid(cnpj), "CNPJ alfanumérico gerado inválido: " + cnpj);
            assertTrue(cnpj.matches("[0-9A-Z]{12}\\d{2}"));
        }
    }

    @Test
    void cnpjGeradoNumericoSempreValida() {
        Random random = new Random(11);
        for (int i = 0; i < 500; i++) {
            assertTrue(Cnpj.isValid(Cnpj.generateNumeric(random)));
        }
    }

    @Test
    void cnpjComDvTrocadoEInvalido() {
        assertFalse(Cnpj.isValid("99999999000192"));
        assertFalse(Cnpj.isValid("00000000000000"));
    }

    @Test
    void cnpjAceitaMinusculasNaEntrada() {
        String cnpj = Cnpj.generateAlphanumeric(new Random(3));
        assertTrue(Cnpj.isValid(cnpj.toLowerCase()));
    }

    @Test
    void cnpjFormata() {
        assertEquals("99.999.999/0001-91", Cnpj.format("99999999000191"));
    }
}
