# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/backend

# POMs primeiro: dependencias mudam muito menos que o codigo e ficam em camada reaproveitavel.
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
COPY backend/crm-shared-kernel/pom.xml crm-shared-kernel/pom.xml
COPY backend/crm-core/pom.xml crm-core/pom.xml
COPY backend/crm-equipe/pom.xml crm-equipe/pom.xml
COPY backend/crm-atendimento/pom.xml crm-atendimento/pom.xml
COPY backend/crm-automacao-config/pom.xml crm-automacao-config/pom.xml
COPY backend/crm-campanhas/pom.xml crm-campanhas/pom.xml
COPY backend/crm-relatorios/pom.xml crm-relatorios/pom.xml
COPY backend/crm-app/pom.xml crm-app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B --no-transfer-progress dependency:go-offline

COPY backend/ ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B --no-transfer-progress -DskipTests package

# O modo tools do Spring Boot 3.5 extrai o fat jar nas quatro camadas do layers.idx.
FROM eclipse-temurin:21-jre AS layers
WORKDIR /builder
COPY --from=build /workspace/backend/crm-app/target/crm-app-*-exec.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 synapse \
    && useradd --system --uid 10001 --gid synapse --home-dir /application \
        --shell /usr/sbin/nologin synapse

WORKDIR /application
COPY --from=layers --chown=synapse:synapse /builder/extracted/dependencies/ ./
COPY --from=layers --chown=synapse:synapse /builder/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=synapse:synapse /builder/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=synapse:synapse /builder/extracted/application/ ./

USER 10001:10001
EXPOSE 8080
HEALTHCHECK CMD curl --fail --silent --show-error --output /dev/null \
    http://127.0.0.1:8080/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "application.jar"]
