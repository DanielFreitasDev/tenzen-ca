package dev.tenzen.ca.cert;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalização de texto para os campos de largura fixa do leiaute.
 */
final class Texts {

    private Texts() {
    }

    /**
     * Maiúsculas sem acento (conteúdo dos campos de dados e do CN legado).
     */
    static String upperAscii(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /**
     * Campo numérico: zero-fill à esquerda; ausente = tudo zero. Trunca à direita se exceder.
     */
    static String zeroPadLeft(String value, int width) {
        String digits = digitsOnly(value);
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        }
        return "0".repeat(width - digits.length()) + digits;
    }

    /**
     * Campo texto: maiúsculas sem acento, espaços à direita até a largura.
     */
    static String spacePadRight(String value, int width) {
        String text = upperAscii(value);
        if (text.length() > width) {
            text = text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
