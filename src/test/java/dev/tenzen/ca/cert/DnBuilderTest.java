package dev.tenzen.ca.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.junit.jupiter.api.Test;

class DnBuilderTest {

    private static String[] ouValues(X500Name dn) {
        RDN[] rdns = dn.getRDNs(BCStyle.OU);
        String[] values = new String[rdns.length];
        for (int i = 0; i < rdns.length; i++) {
            values[i] = IETFUtils.valueToString(rdns[i].getFirst().getValue());
        }
        return values;
    }

    private static String single(X500Name dn, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
        RDN[] rdns = dn.getRDNs(oid);
        return rdns.length == 0 ? null : IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    @Test
    void ecpfLegadoMontaCnNomeMaiusculoSemAcentoMaisCpf() {
        SubjectData data = SubjectData.builder()
                .name("João da Silva").cpf("01672780838").build();
        X500Name dn = DnBuilder.build(CertificateProfile.RFB_ECPF_A1, data);
        assertEquals("JOAO DA SILVA:01672780838", single(dn, BCStyle.CN));
        assertEquals("BR", single(dn, BCStyle.C));
        assertEquals("ICP-Brasil", single(dn, BCStyle.O));
    }

    @Test
    void ecpfLegadoTemCincoOusNaOrdemDoLeiaute() {
        SubjectData data = SubjectData.builder()
                .name("Ana").cpf("01672780838").validationType("presencial").build();
        String[] ous = ouValues(DnBuilder.build(CertificateProfile.RFB_ECPF_A3, data));
        assertEquals(5, ous.length);
        assertEquals(DnBuilder.AR_CNPJ, ous[0]);
        assertEquals("presencial", ous[1]);
        assertEquals(DnBuilder.RFB_OU, ous[2]);
        assertEquals("RFB e-CPF A3", ous[3]);
        assertEquals("EM BRANCO", ous[4]);
    }

    @Test
    void ecnpjLegadoTemQuatroOusMaisLocalidadeEUf() {
        SubjectData data = SubjectData.builder()
                .razaoSocial("Casa Liquidação").cnpj("99999999000191")
                .city("São Paulo").uf("SP").build();
        X500Name dn = DnBuilder.build(CertificateProfile.RFB_ECNPJ_A1, data);
        assertEquals("CASA LIQUIDACAO:99999999000191", single(dn, BCStyle.CN));
        assertEquals("SAO PAULO", single(dn, BCStyle.L));
        assertEquals("SP", single(dn, BCStyle.ST));
        String[] ous = ouValues(dn);
        assertEquals(4, ous.length);
        assertEquals(DnBuilder.AR_CNPJ, ous[0]);
        assertEquals("videoconferencia", ous[1]);
        assertEquals("RFB e-CNPJ A1", ous[2]);
        assertEquals(DnBuilder.RFB_OU, ous[3]);
    }

    @Test
    void nomeLongoETruncadoEm52NoEcpf() {
        String longName = "A".repeat(80);
        SubjectData data = SubjectData.builder().name(longName).cpf("01672780838").build();
        String cn = single(DnBuilder.build(CertificateProfile.RFB_ECPF_A1, data), BCStyle.CN);
        assertEquals(52 + 1 + 11, cn.length());
    }

    @Test
    void novaGeracaoPfUsaSerialNumberECnComNomeCivil() {
        SubjectData data = SubjectData.builder()
                .name("João da Silva").cpf("01672780838").build();
        X500Name dn = DnBuilder.build(CertificateProfile.NG_PF_A3, data);
        assertEquals("João da Silva", single(dn, BCStyle.CN));
        assertEquals("01672780838", single(dn, IcpBrasilOids.DN_SERIAL_NUMBER));
    }

    @Test
    void seloEletronicoUsaSerialNumberComCnpj() {
        SubjectData data = SubjectData.builder()
                .razaoSocial("Aurora Tecnologia LTDA").cnpj("99999999000191").build();
        X500Name dn = DnBuilder.build(CertificateProfile.NG_PJ_SE_S, data);
        assertEquals("Aurora Tecnologia LTDA", single(dn, BCStyle.CN));
        assertEquals("99999999000191", single(dn, IcpBrasilOids.DN_SERIAL_NUMBER));
    }
}
