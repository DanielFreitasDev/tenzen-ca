#!/usr/bin/env bash
# Cross-check OpenSSL de ponta a ponta contra uma instância local do Tenzen CA.
# Sobe a app antes (mvn spring-boot:run ou java -jar) e rode: scripts/verify-cert.sh
# Para o ciclo completo autocontido (build + instância efêmera), use scripts/verify-e2e.sh.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
WORK="$(mktemp -d /tmp/tenzen-verify.XXXXXX)"
CURL=(curl -s --noproxy '*' -c "$WORK/cookies" -b "$WORK/cookies")
PASS=teste1234
trap 'rm -rf "$WORK"' EXIT

say() { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
fail() { printf '\033[31mFALHOU: %s\033[0m\n' "$*"; exit 1; }

csrf() {
  "${CURL[@]}" "$BASE/emitir" | grep -o 'name="_csrf" *value="[^"]*"' | sed 's/.*value="//;s/"//'
}

issue() { # issue <perfil> <extra-args...> -> imprime id
  local profile="$1"; shift
  local token; token=$(csrf)
  local location
  location=$("${CURL[@]}" -o /dev/null -w '%{redirect_url}' -X POST "$BASE/emitir" \
    --data-urlencode "_csrf=$token" \
    --data-urlencode "profileId=$profile" \
    --data-urlencode "validadeModo=perfil" \
    --data-urlencode "validadeAnos=1" \
    --data-urlencode "validadeDias=365" \
    --data-urlencode "validationType=videoconferencia" \
    --data-urlencode "senha=$PASS" \
    --data-urlencode "senhaConfirma=$PASS" \
    --data-urlencode "alias=verificacao" \
    "$@")
  [[ "$location" == *"/certificados/"* ]] || fail "emissão $profile não redirecionou (obtido: $location)"
  echo "${location##*/}"
}

say "cadeia da AC"
"${CURL[@]}" -o "$WORK/chain.pem" "$BASE/ca/tenzen-chain.pem"
openssl x509 -in "$WORK/chain.pem" -noout -subject | grep -q "Tenzen" || fail "cadeia não é da Tenzen"

say "emitindo e-CPF A1, e-CPF A3, e-CNPJ A1 e SE-S via HTTP"
ID_ECPF_A1=$(issue rfb-ecpf-a1 \
  --data-urlencode "nome=Verificacao Silva" --data-urlencode "cpf=12345678909" \
  --data-urlencode "nascimento=01/02/1980" --data-urlencode "cidade=Fortaleza" \
  --data-urlencode "uf=CE" --data-urlencode "email=v@exemplo.com.br")
ID_ECPF_A3=$(issue rfb-ecpf-a3 \
  --data-urlencode "nome=Verificacao Souza" --data-urlencode "cpf=12345678909" \
  --data-urlencode "rg=112233" --data-urlencode "rgOrgaoUf=SSPCE" \
  --data-urlencode "cidade=Fortaleza" --data-urlencode "uf=CE")
ID_ECNPJ=$(issue rfb-ecnpj-a1 \
  --data-urlencode "razaoSocial=Verificacao Comercio LTDA" \
  --data-urlencode "cnpj=99999999000191" \
  --data-urlencode "responsavelNome=Resp Verificacao" \
  --data-urlencode "responsavelCpf=12345678909" \
  --data-urlencode "cidade=Sao Paulo" --data-urlencode "uf=SP")
ID_SES=$(issue ng-pj-se-s \
  --data-urlencode "razaoSocial=Selo Verificacao SA" \
  --data-urlencode "cnpj=99999999000191" \
  --data-urlencode "responsavelNome=Resp Selo" \
  --data-urlencode "responsavelCpf=12345678909")
echo "ids: $ID_ECPF_A1 $ID_ECPF_A3 $ID_ECNPJ $ID_SES"

say "p12 abre e a cadeia valida (openssl verify)"
for id in "$ID_ECPF_A1" "$ID_ECPF_A3" "$ID_ECNPJ" "$ID_SES"; do
  "${CURL[@]}" -o "$WORK/$id.p12" "$BASE/certificados/$id/certificado.p12"
  openssl pkcs12 -in "$WORK/$id.p12" -nodes -passin "pass:$PASS" 2>/dev/null \
    | openssl x509 -out "$WORK/$id.pem"
  openssl verify -CAfile "$WORK/chain.pem" "$WORK/$id.pem" >/dev/null \
    || fail "verify do cert $id"
done
echo "ok"

say "DN e extensões do e-CPF A3"
TEXT=$(openssl x509 -in "$WORK/$ID_ECPF_A3.pem" -noout -text)
echo "$TEXT" | grep -q "CN = VERIFICACAO SOUZA:12345678909" || fail "CN legado"
echo "$TEXT" | grep -q "O = ICP-Brasil" || fail "O=ICP-Brasil"
echo "$TEXT" | grep -q "Key Usage: critical" || fail "KU crítica"
echo "$TEXT" | grep -q "2.16.76.1.2.3.999" || fail "policy A3"
echo "$TEXT" | grep -q "CA Issuers - URI:.*aia/tenzen-ca.p7b" || fail "AIA"
echo "$TEXT" | grep -qc "URI:.*crl.*tenzen-ca.crl" || fail "CRL DP"
echo "$TEXT" | grep -q "2.16.76.1.3.1" || fail "otherName .3.1"
echo "ok"

say "SAN em OCTET STRING (asn1parse)"
OFFSET=$(openssl asn1parse -in "$WORK/$ID_ECPF_A3.pem" | grep -A1 "Subject Alternative" | tail -1 | cut -d: -f1 | tr -d ' ')
openssl asn1parse -in "$WORK/$ID_ECPF_A3.pem" -strparse "$OFFSET" | grep -q "OCTET STRING" \
  || fail "otherName não é OCTET STRING"
echo "ok"

say "revogação: revogar o e-CPF A1 e conferir a CRL"
TOKEN=$(csrf)
"${CURL[@]}" -o /dev/null -X POST "$BASE/certificados/$ID_ECPF_A1/revogar" \
  --data-urlencode "_csrf=$TOKEN" --data-urlencode "motivo=keyCompromise"
"${CURL[@]}" -o "$WORK/ca.crl" "$BASE/crl/tenzen-ca.crl"
"${CURL[@]}" -o "$WORK/ca2.crl" "$BASE/crl2/tenzen-ca.crl"
cmp -s "$WORK/ca.crl" "$WORK/ca2.crl" || fail "as duas URLs de CRL divergem"
openssl crl -in "$WORK/ca.crl" -inform DER -noout -text > "$WORK/crl.txt"
SERIAL=$(openssl x509 -in "$WORK/$ID_ECPF_A1.pem" -noout -serial | cut -d= -f2)
grep -qi "$SERIAL" "$WORK/crl.txt" || fail "serial revogado não consta na CRL"
grep -q "Key Compromise" "$WORK/crl.txt" || fail "reasonCode ausente"
echo "ok (serial $SERIAL na CRL)"

say "verify -crl_check: revogado falha, válido passa"
openssl crl -in "$WORK/ca.crl" -inform DER -out "$WORK/ca-crl.pem"
if openssl verify -crl_check -CRLfile "$WORK/ca-crl.pem" -CAfile "$WORK/chain.pem" \
    "$WORK/$ID_ECPF_A1.pem" >/dev/null 2>&1; then
  fail "certificado revogado passou no crl_check"
fi
openssl verify -crl_check -CRLfile "$WORK/ca-crl.pem" -CAfile "$WORK/chain.pem" \
  "$WORK/$ID_ECPF_A3.pem" >/dev/null || fail "certificado válido reprovou no crl_check"
echo "ok"

say "AIA .p7b e CRL da raiz respondem"
"${CURL[@]}" -o "$WORK/aia.p7b" "$BASE/aia/tenzen-ca.p7b"
openssl pkcs7 -inform DER -in "$WORK/aia.p7b" -print_certs | grep -q "Tenzen" || fail "p7b"
"${CURL[@]}" -o "$WORK/root.crl" "$BASE/crl/tenzen-root.crl"
openssl crl -in "$WORK/root.crl" -inform DER -noout || fail "CRL da raiz"
echo "ok"

printf '\n\033[32mTudo verificado com sucesso.\033[0m\n'
