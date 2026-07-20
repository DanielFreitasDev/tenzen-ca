---
name: verify-e2e
description: Verificação ponta a ponta da emissão de certificados via OpenSSL (scripts/verify-cert.sh) — builda o jar, sobe uma instância efêmera e valida emissão, DN/extensões, SAN, revogação, CRLs e AIA. Use após mudanças em ca/, cert/, issuance/ ou nos fluxos web de emissão/revogação, ou quando o usuário pedir verificação ponta a ponta. Aceita opcionalmente uma BASE URL como argumento para testar uma instância já no ar.
---

Verificação ponta a ponta do Tenzen CA com `scripts/verify-cert.sh` (exige `openssl` e `curl` no PATH).

## Modo 1 — instância já no ar (somente se o usuário passou uma URL)

Se `$ARGUMENTS` contém uma URL (ex.: `http://localhost:8080`), rode direto contra ela — atenção: isso grava emissões e uma revogação no histórico daquela instância:

```bash
BASE=$ARGUMENTS scripts/verify-cert.sh
```

Atenção: esse modo verifica o código que está **rodando** naquela instância, não necessariamente o código editado.

## Modo 2 — instância efêmera (padrão)

Verifica o código atual sem tocar no `~/.tenzen-ca` do usuário.

1. Builde o jar (frontend pulado — o script só usa curl/openssl, não precisa de assets):

   ```bash
   ./mvnw -DskipTests -Dskip.installnodenpm -Dskip.npm package
   ```

2. Escolha uma porta livre (padrão 8085; se ocupada, tente 8086...) e suba a app com data-dir temporário, em background:

   ```bash
   DATA_DIR=$(mktemp -d /tmp/tenzen-e2e.XXXXXX)
   java -jar target/tenzen-ca-*.jar \
     --server.port=8085 \
     --app.data-dir="$DATA_DIR" \
     --app.base-url=http://localhost:8085
   ```

3. Aguarde o boot: faça polling de `http://localhost:8085/crl/tenzen-ca.crl` até responder 200 (timeout ~120 s — o primeiro boot gera uma cadeia RSA-4096 real).

4. Rode o script e capture a saída completa:

   ```bash
   BASE=http://localhost:8085 scripts/verify-cert.sh
   ```

5. Sempre finalize (mesmo em caso de falha): mate o processo java da instância efêmera e remova `$DATA_DIR`.

## Relatório

- Sucesso: o script termina com "Tudo verificado com sucesso." — resuma as seções verificadas (`== ... ==`).
- Falha: a primeira linha `FALHOU: ...` indica o cheque quebrado; reporte-a junto com a seção em que ocorreu e investigue a causa no código antes de propor correção. Lembre que `docs/standards.md` é a fonte normativa do conteúdo dos certificados.
