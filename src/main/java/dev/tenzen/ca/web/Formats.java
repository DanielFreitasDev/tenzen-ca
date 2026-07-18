package dev.tenzen.ca.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Formatação pt-BR usada nos templates via {@code @fmt}. */
@Component("fmt")
public class Formats {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(ZONE));
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(ZONE));
    }

    /** Serial em grupos de 4 para leitura (ex.: 4F3A 90C1 ...). */
    public String serial(String serialHex) {
        if (serialHex == null) {
            return "";
        }
        return serialHex.toUpperCase().replaceAll("(.{4})(?=.)", "$1 ");
    }
}
