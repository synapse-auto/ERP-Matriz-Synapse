package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Executa o schema real do Traefik com as labels da composicao de producao, nao uma copia delas. */
final class ProxyZApiParaTeste {
    static final String DOMINIO = "crm.zapi.test";
    private static final String PREFIXO = "teste-zapi";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProxyZApiParaTeste() { }

    static GenericContainer<?> criar(int portaBackend) throws Exception {
        var base = compor(false);
        var finalizado = compor(true);
        var labelsBase = base.at("/services/backend/deploy/labels");
        var labels = finalizado.at("/services/backend/deploy/labels");
        assertThat(labels).as("override legado nao deve ser necessario nem divergir do Compose Path do Dokploy")
            .isEqualTo(labelsBase);
        ((ObjectNode) base.at("/services/backend/deploy")).remove("labels");
        ((ObjectNode) finalizado.at("/services/backend/deploy")).remove("labels");
        assertThat(finalizado).as("override nao altera outros servicos, envs, volumes ou politica de deploy").isEqualTo(base);

        // Validar o arquivo configurado como Compose Path, nao apenas uma composicao manual.
        labels = labelsBase;
        String router = "traefik.http.routers." + PREFIXO + "-transicao.";
        String middleware = PREFIXO + "-webhook-sem-origin";
        assertThat(labels.path(router + "rule").asText()).isEqualTo("Host(`" + DOMINIO
            + "`) && (PathPrefix(`/webhook/z-api`) || PathPrefix(`/webhook/canal`))");
        assertThat(labels.path(router + "entrypoints").asText()).isEqualTo("websecure");
        assertThat(labels.path(router + "tls").asText()).isEqualTo("true");
        assertThat(labels.path(router + "tls.certresolver").asText()).isEqualTo("letsencrypt");
        assertThat(labels.path(router + "service").asText()).isEqualTo(PREFIXO + "-backend");
        assertThat(labels.path(router + "middlewares").asText()).isEqualTo(middleware);
        assertThat(labels.path(router + "observability.accessLogs").asText()).isEqualTo("false");
        assertThat(labels.path(router + "observability.tracing").asText()).isEqualTo("false");
        String header = "traefik.http.middlewares." + middleware + ".headers.customrequestheaders.Origin";
        assertThat(labels.has(header)).isTrue();
        assertThat(labels.path(header).asText()).isEmpty();
        assertThat(labels.path("traefik.http.routers." + PREFIXO + "-backend.middlewares").isMissingNode()).isTrue();
        assertThat(labels.path(router + "priority").asInt()).isGreaterThan(
            labels.path("traefik.http.routers." + PREFIXO + "-backend.priority").asInt());

        var config = JSON.createObjectNode();
        var http = config.putObject("http");
        var routers = http.putObject("routers");
        for (String nome : List.of("transicao", "backend")) {
            String chave = "traefik.http.routers." + PREFIXO + "-" + nome + ".";
            var rota = routers.putObject(PREFIXO + "-" + nome);
            rota.put("rule", labels.path(chave + "rule").asText());
            rota.putArray("entryPoints").add(labels.path(chave + "entrypoints").asText());
            rota.put("service", labels.path(chave + "service").asText());
            rota.put("priority", labels.path(chave + "priority").asInt());
            rota.putObject("tls"); // Certificado de teste do Traefik, sem ACME/chamada externa.
            if (labels.has(chave + "middlewares")) rota.putArray("middlewares").add(labels.path(chave + "middlewares").asText());
            if (labels.has(chave + "observability.accessLogs")) {
                rota.putObject("observability")
                    .put("accessLogs", labels.path(chave + "observability.accessLogs").asBoolean())
                    .put("tracing", labels.path(chave + "observability.tracing").asBoolean());
            }
        }
        http.putObject("middlewares").putObject(middleware).putObject("headers")
            .putObject("customRequestHeaders").put("Origin", labels.path(header).asText());
        http.putObject("services").putObject(PREFIXO + "-backend").putObject("loadBalancer")
            .putArray("servers").addObject().put("url", "http://host.testcontainers.internal:" + portaBackend);
        org.testcontainers.Testcontainers.exposeHostPorts(portaBackend);
        return new GenericContainer<>("traefik:v3.6.7")
            .withExposedPorts(8443)
            // Somente fixture sintetica. Evita o upload tar/PAX do Testcontainers, cuja
            // combinacao transitiva Commons Compress/IO existente falha em FileTimes.
            .withEnv("CONFIG_PROXY_TESTE", config.toString())
            .withCommand("sh", "-ec", "printf '%s' \"$CONFIG_PROXY_TESTE\" > /tmp/z-api-proxy.json; "
                + "exec traefik --providers.file.filename=/tmp/z-api-proxy.json "
                + "--entrypoints.websecure.address=:8443 --accesslog=true --accesslog.format=json")
            .waitingFor(Wait.forHttps("/health/liveness").forPort(8443).allowInsecure()
                .forStatusCodeMatching(status -> status == 200 || status == 404).withStartupTimeout(Duration.ofSeconds(60)));
    }

    private static JsonNode compor(boolean override) throws Exception {
        Path raiz = Path.of("../..").toAbsolutePath().normalize();
        Path vazio = Files.createTempFile("zapi-compose-env-", ".env");
        Path saida = Files.createTempFile("zapi-compose-saida-", ".json");
        Path erros = Files.createTempFile("zapi-compose-erros-", ".log");
        try {
            var comando = new java.util.ArrayList<>(List.of("docker", "compose", "--env-file", vazio.toString(),
                "-f", "docker/dokploy-stack.yml"));
            if (override) comando.addAll(List.of("-f", "docker/dokploy-z-api-transicao.override.yml"));
            comando.addAll(List.of("config", "--format", "json"));
            var processo = new ProcessBuilder(comando).directory(raiz.toFile());
            String yaml = Files.readString(raiz.resolve("docker/dokploy-stack.yml"));
            var variaveis = Pattern.compile("\\$\\{([A-Z0-9_]+)([^}]*)}").matcher(yaml);
            while (variaveis.find()) {
                processo.environment().remove(variaveis.group(1));
                if (variaveis.group(2).startsWith(":?")) processo.environment().put(variaveis.group(1), "teste-zapi");
            }
            processo.environment().putAll(Map.of("SYNAPSE_DOMINIO", DOMINIO, "TRAEFIK_ROUTER_PREFIX", PREFIXO,
                "SYNAPSE_TIMEZONE", "UTC"));
            var iniciado = processo.redirectOutput(saida.toFile()).redirectError(erros.toFile()).start();
            if (!iniciado.waitFor(45, TimeUnit.SECONDS)) {
                iniciado.destroyForcibly();
                throw new AssertionError("composicao final excedeu o prazo");
            }
            assertThat(iniciado.exitValue()).as("docker compose config aceitou base/override com envs sinteticas").isZero();
            return JSON.readTree(Files.readString(saida));
        } finally {
            Files.deleteIfExists(vazio); Files.deleteIfExists(saida); Files.deleteIfExists(erros);
        }
    }
}
