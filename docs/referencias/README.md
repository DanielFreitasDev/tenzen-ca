# Referências normativas

Fontes primárias que fundamentam os perfis emitidos (o mapa campo a campo está em
`../standards.md`). Os PDFs oficiais, quando presentes neste diretório, foram baixados
dos repositórios públicos abaixo; se ausentes, baixe pelos links.

| Documento                                                                                  | O que rege aqui                                                                                                        | Onde obter                                                          |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| Leiaute dos Certificados Digitais da AC-RFB **v5.0** (Portaria RFB/Sucor/Cotec nº 42/2020) | DN legado (OUs, L/ST), larguras dos otherNames (51/55), criticidades, EKU, 2 LCRs, OCTET STRING, zero-fill             | gov.br/receitafederal (documentos técnicos de certificação digital) |
| **DOC-ICP-04 v8.3** (Res. 179/2020, compilada até Res. 215/2025)                           | Nova geração: tipos SE/AE, DN com serialNumber (ETSI EN 319 412), SAN provisório até 31/12/2028, Tabela 6 de validades | repositorio.iti.gov.br                                              |
| **DOC-ICP-01.01** (IN ITI nº 22/2022, compilada)                                           | SHA-512 para certs de AC, SHA-256/512 para titular, RSA 4096/2048                                                      | repositorio.iti.gov.br                                              |
| Res. CG ICP-Brasil **211/2024** + FAQ                                                      | Transição: emissão legada até 02/03/2029; novos tipos desde 01/11/2024                                                 | iti.gov.br                                                          |
| **IN RFB 2.229/2024**                                                                      | CNPJ alfanumérico (27/07/2026), DV módulo 11 sobre ASCII-48                                                            | normas.receita.fazenda.gov.br / Serpro                              |
| Fonte de campo (referência secundária)                                                     | Arrays `FIELDS` do Demoiselle Signer (parser histórico dos otherNames)                                                 | github.com/demoiselle/signer                                        |

Divergência documentada: o Demoiselle parseia `2.16.76.1.3.4` com órgão/UF em 6
posições; o Leiaute v5.0 manda **10** (total 55). Este projeto segue o leiaute.
