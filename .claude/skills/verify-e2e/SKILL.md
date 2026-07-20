---
name: verify-e2e
description: Verificação ponta a ponta da emissão de certificados via OpenSSL (scripts/verify-e2e.sh) — builda o jar, sobe uma instância efêmera e valida emissão, DN/extensões, SAN, revogação, CRLs e AIA. Use após mudanças em ca/, cert/, issuance/ ou nos fluxos web de emissão/revogação, ou quando o usuário pedir verificação ponta a ponta. Aceita opcionalmente uma BASE URL como argumento para testar uma instância já no ar.
---

Verificação ponta a ponta do Tenzen CA (exige `openssl`, `curl` e `java` no PATH). Os cheques em si — emissão dos 4 perfis, cadeia, DN/extensões, SAN, revogação, CRLs, AIA — vivem em `scripts/verify-cert.sh`; o que muda entre os modos é contra qual instância ele roda. Decida primeiro: se `$ARGUMENTS` contém uma URL, use o modo alternativo contra a instância já no ar; caso contrário, use o modo padrão efêmero.

## Modo padrão — instância efêmera

`scripts/verify-e2e.sh` faz o ciclo completo sozinho: builda o jar (frontend pulado), acha uma porta livre a partir da 8085, sobe a app com data-dir descartável (não toca no `~/.tenzen-ca` do usuário), aguarda o boot e roda o `verify-cert.sh`, derrubando e limpando tudo ao final — inclusive em falha.

```bash
scripts/verify-e2e.sh
```

- Leva vários minutos (build + boot com geração de cadeia RSA-4096 + verificação): rode em background e acompanhe, ou use timeout de 10 min.
- `--skip-build` reaproveita o jar de `target/` — só use se o jar já reflete o código editado; senão você estará verificando código velho.
- Em falha, o script imprime as últimas linhas do log da app e **mantém** o diretório de trabalho (`/tmp/tenzen-e2e.*`) para inspeção — consulte o `app.log` de lá se o motivo não estiver óbvio e remova o diretório ao encerrar a investigação.

## Modo alternativo — instância já no ar (somente se o usuário passou uma URL)

Se `$ARGUMENTS` contém uma URL (ex.: `http://localhost:8080`), rode direto contra ela:

```bash
BASE=$ARGUMENTS scripts/verify-cert.sh
```

Dois cuidados: isso grava emissões e uma revogação no histórico daquela instância, e verifica o código que está **rodando** nela — não necessariamente o código editado.

## Relatório

- Sucesso: o script termina com "Tudo verificado com sucesso." — resuma as seções verificadas (`== ... ==`).
- Falha: a primeira linha `FALHOU: ...` indica o cheque quebrado; reporte-a junto com a seção em que ocorreu e investigue a causa no código antes de propor correção. Falhas de build ou boot vêm do próprio `verify-e2e.sh` (o log da app diz o porquê); falhas de conteúdo de certificado devem ser conferidas contra `docs/standards.md`, a fonte normativa.
