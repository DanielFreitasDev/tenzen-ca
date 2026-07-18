package dev.tenzen.ca.identity;

import java.util.random.RandomGenerator;

/**
 * Validação, geração e formatação de CNPJ.
 *
 * <p>Suporta o formato numérico legado e o alfanumérico da IN RFB 2.229/2024
 * (produção a partir de 27/07/2026): as 12 primeiras posições aceitam {@code [0-9A-Z]},
 * os 2 dígitos verificadores continuam numéricos, calculados por módulo 11 sobre o
 * valor {@code (código ASCII - 48)} de cada caractere.</p>
 */
public final class Cnpj {

    private static final int[] W1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] W2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {
    }

    /**
     * Remove máscara, sobe para maiúsculas e mantém apenas {@code [0-9A-Z]}.
     */
    public static String strip(String value) {
        return value == null ? "" : value.toUpperCase().replaceAll("[^0-9A-Z]", "");
    }

    public static boolean isValid(String cnpj) {
        String chars = strip(cnpj);
        if (!chars.matches("[0-9A-Z]{12}\\d{2}") || chars.chars().distinct().count() == 1) {
            return false;
        }
        return dv(chars, W1, 12) == chars.charAt(12) - '0'
                && dv(chars, W2, 13) == chars.charAt(13) - '0';
    }

    private static int dv(String chars, int[] weights, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (chars.charAt(i) - 48) * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    public static String generateNumeric(RandomGenerator random) {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        sb.append("0001"); // matriz, como nos CNPJs reais
        return appendDvs(sb);
    }

    public static String generateAlphanumeric(RandomGenerator random) {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        sb.append("0001");
        return appendDvs(sb);
    }

    private static String appendDvs(StringBuilder base12) {
        base12.append(dv(base12.toString(), W1, 12));
        base12.append(dv(base12.toString(), W2, 13));
        return base12.toString();
    }

    public static String format(String cnpj) {
        String c = strip(cnpj);
        if (c.length() != 14) {
            return cnpj;
        }
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8)
                + "/" + c.substring(8, 12) + "-" + c.substring(12);
    }
}
