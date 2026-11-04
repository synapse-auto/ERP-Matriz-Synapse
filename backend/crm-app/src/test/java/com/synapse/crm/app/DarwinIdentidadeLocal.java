package com.synapse.crm.app;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** CRM REAL com containers descartáveis e HTTP Darwin sintético, opt-in e só em test-classes. */
public final class DarwinIdentidadeLocal {
    private DarwinIdentidadeLocal() {}
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("DARWIN_VISUAL_LOCAL"))) throw new IllegalStateException("fixture local opt-in");
        var postgres = new PostgreSQLContainer<>("postgres:15-alpine").withDatabaseName("darwin_visual");
        var redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        postgres.start(); redis.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .placeholders(Map.of("telefone_ddi_padrao", "55")).load().migrate();
        for (String pool : new String[]{"general", "chat"}) {
            System.setProperty("synapse.datasource."+pool+".url",postgres.getJdbcUrl());
            System.setProperty("synapse.datasource."+pool+".username",postgres.getUsername());
            System.setProperty("synapse.datasource."+pool+".password",postgres.getPassword());
            System.setProperty("synapse.datasource."+pool+".hikari.maximum-pool-size","2");
        }
        System.setProperty("spring.data.redis.host",redis.getHost());
        System.setProperty("spring.data.redis.port",redis.getMappedPort(6379).toString());
        System.setProperty("synapse.seguranca.jwt-segredo",PostgresIT.SEGREDO_JWT_DE_TESTE);
        System.setProperty("synapse.canal.whatsapp.provedor","fake");
        System.setProperty("synapse.agendamento.habilitado","false");
        System.setProperty("synapse.saude.critica.monitoramento-habilitado","false");
        System.setProperty("synapse.tempo-real.origens-permitidas","http://localhost:3014");
        System.setProperty("synapse.seguranca.frontend-origem","http://localhost:3014");
        var estado = new AtomicReference<>("sucesso");
        var liberar = new AtomicReference<CompletableFuture<Void>>(new CompletableFuture<>());
        var banco = new AtomicReference<JdbcTemplate>();
        UUID lead = UUID.fromString("da000000-0000-4000-8000-000000000001");
        var fake = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        fake.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        fake.createContext("/controle", troca -> {
            String modo = troca.getRequestURI().getRawQuery();
            estado.set(modo);
            if ("lento".equals(modo)) liberar.set(new CompletableFuture<>()); else liberar.get().complete(null);
            if ("conflito".equals(modo)) banco.get().update("UPDATE lead SET nome='Correção manual sintética' WHERE id=?",lead);
            troca.sendResponseHeaders(204,-1); troca.close();
        });
        fake.createContext("/", troca -> {
            try {
                if ("lento".equals(estado.get())) liberar.get().get(20,TimeUnit.SECONDS);
                String corpo = "{\"cpf\":\"529.982.247-25\",\"name\":\"Pessoa Darwin Sintética\",\"email\":\"pessoa.sintetica@example.invalid\"}";
                int status = "erro".equals(estado.get()) ? 403 : "vazio".equals(estado.get()) ? 404 : 200;
                byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().set("Content-Type","application/json");
                troca.sendResponseHeaders(status,bytes.length); troca.getResponseBody().write(bytes);
            } catch (Exception erro) { /* Abort/timeout de teste não expõe corpo nem identidade. */ }
            finally { troca.close(); }
        });
        fake.start();
        String urlFake = "http://127.0.0.1:"+fake.getAddress().getPort();
        System.setProperty("synapse.integracoes.darwin.habilitado","true");
        System.setProperty("synapse.integracoes.darwin.url",urlFake);
        System.setProperty("synapse.integracoes.darwin.token","token-medico-sintetico");
        System.setProperty("synapse.integracoes.darwin.timeout-consulta","30s");
        var contexto = SpringApplication.run(SynapseCrmApplication.class,"--spring.profiles.active=dev","--server.address=127.0.0.1","--server.port=8094");
        var jdbc = contexto.getBean(JdbcTemplate.class); banco.set(jdbc);
        if (!"darwin_visual".equals(jdbc.queryForObject("SELECT current_database()",String.class))) throw new IllegalStateException("fixture fora do banco descartável");
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'",UUID.class);
        UUID atendimento = UUID.fromString("da000000-0000-4000-8000-000000000002");
        jdbc.update("INSERT INTO lead(id,nome,telefone,status_basico,atendente_responsavel_id,num_atendimentos) VALUES (?,?,?,'EM_ATENDIMENTO',?,1)",lead,"Contato sintético Darwin","5561999900777",ana);
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status,iniciado_em) VALUES (?,?,'ca000000-0000-4000-8000-000000000001',?,'EM_ATENDIMENTO',now())",atendimento,lead,ana);
        jdbc.update("INSERT INTO mensagem(atendimento_id,remetente_tipo,tipo,conteudo,enviado_em,status_entrega) VALUES (?,'LEAD','TEXTO','Conversa sintética para validação local.',now(),'ENTREGUE')",atendimento);
        Files.createDirectories(Path.of("..","output"));
        Files.writeString(Path.of("..","output","darwin-local-runtime.json"),"{\"backend\":\"http://localhost:8094\",\"controle\":\""+urlFake+"/controle\",\"leadId\":\""+lead+"\"}");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { fake.stop(0); postgres.stop(); redis.stop(); }));
    }
}
