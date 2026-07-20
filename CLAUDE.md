# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

Autoridade Certificadora **de testes** estilo ICP-Brasil (e-CPF, e-CNPJ, nova geração PF, Selo Eletrônico) — Spring Boot 4.1, Thymeleaf, BouncyCastle, Java 25, frontend Tailwind v4 + esbuild. A cadeia Root → Intermediária é fictícia e sem valor jurídico.

## Comandos

- Requisitos: JDK 25 (enforcer exige `[25,26)`) e Maven 3.9+ — use sempre `./mvnw`.
- Suíte completa: `./mvnw verify` — lenta (os testes geram cadeias RSA-4096 reais). Obrigatória antes de qualquer commit.
- Teste único/targeted: `./mvnw test -Dtest=NomeDaClasse -Dskip.installnodenpm -Dskip.npm` — as flags pulam o build de frontend, que roda na fase `generate-resources` de qualquer build normal (e exige rede no primeiro build).
- Dev: `./mvnw spring-boot:run` (+ `npm run watch:css` / `npm run watch:js` em terminais separados).
- Verificação ponta a ponta (emissão, extensões, revogação, CRL via OpenSSL): skill `/verify-e2e`, que usa `scripts/verify-cert.sh`.
- Formatação: `./mvnw spotless:apply` (imports e whitespace; aplicada automaticamente via hook após edições).

## Armadilhas

- **Material da CA**: no primeiro boot são criados `root.p12` e `issuing.p12` em `~/.tenzen-ca` (`app.data-dir`), com o fingerprint da raiz ancorado no H2. Se keystores e âncora divergirem (ou os keystores sumirem), a app **se recusa a iniciar**; reset = apagar o data-dir inteiro (Docker: `docker compose down -v`). Senha local padrão `tenzen-dev` (`APP_CA_KEYSTORE_PASSWORD`); trocá-la depois do primeiro boot invalida os keystores existentes.
- **`docs/standards.md` é a fonte normativa** do conteúdo dos certificados (Leiaute AC-RFB v5.0, DOC-ICP-04 etc.). Qualquer mudança em `cert/` deve ser conferida contra ele; `CertificateGoldenTest` trava os bytes ASN.1 de cada perfil e falha em divergências não intencionais.
- Assets gerados (`static/css/app.css`, `static/js/app.js`, `static/fonts/`) são gitignorados — nunca editar; os fontes ficam em `src/main/css/` e `src/main/js/`.
- Spring Boot 4 reorganizou os módulos de teste — ex.: `AutoConfigureMockMvc` vem de `org.springframework.boot.webmvc.test.autoconfigure`.
- Testes de integração estendem `IntegrationTestBase` (data-dir temporário por JVM + H2 em memória); novos testes de integração devem fazer o mesmo.

## Estilo e git

- Português em comentários, Javadoc, mensagens de log/exceção e commits; identificadores em inglês.
- Indentação de 4 espaços, sem tabs.
- Commits direto na `main`, mensagens descritivas em português no estilo do histórico (sem conventional commits).
- Depois de editar: rode só as classes de teste afetadas; `./mvnw verify` completo antes de commitar.
