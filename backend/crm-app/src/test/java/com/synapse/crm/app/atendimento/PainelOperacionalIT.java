package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;

/** Entrada HTTP real, PostgreSQL/RLS e concessão pela Gestão; fixture descartável. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PainelOperacionalIT extends PostgresIT {
    private static final String ROTA = "/api/v1/atendimentos/painel-operacional";
    private static final String PREFIXO = "Painel operacional IT ";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ResolvedorDePermissoesEfetivas permissoes;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    private final List<UUID> leads = new ArrayList<>();
    private UUID ana;
    private UUID bruno;
    private Instant agora;
    private String gestor;

    @Test
    void alteracaoManualDePresencaPelaApiInvalidaPainelDosGestoresDepoisDoCommit() throws Exception {
        String anterior = jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id=?", String.class, ana);
        var recebidos = new java.util.concurrent.ConcurrentLinkedQueue<JsonNode>();
        var assinante = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        assinante.setConnectionFactory(redis.getConnectionFactory());
        assinante.addMessageListener((mensagem, padrao) -> {
            try {
                JsonNode evento = json.readTree(mensagem.getBody());
                if (evento.path("tipo").asText().equals("PAINEL_OPERACIONAL_ALTERADO")) recebidos.add(evento);
            } catch (Exception erro) { throw new AssertionError(erro); }
        }, new org.springframework.data.redis.listener.ChannelTopic("synapse:aviso-usuario"));
        try {
            assinante.afterPropertiesSet();
            assinante.start();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(assinante::isListening);
            String token = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            var resposta = http.exchange("/api/v1/usuarios/me/presenca", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", anterior.equals("ONLINE") ? "AUSENTE" : "ONLINE"), headers), String.class);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(recebidos).anySatisfy(e -> assertThat(e.path("usuarioId").asText()).isEqualTo(usuario(EMAIL_GESTOR).toString())));
            for (var aviso : recebidos) {
                assertThat(aviso.path("dados").size()).isZero();
                assertThat(aviso.toString()).doesNotContain(ana.toString(), bruno.toString());
            }
            assertThat(ler(gestor, "").path("equipe").toString()).contains(anterior.equals("ONLINE") ? "AUSENTE" : "ONLINE");
        } finally {
            assinante.stop();
            assinante.destroy();
            jdbc.update("UPDATE usuario SET status_presenca=CAST(? AS status_presenca) WHERE id=?", anterior, ana);
        }
    }

    @BeforeEach
    void preparar() {
        ana = usuario(EMAIL_ANA);
        bruno = usuario(EMAIL_BRUNO);
        agora = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        gestor = login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
    }

    @AfterEach
    void limpar() {
        for (UUID lead : leads) {
            jdbc.update("DELETE FROM pedido_entrada_atendimento WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id=?)", lead);
            jdbc.update("DELETE FROM atendimento_participante WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id=?)", lead);
            jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", lead);
            jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", lead);
            jdbc.update("DELETE FROM lead WHERE id = ?", lead);
        }
        jdbc.update("DELETE FROM permissao_perfil_item WHERE alvo = 'atendimentos.painel_operacional'");
        permissoes.aoAlterarAcesso(new com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado(
                java.util.Set.of(ana), false, 0));
    }

    @Test
    void delegadoVeConviteEParticipacao_semVerOutrosLeadsDoResponsavel() throws Exception {
        UUID compartilhado = ciclo("colaboração", bruno, "EM_ATENDIMENTO");
        UUID alheio = ciclo("não convidado", bruno, "EM_ATENDIMENTO");
        conceder(true);
        String token = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        jdbc.update("INSERT INTO pedido_entrada_atendimento (atendimento_id,solicitante_id,tipo,status)"
                + " VALUES (?,?,'CONVITE','PENDENTE')", compartilhado, ana);
        assertThat(ler(token, "/fila?recorte=PENDENTES&busca=Painel%20operacional%20IT").toString())
                .contains(compartilhado.toString(), "\"convitePendente\":true").doesNotContain(alheio.toString());
        jdbc.update("UPDATE pedido_entrada_atendimento SET status='APROVADO' WHERE atendimento_id=?", compartilhado);
        jdbc.update("INSERT INTO atendimento_participante (atendimento_id,usuario_id,origem) VALUES (?,?,'CONVITE')", compartilhado, ana);
        assertThat(ler(token, "/fila?recorte=ATIVOS&busca=Painel%20operacional%20IT").toString())
                .contains(compartilhado.toString(), "\"convitePendente\":false").doesNotContain(alheio.toString());
        assertThat(linha(ler(token, "?busca=Painel%20operacional%20IT"), bruno).path("contagens").path("ativos").asLong()).isEqualTo(1);
        jdbc.update("UPDATE atendimento_participante SET saiu_em=now() WHERE atendimento_id=? AND usuario_id=?", compartilhado, ana);
        assertThat(ler(token, "/fila?busca=Painel%20operacional%20IT").toString()).doesNotContain(compartilhado.toString(),alheio.toString());
    }

    @Test
    void padraoAutorizacaoEConcessaoNaoAmpliaCarteira() throws Exception {
        for (var conta : List.of(new String[]{EMAIL_GESTOR,SENHA_GESTOR},
                new String[]{EMAIL_SUBGESTOR,SENHA_SUBGESTOR}, new String[]{EMAIL_ADMINISTRADOR,SENHA_ADMINISTRADOR})) {
            String token = login(http, conta[0], conta[1]).accessToken();
            assertThat(comToken(http, token, HttpMethod.GET, ROTA, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        String token = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        assertThat(comToken(http, token, HttpMethod.GET, ROTA, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(comToken(http, token, HttpMethod.GET, ROTA + "/fila", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        UUID propria = ciclo("próprio", ana, "EM_ATENDIMENTO");
        UUID alheia = ciclo("alheio", bruno, "EM_ATENDIMENTO");
        conceder(true);
        var corpo = ler(token, "/fila?busca=Painel%20operacional%20IT");
        assertThat(corpo.toString()).contains(propria.toString()).doesNotContain(alheia.toString());
        assertThat(ler(token, "/fila?atendenteId=" + bruno + "&busca=Painel%20operacional%20IT").path("itens")).isEmpty();
        assertThat(ler(token, "").path("equipe").toString()).doesNotContain(bruno.toString());
        conceder(false);
        assertThat(comToken(http, token, HttpMethod.GET, ROTA, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void contagensEFilasComMaisDeUmaPaginaESemDuplicarCiclos() throws Exception {
        for (int i = 0; i < 65; i++) {
            UUID ciclo = ciclo("pagina " + i, ana, "EM_ATENDIMENTO");
            mensagem(ciclo, "LEAD", "ENVIADO", agora.minusSeconds(1800));
        }
        var snapshot = ler(gestor, "?busca=Painel%20operacional%20IT");
        var contagem = linha(snapshot, ana).path("contagens");
        assertThat(contagem.path("emAberto").asLong()).isEqualTo(65);
        assertThat(contagem.path("semResposta").asLong()).isEqualTo(65);
        assertThat(contagem.path("novasMensagens").asLong()).isEqualTo(65);
        var ids = new HashSet<String>();
        String cursor = null;
        do {
            var pagina = ler(gestor, "/fila?recorte=SEM_RESPOSTA&busca=Painel%20operacional%20IT&tamanho=20"
                    + (cursor == null ? "" : "&cursor=" + cursor));
            for (var item : pagina.path("itens")) assertThat(ids.add(item.path("leadId").asText())).isTrue();
            cursor = pagina.path("proximoCursor").isNull() ? null : pagina.path("proximoCursor").asText();
        } while (cursor != null);
        assertThat(ids).hasSize(65);
    }

    @Test
    void leituraFalhaSistemaERespostaIaNaoSaoEquivalentes() throws Exception {
        UUID falha = ciclo("falha", ana, "EM_ATENDIMENTO");
        mensagem(falha, "LEAD", "ENVIADO", agora.minusSeconds(2400));
        mensagem(falha, "LEAD", "ENVIADO", agora.minusSeconds(1800));
        mensagem(falha, "ATENDENTE", "FALHOU", agora.minusSeconds(600));
        mensagem(falha, "SISTEMA", "ENVIADO", agora.minusSeconds(300));
        jdbc.update("INSERT INTO atendimento_leitura (atendimento_id, usuario_id, lido_ate) VALUES (?, ?, ?)", falha, ana, Timestamp.from(agora));
        UUID respondida = ciclo("IA respondeu", ana, "EM_ATENDIMENTO");
        mensagem(respondida, "LEAD", "ENVIADO", agora.minusSeconds(2400));
        mensagem(respondida, "IA", "ENVIADO", agora.minusSeconds(300));
        var contagem = linha(ler(gestor, "?busca=Painel%20operacional%20IT"), ana).path("contagens");
        assertThat(contagem.path("semResposta").asLong()).isEqualTo(1);
        assertThat(contagem.path("novasConversas").asLong()).isEqualTo(1);
        var fila = ler(gestor, "/fila?recorte=SEM_RESPOSTA&busca=Painel%20operacional%20IT").path("itens");
        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).path("atendimentoId").asText()).isEqualTo(falha.toString());
        assertThat(Instant.parse(fila.get(0).path("esperaDesde").asText())).isEqualTo(agora.minusSeconds(2400).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void finalizadosDoDiaNaoIncluemReabertosNemOntem() throws Exception {
        UUID hoje = ciclo("finalizado hoje", ana, "FINALIZADO");
        UUID ontem = ciclo("ontem", ana, "FINALIZADO");
        jdbc.update("UPDATE atendimento SET finalizado_em = ? WHERE id = ?", Timestamp.from(agora.minusSeconds(86400)), ontem);
        UUID reaberto = ciclo("reaberto", ana, "FINALIZADO");
        UUID lead = jdbc.queryForObject("SELECT lead_id FROM atendimento WHERE id = ?", UUID.class, reaberto);
        jdbc.update("INSERT INTO atendimento (lead_id, atendente_id, status, iniciado_em) VALUES (?, ?, 'EM_ATENDIMENTO', ?)", lead, ana, Timestamp.from(agora));
        jdbc.update("UPDATE lead SET status_basico = 'EM_ATENDIMENTO' WHERE id = ?", lead);
        var fila = ler(gestor, "/fila?recorte=FINALIZADOS&busca=Painel%20operacional%20IT").path("itens");
        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).path("atendimentoId").asText()).isEqualTo(hoje.toString());
        assertThat(linha(ler(gestor, "?busca=Painel%20operacional%20IT"), ana).path("contagens").path("finalizados").asLong()).isEqualTo(1);
    }

    @Test
    void detalhesDaPaginaTemEtapaEPreviaLimitadaSemPayloadOuUrlDeMidia() throws Exception {
        UUID texto = ciclo("prévia curta",ana,"EM_ATENDIMENTO");
        mensagem(texto,"LEAD","ENVIADO",agora.minusSeconds(1800));
        jdbc.update("UPDATE mensagem SET conteudo=? WHERE atendimento_id=?", "Mensagem sintética ".repeat(1000),texto);
        UUID etapa = jdbc.queryForObject("SELECT id FROM etapa_atendimento ORDER BY ordem LIMIT 1",UUID.class);
        jdbc.update("UPDATE lead SET etapa_atendimento_id=? WHERE id=(SELECT lead_id FROM atendimento WHERE id=?)",etapa,texto);
        UUID documento = ciclo("documento sem URL na prévia",ana,"EM_ATENDIMENTO");
        mensagem(documento,"LEAD","ENVIADO",agora.minusSeconds(1700));
        jdbc.update("UPDATE mensagem SET tipo='DOCUMENTO',conteudo='https://storage-sintetico.invalid/privado' WHERE atendimento_id=?",documento);
        var itens = ler(gestor,"/fila?busca=Painel%20operacional%20IT").path("itens");
        assertThat(itens).hasSize(2);
        for (var item : itens) {
            assertThat(item.toString()).doesNotContain("storage-sintetico", "metadados", "resumo_ia", "dados_customizados");
            if (item.path("atendimentoId").asText().equals(texto.toString())) {
                assertThat(item.path("ultimaMensagemPreview").asText()).hasSize(120);
                assertThat(item.path("etapaNome").asText()).isNotBlank();
                assertThat(item.path("situacao").asText()).isEqualTo("PENDENTE");
            } else {
                assertThat(item.path("ultimaMensagemPreview").asText()).isEmpty();
                assertThat(item.path("ultimaMensagemTipo").asText()).isEqualTo("DOCUMENTO");
            }
        }
    }

    @Test
    void filtroEntradaValidacaoECursorNaoAlteramAtendimento() throws Exception {
        UUID potencial = ciclo("fila sem dono", null, "EM_IA");
        assertThat(ler(gestor, "/fila?entrada=true&recorte=POTENCIAIS&busca=Painel%20operacional%20IT").path("itens").get(0).path("atendimentoId").asText()).isEqualTo(potencial.toString());
        for (String parametros : List.of("entrada=true&atendenteId=" + ana, "cursor=invalido", "tamanho=101", "recorte=INVENTADO", "recorte=FINALIZADOS&sinal=NOVAS")) {
            var erro = comToken(http, gestor, HttpMethod.GET, ROTA + "/fila?" + parametros, String.class);
            assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(erro.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        }
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, potencial)).isEqualTo("EM_IA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento_leitura WHERE atendimento_id = ?", Long.class, potencial)).isZero();
    }

    private JsonNode ler(String token, String sufixo) throws Exception {
        // TestRestTemplate já codifica a URI; %20 pré-codificado viraria %2520.
        var resposta = comToken(http, token, HttpMethod.GET, ROTA + sufixo.replace("%20", " "), String.class);
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }

    private JsonNode linha(JsonNode snapshot, UUID id) {
        for (var linha : snapshot.path("equipe")) if (linha.path("atendenteId").asText().equals(id.toString())) return linha;
        throw new AssertionError("linha ausente");
    }

    private UUID usuario(String email) { return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email); }

    private UUID ciclo(String nome, UUID dono, String status) {
        UUID lead = UUID.randomUUID();
        leads.add(lead);
        jdbc.update("INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id) VALUES (?, ?, ?, CAST(? AS status_basico_lead), ?)",
                lead, PREFIXO + nome, "55" + System.nanoTime(), status.equals("EM_IA") ? "IA" : status.equals("FINALIZADO") ? "FINALIZADO" : "EM_ATENDIMENTO", dono);
        UUID atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em, finalizado_em) VALUES (?, ?, ?, CAST(? AS status_atendimento), ?, ?)",
                atendimento, lead, dono, status, Timestamp.from(agora.minusSeconds(3600)), status.equals("FINALIZADO") ? Timestamp.from(agora) : null);
        return atendimento;
    }

    private void mensagem(UUID ciclo, String remetente, String status, Instant quando) {
        jdbc.update("INSERT INTO mensagem (atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em) VALUES (?, CAST(? AS remetente_tipo), 'TEXTO', 'Mensagem sintética', CAST(? AS status_entrega), ?)", ciclo, remetente, status, Timestamp.from(quando));
    }

    private void conceder(boolean permitido) {
        long revisao = jdbc.queryForObject("SELECT revisao FROM permissao_perfil WHERE papel = 'ATENDENTE'", Long.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(gestor);
        var r = http.exchange("/api/v1/gestao/permissoes/perfis/ATENDENTE", HttpMethod.PUT,
                new HttpEntity<>(Map.of("revisaoEsperada", revisao, "niveis", Map.of(), "acoes", Map.of("atendimentos.painel_operacional", permitido)), headers), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }
}
