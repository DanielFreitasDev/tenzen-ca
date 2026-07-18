# syntax=docker/dockerfile:1

########################################################################
# Build: JDK 25 + Maven Wrapper. O frontend-maven-plugin baixa Node/npm
# e compila o frontend (Tailwind v4 + esbuild) dentro do proprio Maven.
########################################################################
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

# curl para o bootstrap do Maven Wrapper (a imagem JDK nao traz curl/wget).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Java/Maven ignoram http_proxy do ambiente. Se (e somente se) o build recebeu
# proxy pelos build args predefinidos do BuildKit — caso de maquinas atras de
# proxy corporativo —, materializa num settings.xml usado por todos os ./mvnw;
# o frontend-maven-plugin repassa esses proxies ao download do Node e ao npm.
# Sem proxy no ambiente (a maioria), o arquivo sai vazio e nada muda.
RUN <<'EOS'
set -eu
proxy="${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-${http_proxy:-}}}}"
{
  echo '<settings>'
  if [ -n "$proxy" ]; then
    rest="${proxy#*://}"; rest="${rest%%/*}"
    auth=''
    case "$rest" in *@*) auth="${rest%@*}"; rest="${rest##*@}" ;; esac
    host="${rest%%:*}"; port="${rest##*:}"
    [ "$port" = "$host" ] && port=80
    cred=''
    if [ -n "$auth" ]; then
      user="${auth%%:*}"; pass=''
      case "$auth" in *:*) pass="${auth#*:}" ;; esac
      cred="<username>${user}</username><password>${pass}</password>"
    fi
    echo '  <proxies>'
    for scheme in http https; do
      echo "    <proxy><id>${scheme}</id><active>true</active><protocol>${scheme}</protocol><host>${host}</host><port>${port}</port>${cred}</proxy>"
    done
    echo '  </proxies>'
  fi
  echo '</settings>'
} > /workspace/maven-settings.xml
EOS

# So o descritor de build primeiro: resolucao de dependencias fica em camada
# propria e no cache do BuildKit (~/.m2 persiste entre builds).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -s maven-settings.xml dependency:go-offline

COPY package.json package-lock.json ./
COPY scripts/ scripts/
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm \
    ./mvnw -B -ntp -s maven-settings.xml -DskipTests package \
 && cp target/tenzen-ca-*.jar application.jar

# Explode o fat jar em camadas (dependencias mudam pouco, app muda sempre):
# imagem final com cache melhor e pull incremental barato.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

########################################################################
# Runtime: JRE 25, usuario sem privilegios, dados persistentes em /data.
########################################################################
FROM eclipse-temurin:25-jre-noble AS runtime

LABEL org.opencontainers.image.title="Tenzen CA" \
      org.opencontainers.image.description="Autoridade Certificadora de testes estilo ICP-Brasil (sem valor legal)" \
      org.opencontainers.image.vendor="dev.tenzen"

# curl e usado apenas pelo HEALTHCHECK (a imagem JRE nao traz curl/wget).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --user-group --home-dir /data --shell /usr/sbin/nologin tenzen \
 && install -d -o tenzen -g tenzen -m 0750 /data

WORKDIR /opt/tenzen-ca
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# Dentro do conteiner e preciso escutar em todas as interfaces; quem controla a
# exposicao real e o mapeamento de portas do compose (default: so 127.0.0.1).
ENV SERVER_ADDRESS=0.0.0.0 \
    APP_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

USER tenzen

# A CRL fica fora do basic-auth por design, entao serve de healthcheck mesmo
# com autenticacao ligada — e ainda comprova que a cadeia da AC esta no ar.
# --noproxy '*': ignora proxies injetados no ambiente do conteiner.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS --noproxy '*' -o /dev/null http://127.0.0.1:8080/crl/tenzen-ca.crl || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
