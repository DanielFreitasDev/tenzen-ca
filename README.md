# Tenzen CA

Autoridade Certificadora **de testes** com interface web: emite certificados no estilo
ICP-Brasil (e-CPF e e-CNPJ legados da AC-RFB, nova geração PF A3/A4 e Selo Eletrônico
SE-S/SE-H) assinados por uma cadeia simulada (Raiz → Intermediária → titular), com DN,
otherNames `2.16.76.1.3.*`, extensões, CRL e AIA fiéis aos leiautes oficiais.

**Nada aqui tem valor legal.** A cadeia é fictícia; a Raiz só deve ser confiada em
truststores isolados de ambiente de teste.

## Rodar

Requisitos: Java 25 e Maven 3.9+ (o wrapper `./mvnw` resolve o Maven; o build baixa
o próprio Node para compilar o frontend).

```bash
./mvnw -DskipTests package
java -jar target/tenzen-ca-*.jar
# http://localhost:8080
```

Ou em desenvolvimento: `./mvnw spring-boot:run` (e `npm run watch:css` / `watch:js`
em outro terminal para o frontend).

Dados ficam em `~/.tenzen-ca/`: `root.p12` e `issuing.p12` (chaves da cadeia,
senha em `app.ca.keystore-password` / env `APP_CA_KEYSTORE_PASSWORD`) e o banco H2
do histórico. Se o banco registrar uma cadeia e os keystores sumirem ou divergirem,
a aplicação **recusa subir** em vez de gerar outra AC em silêncio — apague o
diretório inteiro para recomeçar.

## O que dá para fazer

- **Emitir** (`/emitir`): escolhe o perfil, preenche os dados (ou gera dados
  fictícios válidos, inclusive CNPJ alfanumérico da IN RFB 2.229/2024), escolhe a
  validade ("conforme o perfil" ou modo "laboratório" sem tetos), senha e alias, e
  baixa `.p12` + PEM.
- **Assinar CSR** (`/csr`): envia um PKCS#10 e recebe o certificado sem a AC ver a
  chave privada.
- **Histórico** (`/historico`): busca, re-download e **revogação** com motivo; a CRL
  (CRLNumber monotônico, nextUpdate 24 h, AKI, reasonCode) é republicada na hora nas
  duas URLs (`/crl/tenzen-ca.crl` e `/crl2/tenzen-ca.crl`).
- **Cadeia** (`/cadeia`): downloads da Raiz/Intermediária (CRT/PEM/P7B), fingerprint
  e instruções de truststore isolado. AIA em `/aia/tenzen-ca.p7b`; DPC fictícia em `/dpc`.

## Verificar

```bash
./mvnw verify            # golden tests ASN.1 por perfil, PKIX, CRL, MockMvc
scripts/verify-cert.sh   # com a app no ar: emite, inspeciona e revoga via OpenSSL
```

## Referências

`docs/standards.md` mapeia cada campo emitido para a fonte normativa (Leiaute
AC-RFB v5.0, DOC-ICP-04 v8.3, DOC-ICP-01.01, Res. 211/2024, IN RFB 2.229/2024),
incluindo as divergências e interpretações declaradas. `docs/referencias/README.md`
lista onde obter os documentos.

## Stack

Spring Boot 4.1 (Java 25) · Bouncy Castle 1.85 · Thymeleaf (fragmentos nativos) ·
Tailwind CSS v4 + esbuild (via frontend-maven-plugin) · anime.js 4 · H2 em arquivo ·
IBM Plex self-hosted. Tema claro/escuro com View Transitions, controles de formulário
com HTML semântico + ARIA APG, CSP com nonce e CSRF ativo.
