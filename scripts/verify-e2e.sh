#!/usr/bin/env bash
# Verificação ponta a ponta autocontida: builda o jar (frontend pulado), sobe uma
# instância efêmera (porta livre + data-dir descartável) e roda scripts/verify-cert.sh
# contra ela. Derruba a instância ao final mesmo em caso de falha; em falha, imprime
# as últimas linhas do log da app e mantém o diretório de trabalho para inspeção.
#
# Uso: scripts/verify-e2e.sh [--skip-build] [--port N]
#   --skip-build  reaproveita o jar existente em target/ (cuidado: pode ser código velho)
#   --port N      força a porta em vez de procurar uma livre a partir da 8085
set -euo pipefail
cd "$(dirname "$0")/.."

say() { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
fail() { printf '\033[31mFALHOU: %s\033[0m\n' "$*"; exit 1; }

SKIP_BUILD=0
PORT=""
while (($#)); do
  case "$1" in
    --skip-build) SKIP_BUILD=1 ;;
    --port)
      shift
      PORT="${1:-}"
      [[ "$PORT" =~ ^[0-9]+$ ]] || fail "--port exige um número (obtido: '${PORT}')"
      ;;
    *) fail "argumento desconhecido: $1 (uso: verify-e2e.sh [--skip-build] [--port N])" ;;
  esac
  shift
done

if ((SKIP_BUILD == 0)); then
  say "build do jar (testes e frontend pulados)"
  ./mvnw -ntp -DskipTests -Dskip.installnodenpm -Dskip.npm package
fi
JAR=$(ls -t target/tenzen-ca-*.jar 2>/dev/null | head -1) || true
[[ -n "$JAR" ]] || fail "jar não encontrado em target/ (rode sem --skip-build)"

if [[ -z "$PORT" ]]; then
  for p in $(seq 8085 8115); do
    if ! (exec 3<>"/dev/tcp/127.0.0.1/$p") 2>/dev/null; then
      PORT=$p
      break
    fi
  done
  [[ -n "$PORT" ]] || fail "nenhuma porta livre entre 8085 e 8115"
fi

WORK=$(mktemp -d /tmp/tenzen-e2e.XXXXXX)
LOG="$WORK/app.log"
APP_PID=""

cleanup() {
  local code=$?
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if ((code != 0)); then
    if [[ -s "$LOG" ]]; then
      say "últimas linhas do log da app"
      tail -n 40 "$LOG"
    fi
    echo "diretório de trabalho mantido para inspeção: $WORK"
  else
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM

say "subindo instância efêmera na porta $PORT (data-dir: $WORK/data)"
java -jar "$JAR" \
  --server.port="$PORT" \
  --app.data-dir="$WORK/data" \
  --app.base-url="http://localhost:$PORT" >"$LOG" 2>&1 &
APP_PID=$!

BOOT_START=$SECONDS
DEADLINE=$((SECONDS + 180))
until curl -sf --noproxy '*' -o /dev/null "http://localhost:$PORT/crl/tenzen-ca.crl"; do
  kill -0 "$APP_PID" 2>/dev/null || fail "a app terminou durante o boot"
  ((SECONDS < DEADLINE)) || fail "timeout de 180 s aguardando o boot"
  sleep 2
done
echo "app no ar após $((SECONDS - BOOT_START)) s (o primeiro boot gera uma cadeia RSA-4096 real)"

BASE="http://localhost:$PORT" scripts/verify-cert.sh
