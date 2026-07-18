package dev.tenzen.ca.ca;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Contador monotônico do CRLNumber, persistido por emissor ("ca" e "root").
 */
@Entity
@Table(name = "crl_state")
public class CrlState {

    @Id
    private String issuer;

    private long crlNumber;

    protected CrlState() {
    }

    public CrlState(String issuer) {
        this.issuer = issuer;
        this.crlNumber = 0;
    }

    public long next() {
        return ++crlNumber;
    }

    public String getIssuer() {
        return issuer;
    }

    public long getCrlNumber() {
        return crlNumber;
    }
}
