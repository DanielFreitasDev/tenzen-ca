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

Dados ficam em `~/.tenzen-ca/` (configurável em `app.data-dir` / env `APP_DATA_DIR`):
`root.p12` e `issuing.p12` (chaves da cadeia) e o banco H2 do histórico. Se o banco
registrar uma cadeia e os keystores sumirem ou divergirem, a aplicação **recusa
subir** em vez de gerar outra AC em silêncio — apague o diretório inteiro para
recomeçar.

### Docker

```bash
cp .env.example .env   # defina APP_CA_KEYSTORE_PASSWORD (gere com: openssl rand -base64 24)
docker compose up -d --build
# http://localhost:8080
```

Imagem multi-stage (Temurin 25: JDK no build, JRE no runtime, jar extraído em
camadas), usuário sem privilégios, healthcheck pela CRL e contêiner endurecido
(raiz somente leitura, sem capabilities, sem escalada de privilégios). Os dados
moram no volume `ca-data`, montado em `/data`; o equivalente do "apague o
diretório" é `docker compose down -v`, que descarta cadeia e histórico. Toda a
configuração vem do `.env` (ver `.env.example`). Em máquinas cuja internet sai
por proxy em localhost, defina `BUILD_NETWORK=host` para o build da imagem.

## Configurar

Propriedades de `application.properties`, sobreponíveis por variável de ambiente
(no Docker, tudo passa pelo `.env`):

- **Senha dos keystores** — `app.ca.keystore-password` / `APP_CA_KEYSTORE_PASSWORD`.
  Local tem default de conveniência (`tenzen-dev`); no Docker é obrigatória. Trocar
  a senha depois da primeira subida invalida os keystores existentes.
- **URL base** — `app.base-url` / `APP_BASE_URL`: embutida nos certificados emitidos
  (CRL DP, AIA, CPS); precisa ser alcançável por quem for validá-los — ajuste ao
  expor na rede.
- **Exposição** — a app escuta só em `127.0.0.1` (`server.address`; no Docker o
  compose publica a porta conforme `TENZEN_BIND`/`TENZEN_PORT`). Para expor além do
  localhost, ligue o basic-auth (`APP_SECURITY_BASIC_AUTH_ENABLED=true` +
  usuário/senha; senha obrigatória). CRL, AIA e estáticos continuam públicos para
  os validadores; o resto da interface passa a exigir login.
- **Policy OID** — `app.ca.policy-mode` / `APP_CA_POLICY_MODE`: `icp-format`
  (formato ICP-Brasil com leaf fictício, documentado como não atribuído — default)
  ou `private-2.25` (OID privado derivado de UUID).

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
  e instruções de truststore isolado. AIA em `/aia/tenzen-ca.p7b`; CRL da Raiz em
  `/crl/tenzen-root.crl`; DPC fictícia em `/dpc`.

## Verificar

```bash
./mvnw verify            # golden tests ASN.1 dos 8 perfis, PKIX com revogação, CRL, PKCS#12, MockMvc
scripts/verify-cert.sh   # com a app no ar: emite, inspeciona e revoga via OpenSSL
```

## Referências

`docs/standards.md` mapeia cada campo emitido para a fonte normativa (Leiaute
AC-RFB v5.0, DOC-ICP-04 v8.3, DOC-ICP-01.01, Res. 211/2024 e 212/2025, IN RFB
2.229/2024), incluindo as divergências e interpretações declaradas.
`docs/referencias/README.md` lista onde obter os documentos.

## Stack

Spring Boot 4.1 (Java 25) · Bouncy Castle 1.85 · Thymeleaf (fragmentos nativos) ·
Tailwind CSS v4 + esbuild (via frontend-maven-plugin) · anime.js 4 · H2 em arquivo ·
IBM Plex self-hosted · Docker multi-stage + Compose. Tema claro/escuro com View
Transitions, controles de formulário com HTML semântico + ARIA APG, CSP com nonce
e CSRF ativo.
