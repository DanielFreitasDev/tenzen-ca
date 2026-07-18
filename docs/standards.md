# Tenzen CA — mapa normativo campo a campo

Cada campo emitido, com a fonte primária que o justifica. Onde a implementação
interpreta ou diverge de propósito, está dito explicitamente.

Documentos de referência (ver `docs/referencias/`):

- **Leiaute v5.0** — Leiaute dos Certificados Digitais da AC-RFB v5.0 (Portaria RFB/Sucor/Cotec nº 42/2020). Rege os perfis legados "RFB e-CPF" e "RFB e-CNPJ".
- **DOC-ICP-04 v8.3** — Requisitos mínimos para as PC (Res. 179/2020, compilada até a Res. 215/2025). Rege a nova geração (PF A3/A4, Selo Eletrônico SE-S/SE-H).
- **DOC-ICP-01.01** — Padrões e algoritmos criptográficos (IN ITI nº 22/2022, compilada).
- **Res. CG ICP-Brasil 211/2024 + FAQ** — transição: emissão legada permitida até 02/03/2029; novos tipos desde 01/11/2024.
- **IN RFB 2.229/2024** — CNPJ alfanumérico (produção 27/07/2026; DV módulo 11 sobre ASCII-48).

## Cadeia da AC (simulada)

| Item | Valor emitido | Fonte | Nota |
|---|---|---|---|
| Algoritmo de assinatura (Root e Intermediária) | sha512WithRSAEncryption | DOC-ICP-01.01 | certificados **de AC** assinam com SHA-512 |
| Chaves de AC | RSA-4096 | DOC-ICP-01.01 | 2048/4096 admitidos; usamos 4096 |
| basicConstraints | crítica, CA:TRUE; pathLen=0 na Intermediária | Leiaute v5.0 (perfil de AC) | |
| keyUsage | crítica, keyCertSign + cRLSign | Leiaute v5.0 | |
| SKI/AKI | presentes | Leiaute v5.0 | SKI/AKI **só** nos certs de AC |
| CRL DP da Intermediária | 2 URLs (`/crl/tenzen-root.crl`, `/crl2/…`) | Leiaute v5.0 ("dois endereços web diferentes") | |
| DNs da cadeia | `CN=AC Raiz de Testes Tenzen v1` etc. | — | nomes deliberadamente fictícios; `O=ICP-Brasil` mantido para fidelidade estrutural |

## Titular — comum às duas gerações

| Item | Valor emitido | Fonte | Nota |
|---|---|---|---|
| Assinatura do titular | sha256WithRSAEncryption (config p/ SHA-512) | DOC-ICP-01.01 | `app.ca.subject-signature-algorithm` |
| Chave do titular | RSA-2048 (A4: 4096) | DOC-ICP-01.01 | |
| basicConstraints | **não crítica**, CA:FALSE | Leiaute v5.0 ("Não crítica, Opcional") | crítica apenas em AC |
| keyUsage | **crítica**: digitalSignature + nonRepudiation + keyEncipherment | Leiaute v5.0 (legado, os 3 sem distinção A1/A3); DOC-ICP-04 v8.3 (NG: DS obrigatório, NR/KE permitidos) | na NG emitimos os 3 (permitido) |
| extendedKeyUsage | clientAuth + emailProtection; + MS Smartcard Logon `1.3.6.1.4.1.311.20.2.2` **só no e-CPF legado** | Leiaute v5.0 | smartcard é opcional e não listado no e-CNPJ/NG |
| SKI | **ausente** | Leiaute v5.0 não lista p/ titular; item 8.2 do DOC-ICP-04 revogado (Res. 215/2025) | |
| AKI | presente, keyid da Intermediária | Leiaute v5.0 | |
| cRLDistributionPoints | 2 URLs distintas (`/crl/tenzen-ca.crl`, `/crl2/…`) | Leiaute v5.0 | |
| authorityInfoAccess | caIssuers → `/aia/tenzen-ca.p7b` (cadeia em PKCS#7) | Leiaute v5.0 (AIA opcional: OCSP + arquivo .p7b) | OCSP responder fora do escopo |
| certificatePolicies | `2.16.76.1.2.<ramo>.999` + CPS → `/dpc` | DOC-ICP-04.01 (estrutura do OID) | **999 é fictício, não atribuído pelo ITI**; modo `private-2.25` disponível |
| Serial | aleatório de 127 bits, único | prática CA/B + unicidade verificada no banco | |
| Validade | A1 ≤ 3 (preset 1) · A3 ≤ 5 · A4 ≤ 6 · SE-S = 1 · SE-H ≤ 5 | Tabela 6 do DOC-ICP-04 v8.3 (Res. 212/2025); legado: PCs históricas (Res. 99) | modo "laboratório" ignora tetos e marca o certificado |

## Titular — DN legado (Leiaute v5.0)

| Item | Valor emitido | Nota |
|---|---|---|
| Ordem de codificação | C, O, (ST, L no e-CNPJ), OUs, CN | conferível no `openssl x509 -subject` |
| `C` / `O` | `BR` / `ICP-Brasil` | constantes |
| e-CPF: OU① | CNPJ da AR: `99999999000191` | AR fictícia; base 99999999 com DV real |
| e-CPF: OU② | `videoconferencia` \| `presencial` \| `certificado digital` | escolhido no formulário; sem acento, como nos certificados reais |
| e-CPF: OU③ | `Secretaria da Receita Federal do Brasil – RFB` | string do leiaute **verbatim, com travessão** (vira UTF8String no DER) |
| e-CPF: OU④ | `RFB e-CPF A1` \| `A3` | |
| e-CPF: OU⑤ | domínio informado ou literal `EM BRANCO` | |
| e-CPF: CN | `<NOME ≤52, maiúsculo sem acento>:<CPF 11>` | |
| e-CNPJ: OUs | ① CNPJ AR, ② tipo, ③ `RFB e-CNPJ A1\|A3`, ④ `Secretaria…– RFB` | ordem ③/④ invertida vs e-CPF, conforme leiaute |
| e-CNPJ: L / ST | cidade sem acentos / UF | obrigatórios no e-CNPJ |
| e-CNPJ: CN | `<RAZÃO ≤49>:<CNPJ 14>` | |

## Titular — DN nova geração (DOC-ICP-04 v8.3 / ETSI EN 319 412)

| Item | Valor emitido | Nota |
|---|---|---|
| PF | `CN=<nome civil>` + `serialNumber (2.5.4.5)=<CPF>` | ETSI EN 319 412-2 |
| PJ (SE) | `CN=<razão social>` + `serialNumber=<CNPJ>` | ETSI EN 319 412-3; `organizationIdentifier (2.5.4.97)` **não** emitido (simplificação declarada) |
| OUs | `Tenzen CA de Testes` (+ tipo de validação na PF) | composição de OU varia por PC de AC real; usamos conjunto mínimo |

## SAN — otherNames (`2.16.76.1.3.*`)

Codificação: `otherName ::= SEQUENCE { OID, [0] EXPLICIT OCTET STRING }` — o leiaute
admite OCTET STRING ou PrintableString; usamos **OCTET STRING** (dominante em produção).
Campos numéricos ausentes viram zeros na largura total; texto alinha à esquerda com
espaços à direita.

| OID | Perfis | Layout | Total | Nota |
|---|---|---|---|---|
| `.3.1` | e-CPF legado | nasc DDMMAAAA(8) · CPF(11) · NIS(11) · RG(15) · órgão/UF(**6**) | **51** | Leiaute v5.0 |
| `.3.1` | NG PF | idem com órgão/UF(**10**) | **55** | DOC-ICP-04 v8.3; mantido provisoriamente até 31/12/2028 |
| `.3.5` | e-CPF legado | inscrição(12) · zona(3) · seção(4) · município+UF(22) | 41 | |
| `.3.6` | e-CPF legado | CEI/NIT | 12 | |
| `.3.2` | e-CNPJ/SE | nome do responsável | var | |
| `.3.3` | e-CNPJ/SE | CNPJ | 14 | aceita alfanumérico (IN RFB 2.229/2024) |
| `.3.4` | e-CNPJ/SE | dados PF do responsável, órgão/UF(**10**) | **55** | **≠ `.3.1` legado!** O Demoiselle Signer parseia `.3.4` com 6 posições; o leiaute manda 10 — a fonte primária vence |
| `.3.7` | e-CNPJ/SE | CEI da empresa | 12 | |
| email | todos | `rfc822Name` (IA5String) | — | |
| `.3.8` (razão social) e `.3.9` (RIC) | — | **não emitidos** | — | não fazem parte do SAN obrigatório do leiaute v5.0 |

Interpretações declaradas (texto do leiaute admite leitura dupla):

- **RG ausente** → campo órgão/UF **omitido** (bloco fica com 45 posições), como manda o leiaute; demais numéricos zerados.
- **Título ausente** → inscrição/zona/seção zeradas e município/UF omitido (paralelo à regra do RG).
- **UPN Microsoft** (`1.3.6.1.4.1.311.20.2.3`, UTF8String) e OID de conselho de classe (`2.16.76.1.4.*`): opcionais do leiaute, **não emitidos** nesta versão.

## CRL

| Item | Valor | Fonte |
|---|---|---|
| Assinatura | SHA512withRSA (mesma chave da AC emissora) | DOC-ICP-01.01 |
| nextUpdate | 24 h (CA) / 30 d (Root) | prática das ACs; regeneração agendada antes de vencer |
| CRLNumber | monotônico, persistido em banco | RFC 5280 §5.2.3 |
| AKI | presente | RFC 5280 |
| reasonCode | por entrada (unspecified/keyCompromise/affiliationChanged/superseded/cessationOfOperation) | RFC 5280 §5.3.1 |
| Publicação | 2 URLs distintas, troca atômica de referência, CRL inicial vazia no bootstrap | Leiaute v5.0 |

## CNPJ alfanumérico (IN RFB 2.229/2024)

12 primeiras posições `[0-9A-Z]`, 2 DVs numéricos por módulo 11 sobre `(ASCII - 48)`
(A=17 … Z=42). Produção a partir de 27/07/2026. A máscara da UI aceita letras
(uppercase automático) e valida o DV no cliente e no servidor; o gerador de dados
fictícios produz ambos os formatos.
