package dev.tenzen.ca.cert;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SubjectAltNameBuilderTest {

    private static SubjectData fullPerson() {
        return SubjectData.builder()
                .name("João da Silva")
                .cpf("01672780838")
                .birthDate(LocalDate.of(1980, 3, 25))
                .nis("123456789")
                .rg("998877")
                .rgIssuerUf("SSPCE")
                .voterId("1234567890")
                .voterZone("12")
                .voterSection("345")
                .ceiNit("55555")
                .city("Fortaleza")
                .uf("CE")
                .email("joao@exemplo.com.br")
                .build();
    }

    @Test
    void blocoPfLegadoTem51PosicoesComOrgaoUfEm6() {
        String block = SubjectAltNameBuilder.personBlock("25031980", "01672780838",
                "123456789", "998877", "SSPCE", 6);
        assertEquals(51, block.length());
        assertEquals("25031980", block.substring(0, 8));
        assertEquals("01672780838", block.substring(8, 19));
        assertEquals("00123456789", block.substring(19, 30));
        assertEquals("000000000998877", block.substring(30, 45));
        assertEquals("SSPCE ", block.substring(45)); // texto: espaços à direita
    }

    @Test
    void blocoResponsavelUsa10PosicoesDeOrgaoUfTotal55() {
        String block = SubjectAltNameBuilder.personBlock("25031980", "01672780838",
                "123456789", "998877", "SSPCE", 10);
        assertEquals(55, block.length());
        assertEquals("SSPCE     ", block.substring(45));
    }

    @Test
    void camposAusentesViramZeros() {
        String block = SubjectAltNameBuilder.personBlock(null, "01672780838", null, null, null, 6);
        // sem RG o órgão/UF fica vazio: 8+11+11+15 = 45 posições
        assertEquals(45, block.length());
        assertEquals("00000000", block.substring(0, 8));
        assertEquals("00000000000", block.substring(19, 30));
        assertEquals("000000000000000", block.substring(30, 45));
    }

    @Test
    void tituloDeEleitorTem41PosicoesQuandoPresente() {
        String block = SubjectAltNameBuilder.voterBlock(fullPerson());
        assertEquals(41, block.length());
        assertEquals("001234567890", block.substring(0, 12));
        assertEquals("012", block.substring(12, 15));
        assertEquals("0345", block.substring(15, 19));
        assertEquals("FORTALEZACE", block.substring(19, 30));
        assertEquals("           ", block.substring(30)); // completa 22 com espaços
    }

    @Test
    void cnpjAlfanumericoEntraNoBlocoSemMascara() {
        assertEquals("12ABC345000199", SubjectAltNameBuilder.cnpjBlock("12.ABC.345/0001-99"));
        assertEquals("00000000000000", SubjectAltNameBuilder.cnpjBlock(null));
    }

    @Test
    void otherNameCodificaValorComoOctetString() throws Exception {
        GeneralNames san = SubjectAltNameBuilder.build(CertificateProfile.RFB_ECPF_A3, fullPerson());
        GeneralName first = san.getNames()[0];
        assertEquals(GeneralName.otherName, first.getTagNo());
        ASN1Sequence seq = ASN1Sequence.getInstance(first.getName());
        assertEquals(IcpBrasilOids.PF_DADOS_TITULAR, seq.getObjectAt(0));
        ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(seq.getObjectAt(1));
        assertEquals(0, tagged.getTagNo());
        assertInstanceOf(ASN1OctetString.class, tagged.getExplicitBaseObject());
        assertEquals(51,
                ASN1OctetString.getInstance(tagged.getExplicitBaseObject()).getOctets().length);
    }

    @Test
    void sanDoEcpfLegadoTemTresOtherNamesMaisEmail() {
        GeneralNames san = SubjectAltNameBuilder.build(CertificateProfile.RFB_ECPF_A1, fullPerson());
        assertEquals(4, san.getNames().length);
        assertEquals(GeneralName.rfc822Name, san.getNames()[3].getTagNo());
    }

    @Test
    void sanDoEcnpjLegadoTemQuatroOtherNamesMaisEmail() {
        SubjectData company = SubjectData.builder()
                .razaoSocial("Casa Liquidação")
                .cnpj("99999999000191")
                .companyCei("123")
                .responsibleName("Maria Souza")
                .responsibleCpf("01672780838")
                .responsibleBirthDate(LocalDate.of(1975, 12, 1))
                .responsibleRg("112233")
                .responsibleRgIssuerUf("SSPSP")
                .city("São Paulo")
                .uf("SP")
                .email("contato@exemplo.com.br")
                .build();
        GeneralNames san = SubjectAltNameBuilder.build(CertificateProfile.RFB_ECNPJ_A1, company);
        assertEquals(5, san.getNames().length);

        // .3.4 (dados do responsável) com órgão/UF em 10 posições: total 55
        ASN1Sequence resp = ASN1Sequence.getInstance(san.getNames()[2].getName());
        assertEquals(IcpBrasilOids.PJ_DADOS_RESPONSAVEL, resp.getObjectAt(0));
        byte[] octets = ASN1OctetString.getInstance(
                        ASN1TaggedObject.getInstance(resp.getObjectAt(1)).getExplicitBaseObject())
                .getOctets();
        assertEquals(55, octets.length);
    }
}
