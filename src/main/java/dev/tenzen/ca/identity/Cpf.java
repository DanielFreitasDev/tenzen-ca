package dev.tenzen.ca.identity;

import java.util.random.RandomGenerator;

/** Validação, geração e formatação de CPF (11 dígitos, DV módulo 11). */
public final class Cpf {

    private Cpf() {
    }

    /** Remove tudo que não for dígito. */
    public static String strip(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static boolean isValid(String cpf) {
        String digits = strip(cpf);
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }
        return dv(digits, 9) == digits.charAt(9) - '0'
                && dv(digits, 10) == digits.charAt(10) - '0';
    }

    private static int dv(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int r = (sum * 10) % 11;
        return r == 10 ? 0 : r;
    }

    public static String generate(RandomGenerator random) {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }
        sb.append(dv(sb.toString(), 9));
        sb.append(dv(sb.toString(), 10));
        String cpf = sb.toString();
        return cpf.chars().distinct().count() == 1 ? generate(random) : cpf;
    }

    public static String format(String cpf) {
        String d = strip(cpf);
        if (d.length() != 11) {
            return cpf;
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }
}
