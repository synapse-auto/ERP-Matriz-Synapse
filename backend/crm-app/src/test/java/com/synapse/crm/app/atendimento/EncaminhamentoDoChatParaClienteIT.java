package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

/**
 * Encaminhar do Chat Interno para o cliente (docs/61), pelos pontos de entrada reais: HTTP, Postgres,
 * RLS, outbox e o canal fake no lugar do provedor. Os adaptadores Meta e UZAPI têm os próprios testes
 * com o conteúdo que o chat produz ({@code EncaminhamentoDoChatNosProvedoresTest}).
 *
 * <p>A mensagem do chat entra por JDBC (o que interessa é o encaminhamento, não o upload do chat) e a
 * conversa nasce pela API, para as regras de participação serem as reais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000"
        })
class EncaminhamentoDoChatParaClienteIT extends PostgresIT {

    private static final String PREFIXO = "E-fwd-chat-";
    private static final String ENCAMINHAR = "/api/v1/atendimentos/%s/encaminhamento-do-chat-interno";
    private static final String ACAO_AUDITADA = "ENCAMINHAR_CHAT_INTERNO_PARA_CLIENTE";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private CanalFake canal;
    @Autowired private PublicadorDaOutbox publicador;

    private final Map<String, String> tokens = new HashMap<>();
    private final List<UUID> conversas = new ArrayList<>();
    private UUID ana;
    private UUID bruno;
    private UUID gestor;

    @BeforeEach
    void preparar() {
        limpar();
        canal.limpar();
        canal.abrirJanela();
        canal.religar();
        ana = idDoUsuario(EMAIL_ANA);
        bruno = idDoUsuario(EMAIL_BRUNO);
        gestor = idDoUsuario(EMAIL_GESTOR);
    }

    @AfterEach
    void limpar() {
        String leads = "SELECT id FROM lead WHERE nome LIKE '" + PREFIXO + "%'";
        String atendimentos = "SELECT id FROM atendimento WHERE lead_id IN (" + leads + ")";
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM chat_interno_encaminhamento_cliente WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM outbox_evento WHERE payload->>'atendimentoId' IN (SELECT id::text FROM atendimento WHERE lead_id IN (" + leads + "))");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM pedido_entrada_atendimento WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM atendimento_participante WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM lead WHERE nome LIKE '" + PREFIXO + "%'");
        for (UUID conversa : conversas) {
            jdbc.update("DELETE FROM chat_interno_mensagem WHERE conversa_id = ?", conversa);
            jdbc.update("DELETE FROM chat_interno_participante WHERE conversa_id = ?", conversa);
            jdbc.update("DELETE FROM chat_interno_conversa WHERE id = ?", conversa);
        }
        conversas.clear();
    }

    // ---------------------------------------------------------------- um tipo de cada

    @Test
    @DisplayName("texto: vai pelo fluxo oficial, o telefone e do backend e o canal recebe o texto da linha persistida")
    void encaminhaTexto() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "Oi, segue o orçamento aprovado.");

        // O payload tenta indicar outro lead e outro telefone: o backend ignora, só vale o atendimento da URL.
        Map<String, Object> corpo = new LinkedHashMap<>(corpo(conversa, mensagem));
        corpo.put("leadId", UUID.randomUUID().toString());
        corpo.put("telefone", "5500000000000");
        corpo.put("conteudo", "texto trocado pelo navegador");
        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo, "k-texto");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode r = json.readTree(resposta.getBody());
        assertThat(r.path("statusEntrega").asText()).isEqualTo("PENDENTE");
        assertThat(r.path("transferiuOLead").asBoolean()).isFalse();
        assertThat(r.path("reutilizado").asBoolean()).isFalse();
        UUID externa = UUID.fromString(r.path("mensagemExternaId").asText());
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT atendimento_id, remetente_tipo::text AS remetente_tipo, remetente_id, tipo::text AS tipo, conteudo, midia_url FROM mensagem WHERE id = ?",
                externa);
        assertThat(linha.get("atendimento_id")).isEqualTo(destino.atendimentoId());
        assertThat(linha.get("remetente_id")).isEqualTo(ana);
        assertThat(linha.get("tipo")).isEqualTo("TEXTO");
        assertThat(linha.get("conteudo")).isEqualTo("Oi, segue o orçamento aprovado.");

        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            CanalGateway.Envio envio = envioDe(externa);
            assertThat(envio.telefoneDestino()).isEqualTo(destino.telefone());
            // A assinatura do atendente ("*Ana Atendente:*") e do envio oficial de qualquer mensagem manual.
            assertThat(((ConteudoDeEnvio.MensagemLivre) envio.conteudo()).texto())
                    .endsWith("Oi, segue o orçamento aprovado.")
                    .contains("Ana Atendente");
        });
        assertThat(statusExterno(externa)).isIn("ENVIADO", "ENTREGUE", "LIDO");
    }

    static Stream<Arguments> midias() {
        return Stream.of(
                Arguments.of("IMAGEM", TipoMensagem.IMAGEM, "image/png", "foto.png", "foto.png", "veja a foto"),
                Arguments.of("VIDEO", TipoMensagem.VIDEO, "video/mp4", "filme.mp4", "filme.mp4", "veja o vídeo"),
                Arguments.of("AUDIO", TipoMensagem.AUDIO, "audio/ogg", "voz.ogg", "voz.ogg", null),
                Arguments.of(
                        "DOCUMENTO", TipoMensagem.DOCUMENTO, "application/pdf", "orçamento 2026.pdf",
                        "or_amento_2026.pdf", "segue o documento"));
    }

    @ParameterizedTest(name = "{0}: preserva tipo, mimetype, nome, legenda e aponta para o mesmo objeto")
    @MethodSource("midias")
    void encaminhaMidia(String tipo, TipoMensagem esperado, String mime, String nome, String nomeEsperado, String legenda)
            throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        String referencia = "midia/" + UUID.randomUUID() + ".bin";
        UUID mensagem = mensagemDeMidia(conversa, bruno, tipo, mime, nome, 2048, legenda, referencia);

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-" + tipo);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID externa = UUID.fromString(json.readTree(resposta.getBody()).path("mensagemExternaId").asText());
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT tipo::text AS tipo, midia_url, midia_metadados::text AS metadados FROM mensagem WHERE id = ?",
                externa);
        assertThat(linha.get("tipo")).isEqualTo(tipo);
        assertThat(linha.get("midia_url")).as("sem copia: o mesmo objeto do storage").isEqualTo(referencia);
        JsonNode metadados = json.readTree((String) linha.get("metadados"));
        assertThat(metadados.path("mimetype").asText()).isEqualTo(mime);
        assertThat(metadados.path("nome").asText()).isEqualTo(nomeEsperado);
        assertThat(metadados.path("tamanho").asLong()).isEqualTo(2048);
        assertThat(metadados.path("legenda").isMissingNode() ? null : metadados.path("legenda").asText()).isEqualTo(legenda);

        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            CanalGateway.Envio envio = envioDe(externa);
            assertThat(envio.telefoneDestino()).isEqualTo(destino.telefone());
            ConteudoDeEnvio.MensagemMidia midia = (ConteudoDeEnvio.MensagemMidia) envio.conteudo();
            assertThat(midia.tipo()).isEqualTo(esperado);
            assertThat(midia.referenciaStorage()).isEqualTo(referencia);
            if (legenda == null) {
                assertThat(midia.legenda()).isNull();
            } else {
                assertThat(midia.legenda()).endsWith(legenda);
            }
        });
    }

    // ---------------------------------------------------------------- RN-CRM-06 e convite

    @Test
    @DisplayName("sem responsavel: quem encaminha assume o lead (RN-CRM-06), sem convite, e a transferencia e auditada")
    void semResponsavelAssume() throws Exception {
        Destino destino = destino(null, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "primeira resposta");

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-assume");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode r = json.readTree(resposta.getBody());
        assertThat(r.path("transferiuOLead").asBoolean()).isTrue();
        assertThat(r.path("conviteCriado").asBoolean()).isFalse();
        assertThat(responsavelDoLead(destino)).isEqualTo(ana);
        assertThat(responsavelDoAtendimento(destino)).isEqualTo(ana);
        assertThat(convitesPendentes(destino)).isZero();
        assertThat(acoesAuditadas(destino)).contains("ENVIO_COM_TRANSFERENCIA_DE_LEAD", ACAO_AUDITADA);
    }

    @Test
    @DisplayName("com responsavel: gestor que nao participa NAO transfere, recebe um convite, e o responsavel continua")
    void comResponsavelMantemEConvida() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "texto interno");

        ResponseEntity<String> resposta = encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, mensagem), "k-gestor-1");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode r = json.readTree(resposta.getBody());
        assertThat(r.path("transferiuOLead").asBoolean()).isFalse();
        assertThat(r.path("conviteCriado").asBoolean()).isTrue();
        assertThat(responsavelDoLead(destino)).isEqualTo(ana);
        assertThat(responsavelDoAtendimento(destino)).isEqualTo(ana);
        assertThat(convitesPendentes(destino)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT solicitante_id FROM pedido_entrada_atendimento WHERE atendimento_id = ? AND tipo = 'CONVITE'",
                        UUID.class,
                        destino.atendimentoId()))
                .isEqualTo(gestor);
        UUID externa = UUID.fromString(r.path("mensagemExternaId").asText());
        assertThat(jdbc.queryForObject("SELECT remetente_id FROM mensagem WHERE id = ?", UUID.class, externa)).isEqualTo(gestor);
        assertThat(acoesAuditadas(destino)).contains(ACAO_AUDITADA, "CONVITE_ATENDIMENTO_CRIADO");

        // Novo encaminhamento, outra mensagem e outra chave: convite pendente nao se duplica.
        UUID outra = mensagemDeTexto(conversa, ana, "segunda");
        JsonNode segunda = json.readTree(
                encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, outra), "k-gestor-2").getBody());
        assertThat(segunda.path("conviteCriado").asBoolean()).isFalse();
        assertThat(convitesPendentes(destino)).isEqualTo(1);
        assertThat(responsavelDoLead(destino)).isEqualTo(ana);
    }

    @Test
    @DisplayName("convite aceito: o gestor participa, nao recebe outro convite e continua sem assumir o lead")
    void aposAceitarNaoRecebeOutroConvite() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID primeira = mensagemDeTexto(conversa, ana, "um");
        encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, primeira), "k-aceite-1");
        UUID pedido = jdbc.queryForObject(
                "SELECT id FROM pedido_entrada_atendimento WHERE atendimento_id = ? AND tipo = 'CONVITE'",
                UUID.class,
                destino.atendimentoId());

        ResponseEntity<String> aceite = chamar(
                EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.POST, "/api/v1/atendimentos/pedidos-entrada/" + pedido + "/aprovar", null, null);
        assertThat(aceite.getStatusCode().is2xxSuccessful()).isTrue();

        UUID segunda = mensagemDeTexto(conversa, ana, "dois");
        JsonNode r = json.readTree(
                encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, segunda), "k-aceite-2").getBody());

        assertThat(r.path("conviteCriado").asBoolean()).isFalse();
        assertThat(r.path("transferiuOLead").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pedido_entrada_atendimento WHERE atendimento_id = ? AND tipo = 'CONVITE'",
                        Integer.class,
                        destino.atendimentoId()))
                .isEqualTo(1);
        assertThat(responsavelDoLead(destino)).isEqualTo(ana);
    }

    @Test
    @DisplayName("gestor que ja participa por entrada direta: nao transfere e nao recebe convite")
    void participanteQueEntrouDiretoNaoTransfere() throws Exception {
        Destino destino = destino(ana, true);
        jdbc.update("INSERT INTO atendimento_participante (atendimento_id, usuario_id, origem) VALUES (?, ?, 'ENTRADA_DIRETA')",
                destino.atendimentoId(), gestor);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "participando");

        JsonNode r = json.readTree(
                encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, mensagem), "k-direto").getBody());

        assertThat(r.path("transferiuOLead").asBoolean()).isFalse();
        assertThat(r.path("conviteCriado").asBoolean()).isFalse();
        assertThat(convitesPendentes(destino)).isZero();
        assertThat(responsavelDoLead(destino)).isEqualTo(ana);
        assertThat(responsavelDoAtendimento(destino)).isEqualTo(ana);
    }

    @Test
    @DisplayName("o proprio responsavel encaminha: sem convite e sem troca de dono")
    void responsavelEncaminha() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "da equipe");

        JsonNode r = json.readTree(
                encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-dono").getBody());

        assertThat(r.path("transferiuOLead").asBoolean()).isFalse();
        assertThat(r.path("conviteCriado").asBoolean()).isFalse();
        assertThat(convitesPendentes(destino)).isZero();
    }

    // ---------------------------------------------------------------- prévia

    @Test
    @DisplayName("previa: mostra cliente, telefone mascarado, atendimento e efeito; nao grava nada")
    void previaNaoGravaENaoExpoeOTelefone() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "prévia");
        int mensagensAntes = contarMensagens(destino);

        ResponseEntity<String> resposta = previa(EMAIL_GESTOR, destino.atendimentoId(), conversa, mensagem);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode p = json.readTree(resposta.getBody());
        assertThat(p.path("clienteNome").asText()).startsWith(PREFIXO);
        assertThat(p.path("atendimentoId").asText()).isEqualTo(destino.atendimentoId().toString());
        assertThat(p.path("efeito").asText()).isEqualTo("MANTEM_RESPONSAVEL_E_CONVIDA");
        assertThat(p.path("responsavelNome").asText()).isNotBlank();
        assertThat(p.path("podeEnviar").asBoolean()).isTrue();
        assertThat(p.path("telefoneMascarado").asText()).contains("*").doesNotContain(destino.telefone());
        assertThat(resposta.getBody()).doesNotContain(destino.telefone());
        assertThat(contarMensagens(destino)).isEqualTo(mensagensAntes);
        assertThat(convitesPendentes(destino)).isZero();
    }

    @Test
    @DisplayName("previa: sem responsavel o efeito e assumir o lead; fora da janela e atendimento finalizado bloqueiam")
    void previaBloqueios() throws Exception {
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "x");

        Destino semDono = destino(null, true);
        assertThat(json.readTree(previa(EMAIL_ANA, semDono.atendimentoId(), conversa, mensagem).getBody()).path("efeito").asText())
                .isEqualTo("ASSUME_O_LEAD");

        canal.fecharJanela();
        Destino aberto = destino(ana, true);
        JsonNode fora = json.readTree(previa(EMAIL_ANA, aberto.atendimentoId(), conversa, mensagem).getBody());
        assertThat(fora.path("podeEnviar").asBoolean()).isFalse();
        assertThat(fora.path("bloqueio").asText()).isEqualTo("FORA_DA_JANELA");
        canal.abrirJanela();

        Destino finalizado = destino(ana, false);
        JsonNode fim = json.readTree(previa(EMAIL_ANA, finalizado.atendimentoId(), conversa, mensagem).getBody());
        assertThat(fim.path("podeEnviar").asBoolean()).isFalse();
        assertThat(fim.path("bloqueio").asText()).isEqualTo("ATENDIMENTO_FINALIZADO");
    }

    // ---------------------------------------------------------------- autorização e cliente errado

    @Test
    @DisplayName("quem nao participa da conversa interna nao encaminha nem ve a previa (403) e nada e gravado")
    void naoParticipanteDaConversa() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_GESTOR, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "reservada");

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-intruso");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(previa(EMAIL_ANA, destino.atendimentoId(), conversa, mensagem).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    @Test
    @DisplayName("atendente nao alcanca o atendimento do colega: 404, sem mensagem, sem elo e sem convite")
    void atendenteNaoAlcancaAtendimentoDeColega() throws Exception {
        Destino daAna = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, ana, "x");

        ResponseEntity<String> resposta = encaminhar(EMAIL_BRUNO, daAna.atendimentoId(), corpo(conversa, mensagem), "k-colega");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(previa(EMAIL_BRUNO, daAna.atendimentoId(), conversa, mensagem).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(nadaFoiEnviado(daAna)).isTrue();
        assertThat(responsavelDoLead(daAna)).isEqualTo(ana);
    }

    @Test
    @DisplayName("sem a capacidade atendimentos.responder: 403 e nada e gravado")
    void semPermissaoParaResponder() throws Exception {
        Destino destino = destino(bruno, true);
        UUID conversa = conversaDireta(EMAIL_BRUNO, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "x");
        String gestorToken = token(EMAIL_GESTOR, SENHA_GESTOR);
        long revisao = jdbc.query("SELECT revisao FROM permissao_perfil WHERE papel = CAST('ATENDENTE' AS papel_usuario)",
                (rs, i) -> rs.getLong(1)).stream().findFirst().orElse(0L);
        try {
            ResponseEntity<String> negar = chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE",
                    Map.of("revisaoEsperada", revisao, "niveis", Map.of(), "acoes", Map.of("atendimentos.responder", false)), null);
            assertThat(negar.getStatusCode()).as(negar.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(gestorToken).isNotBlank();

            ResponseEntity<String> resposta = encaminhar(EMAIL_BRUNO, destino.atendimentoId(), corpo(conversa, mensagem), "k-sem-permissao");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(previa(EMAIL_BRUNO, destino.atendimentoId(), conversa, mensagem).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(nadaFoiEnviado(destino)).isTrue();
        } finally {
            long atual = jdbc.query("SELECT revisao FROM permissao_perfil WHERE papel = CAST('ATENDENTE' AS papel_usuario)",
                    (rs, i) -> rs.getLong(1)).stream().findFirst().orElse(0L);
            chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE",
                    Map.of("revisaoEsperada", atual, "niveis", Map.of(), "acoes", Map.of("atendimentos.responder", true)), null);
        }
    }

    @Test
    @DisplayName("mensagem de outra conversa nao e encaminhada: o par conversa/mensagem tem de bater")
    void mensagemDeOutraConversa() throws Exception {
        Destino destino = destino(ana, true);
        UUID minha = conversaDireta(EMAIL_ANA, bruno);
        UUID alheia = conversaDireta(EMAIL_GESTOR, bruno);
        UUID mensagemAlheia = mensagemDeTexto(alheia, bruno, "nao e da minha conversa");

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(minha, mensagemAlheia), "k-cruzada");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    @Test
    @DisplayName("mesma Idempotency-Key para outro atendimento (cliente errado) responde 409 e nao envia ao segundo")
    void chaveReutilizadaParaOutroCliente() throws Exception {
        Destino primeiro = destino(ana, true);
        Destino segundo = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "um cliente so");
        assertThat(encaminhar(EMAIL_ANA, primeiro.atendimentoId(), corpo(conversa, mensagem), "k-mesma").getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> repetida = encaminhar(EMAIL_ANA, segundo.atendimentoId(), corpo(conversa, mensagem), "k-mesma");

        assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nadaFoiEnviado(segundo)).isTrue();
    }

    @Test
    @DisplayName("atendimento finalizado: 409, sem mensagem, sem outbox, sem convite e sem abrir outro atendimento")
    void atendimentoFinalizado() throws Exception {
        Destino destino = destino(ana, false);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "tarde demais");
        int atendimentosAntes = contarAtendimentosDoLead(destino);

        ResponseEntity<String> resposta = encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, mensagem), "k-finalizado");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nadaFoiEnviado(destino)).isTrue();
        assertThat(convitesPendentes(destino)).isZero();
        assertThat(contarAtendimentosDoLead(destino)).isEqualTo(atendimentosAntes);
    }

    @Test
    @DisplayName("texto fora da janela de 24h: 422 FORA_DA_JANELA e nada e gravado")
    void textoForaDaJanela() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "fora");
        canal.fecharJanela();

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-janela");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resposta.getBody()).path("motivo").asText()).isEqualTo("FORA_DA_JANELA");
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    // ---------------------------------------------------------------- conteúdo inválido

    @Test
    @DisplayName("evento de sistema, mensagem apagada e contato compartilhado nao sao conteudo para cliente (422)")
    void conteudoQueNaoEDoCliente() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID sistema = mensagemDeSistema(conversa, ana);
        UUID apagada = mensagemDeTexto(conversa, bruno, "vai sumir");
        jdbc.update("UPDATE chat_interno_mensagem SET removida_em = now(), conteudo = NULL WHERE id = ?", apagada);
        UUID contato = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, midia_metadados) VALUES (?, ?, ?, 'CONTATO'::tipo_mensagem, '{\"contatos\":[]}'::jsonb)",
                contato, conversa, bruno);

        for (UUID invalida : List.of(sistema, apagada, contato)) {
            ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, invalida), "k-" + invalida);
            assertThat(resposta.getStatusCode()).as(invalida.toString()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    @Test
    @DisplayName("arquivo invalido: .xlsm (aceito no chat) e video fora de mp4/3gp sao recusados (422 TIPO_NAO_SUPORTADO)")
    void arquivoInvalidoParaOCliente() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID xlsm = mensagemDeMidia(conversa, bruno, "DOCUMENTO", "application/vnd.ms-excel.sheet.macroEnabled.12",
                "planilha.xlsm", 100, null, "midia/x.xlsm");
        UUID quicktime = mensagemDeMidia(conversa, bruno, "VIDEO", "video/quicktime", "filme.mov", 100, null, "midia/x.mov");
        UUID tipoErrado = mensagemDeMidia(conversa, bruno, "IMAGEM", "application/pdf", "nao-e-imagem.png", 100, null, "midia/x.png");

        for (UUID mensagem : List.of(xlsm, quicktime, tipoErrado)) {
            ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-" + mensagem);
            assertThat(resposta.getStatusCode()).as(mensagem.toString()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(json.readTree(resposta.getBody()).path("motivo").asText()).isEqualTo("TIPO_NAO_SUPORTADO");
        }
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    @Test
    @DisplayName("arquivo acima do limite do envio ao cliente e sem tamanho registrado sao recusados")
    void arquivoAcimaDoLimite() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID grande = mensagemDeMidia(conversa, bruno, "IMAGEM", "image/png", "enorme.png", 900L * 1024 * 1024, null, "midia/enorme.png");
        UUID semTamanho = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, midia_url, midia_metadados) VALUES (?, ?, ?, 'IMAGEM'::tipo_mensagem, 'midia/s.png', '{\"nome_original\":\"s.png\",\"mimetype\":\"image/png\"}'::jsonb)",
                semTamanho, conversa, bruno);

        ResponseEntity<String> acima = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, grande), "k-grande");
        assertThat(acima.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(acima.getBody()).path("motivo").asText()).isEqualTo("ARQUIVO_ACIMA_DO_LIMITE");
        ResponseEntity<String> sem = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, semTamanho), "k-sem-tamanho");
        assertThat(sem.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(sem.getBody()).path("motivo").asText()).isEqualTo("ARQUIVO_SEM_TAMANHO");
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    @Test
    @DisplayName("Idempotency-Key e obrigatoria: sem ela, 400 e nada e gravado")
    void semChave() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "sem chave");

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(nadaFoiEnviado(destino)).isTrue();
    }

    // ---------------------------------------------------------------- idempotência, status e provedor

    @Test
    @DisplayName("idempotencia: o mesmo clique duas vezes devolve o mesmo encaminhamento, com uma mensagem, um elo, uma outbox e uma auditoria")
    void idempotencia() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "uma vez so");

        JsonNode primeira = json.readTree(
                encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, mensagem), "k-idem").getBody());
        ResponseEntity<String> segundaResposta =
                encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, mensagem), "k-idem");
        JsonNode segunda = json.readTree(segundaResposta.getBody());

        assertThat(segundaResposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(segunda.path("reutilizado").asBoolean()).isTrue();
        assertThat(segunda.path("id").asText()).isEqualTo(primeira.path("id").asText());
        assertThat(segunda.path("mensagemExternaId").asText()).isEqualTo(primeira.path("mensagemExternaId").asText());
        assertThat(segunda.path("conviteCriado").asBoolean()).isEqualTo(primeira.path("conviteCriado").asBoolean());
        assertThat(contarMensagens(destino)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_interno_encaminhamento_cliente WHERE lead_id = ?",
                        Integer.class, destino.leadId())).isEqualTo(1);
        assertThat(convitesPendentes(destino)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = 'canal.mensagem.enviar' AND payload->>'atendimentoId' = ?",
                        Integer.class, destino.atendimentoId().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE lead_id = ? AND acao = ?",
                        Integer.class, destino.leadId(), ACAO_AUDITADA)).isEqualTo(1);
    }

    @Test
    @DisplayName("status: a lista acompanha PENDENTE -> ENVIADO sem recarregar, e so mostra o que o proprio usuario encaminhou")
    void statusAcompanhaAEntrega() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "acompanhar");
        encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-status");

        assertThat(statusNaLista(EMAIL_ANA, conversa, mensagem)).isEqualTo("PENDENTE");
        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(statusNaLista(EMAIL_ANA, conversa, mensagem)).isIn("ENVIADO", "ENTREGUE", "LIDO"));

        ResponseEntity<String> doOutro = chamar(EMAIL_BRUNO, SENHA_ATENDENTE, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + conversa + "/mensagens/" + mensagem + "/encaminhamentos-ao-cliente", null, null);
        assertThat(doOutro.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(doOutro.getBody())).isEmpty();
    }

    @Test
    @DisplayName("falha permanente do provedor: a mensagem externa vira FALHOU e a lista mostra, sem derrubar o Chat Interno")
    void falhaDoProvedor() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "vai falhar");
        canal.recusarDeVez("numero invalido");

        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-falha");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(statusNaLista(EMAIL_ANA, conversa, mensagem)).isEqualTo("FALHOU"));
        // O Chat Interno segue de pe: a mesma conversa recebe mensagem nova.
        ResponseEntity<String> chat = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + conversa + "/mensagens", Map.of("conteudo", "ainda funciona"), null);
        assertThat(chat.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("provedor fora do ar: o HTTP responde 202 na hora, a mensagem fica PENDENTE e o retry controlado envia uma vez so")
    void retryControlado() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_ANA, bruno);
        UUID mensagem = mensagemDeTexto(conversa, bruno, "com retry");
        canal.derrubar("timeout");

        long inicio = System.nanoTime();
        ResponseEntity<String> resposta = encaminhar(EMAIL_ANA, destino.atendimentoId(), corpo(conversa, mensagem), "k-retry");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).as("o envio nao espera o provedor").isLessThan(Duration.ofSeconds(5));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID externa = UUID.fromString(json.readTree(resposta.getBody()).path("mensagemExternaId").asText());

        publicador.publicarPendentes();
        assertThat(statusExterno(externa)).isEqualTo("PENDENTE");
        assertThat(canal.enviados()).isEmpty();
        // A aba Atendimentos segue respondendo enquanto o provedor esta fora.
        assertThat(chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.GET, "/api/v1/atendimentos?visao=ATIVOS", null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        canal.religar();
        jdbc.update("UPDATE outbox_evento SET proxima_tentativa_em = now() WHERE tipo = 'canal.mensagem.enviar' AND payload->>'atendimentoId' = ?",
                destino.atendimentoId().toString());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            publicador.publicarPendentes();
            assertThat(statusExterno(externa)).isIn("ENVIADO", "ENTREGUE", "LIDO");
        });
        assertThat(canal.enviados().stream().filter(envio -> externa.equals(envio.mensagemId())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("auditoria: usuario, mensagem interna, atendimento e mensagem externa ficam rastreaveis; tentativa recusada nao deixa registro")
    void auditoria() throws Exception {
        Destino destino = destino(ana, true);
        UUID conversa = conversaDireta(EMAIL_GESTOR, ana);
        UUID mensagem = mensagemDeTexto(conversa, ana, "auditar");

        UUID externa = UUID.fromString(json.readTree(
                        encaminhar(EMAIL_GESTOR, destino.atendimentoId(), corpo(conversa, mensagem), "k-auditoria").getBody())
                .path("mensagemExternaId").asText());

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT ator_id, ator_tipo::text AS ator_tipo, entidade_tipo, lead_id, dados_depois::text AS depois FROM audit_log WHERE lead_id = ? AND acao = ?",
                destino.leadId(), ACAO_AUDITADA);
        assertThat(linha.get("ator_id")).isEqualTo(gestor);
        assertThat(linha.get("ator_tipo")).isEqualTo("USUARIO");
        assertThat(linha.get("entidade_tipo")).isEqualTo("CHAT_INTERNO_ENCAMINHAMENTO_CLIENTE");
        JsonNode depois = json.readTree((String) linha.get("depois"));
        assertThat(depois.path("usuarioId").asText()).isEqualTo(gestor.toString());
        assertThat(depois.path("mensagemInternaId").asText()).isEqualTo(mensagem.toString());
        assertThat(depois.path("conversaId").asText()).isEqualTo(conversa.toString());
        assertThat(depois.path("atendimentoId").asText()).isEqualTo(destino.atendimentoId().toString());
        assertThat(depois.path("mensagemExternaId").asText()).isEqualTo(externa.toString());
        assertThat(depois.path("conviteCriado").asBoolean()).isTrue();
        assertThat(depois.toString()).as("sem texto nem telefone na auditoria").doesNotContain("auditar", destino.telefone());

        Destino alheio = destino(bruno, true);
        encaminhar(EMAIL_ANA, alheio.atendimentoId(), corpo(conversa, mensagem), "k-recusada");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE lead_id = ? AND acao = ?",
                        Integer.class, alheio.leadId(), ACAO_AUDITADA)).isZero();
    }

    // ---------------------------------------------------------------- destinos (busca no servidor)

    @Test
    @DisplayName("destinos: atendente ve os proprios e os potenciais; nao ve os do colega nem os finalizados; telefone mascarado")
    void destinosDoAtendente() throws Exception {
        Destino meu = destino(ana, true, "Alfa Meu");
        Destino potencial = destino(null, true, "Alfa Potencial");
        Destino doColega = destino(bruno, true, "Alfa Colega");
        Destino finalizado = destino(ana, false, "Alfa Finalizado");

        ResponseEntity<String> resposta = destinos(EMAIL_ANA, "alfa");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> ids = idsDosDestinos(resposta);
        assertThat(ids).contains(meu.atendimentoId().toString(), potencial.atendimentoId().toString())
                .doesNotContain(doColega.atendimentoId().toString(), finalizado.atendimentoId().toString());
        for (Destino visto : List.of(meu, potencial)) {
            assertThat(resposta.getBody()).doesNotContain(visto.telefone());
        }
        JsonNode item = json.readTree(resposta.getBody()).get(0);
        assertThat(item.path("telefoneMascarado").asText()).contains("*");
        assertThat(item.path("statusAtendimento").asText()).isIn("EM_ATENDIMENTO", "EM_IA");
    }

    @Test
    @DisplayName("destinos: gestao ve os atendimentos de todos")
    void destinosDaGestao() throws Exception {
        Destino daAna = destino(ana, true, "Beta Ana");
        Destino doBruno = destino(bruno, true, "Beta Bruno");

        List<String> ids = idsDosDestinos(destinos(EMAIL_GESTOR, "beta"));

        assertThat(ids).contains(daAna.atendimentoId().toString(), doBruno.atendimentoId().toString());
    }

    @Test
    @DisplayName("destinos: busca por parte do nome sem diferenciar caixa e por digitos do telefone")
    void destinosBuscaPorNomeETelefone() throws Exception {
        Destino destino = destino(ana, true, "Gamma Distintivo");
        // Formatação do usuário ("(55) 6112345...") com os mesmos dígitos, em sequência, do telefone guardado.
        String formatado = "(" + destino.telefone().substring(0, 2) + ") " + destino.telefone().substring(2, 10);

        assertThat(idsDosDestinos(destinos(EMAIL_ANA, "gAmMa disTIN"))).contains(destino.atendimentoId().toString());
        assertThat(idsDosDestinos(destinos(EMAIL_ANA, formatado))).contains(destino.atendimentoId().toString());
        assertThat(idsDosDestinos(destinos(EMAIL_ANA, "nada-corresponde-a-isto"))).isEmpty();
    }

    @Test
    @DisplayName("destinos: curingas digitados pelo usuario valem como texto, nao como padrao")
    void destinosCuringaVaiComoTexto() throws Exception {
        Destino destino = destino(ana, true, "Delta Curinga");

        assertThat(idsDosDestinos(destinos(EMAIL_ANA, "%"))).doesNotContain(destino.atendimentoId().toString());
        assertThat(idsDosDestinos(destinos(EMAIL_ANA, "Delta_Curinga"))).doesNotContain(destino.atendimentoId().toString());
        assertThat(idsDosDestinos(destinos(EMAIL_ANA, "\\"))).isEmpty();
        assertThat(idsDosDestinos(destinos(EMAIL_ANA, "Delta Curinga"))).contains(destino.atendimentoId().toString());
    }

    @Test
    @DisplayName("destinos: no maximo 20 resultados por busca")
    void destinosLimitados() throws Exception {
        for (int i = 0; i < 25; i++) {
            destino(ana, true, "Epsilon");
        }

        assertThat(json.readTree(destinos(EMAIL_ANA, "epsilon").getBody())).hasSize(20);
    }

    @Test
    @DisplayName("destinos: sem token 401; sem atendimentos.responder 403")
    void destinosExigemAutenticacaoEPermissao() throws Exception {
        assertThat(http.getForEntity("/api/v1/atendimentos/encaminhamento-do-chat-interno/destinos", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        long revisao = jdbc.query("SELECT revisao FROM permissao_perfil WHERE papel = CAST('ATENDENTE' AS papel_usuario)",
                (rs, i) -> rs.getLong(1)).stream().findFirst().orElse(0L);
        try {
            chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE",
                    Map.of("revisaoEsperada", revisao, "niveis", Map.of(), "acoes", Map.of("atendimentos.responder", false)), null);

            assertThat(destinos(EMAIL_BRUNO, "").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            long atual = jdbc.query("SELECT revisao FROM permissao_perfil WHERE papel = CAST('ATENDENTE' AS papel_usuario)",
                    (rs, i) -> rs.getLong(1)).stream().findFirst().orElse(0L);
            chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE",
                    Map.of("revisaoEsperada", atual, "niveis", Map.of(), "acoes", Map.of("atendimentos.responder", true)), null);
        }
    }

    // ---------------------------------------------------------------- apoio

    private record Destino(UUID leadId, UUID atendimentoId, String telefone) {}

    /** Lead e atendimento; {@code dono} nulo = potencial com a IA; {@code aberto=false} = atendimento finalizado. */
    private Destino destino(UUID dono, boolean aberto) {
        return destino(dono, aberto, "cliente");
    }

    private Destino destino(UUID dono, boolean aberto, String apelido) {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        String telefone = "5561" + String.format("%09d", Math.floorMod(leadId.getLeastSignificantBits(), 1_000_000_000L));
        String statusLead = !aberto ? "FINALIZADO" : dono == null ? "IA" : "EM_ATENDIMENTO";
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico, ultima_interacao_em,"
                        + " ultima_mensagem_do_lead_em) VALUES (?, ?, ?, ?, CAST(? AS status_basico_lead), ?, ?)",
                leadId, PREFIXO + apelido + " " + leadId, telefone, dono, statusLead,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        String statusAtendimento = !aberto ? "FINALIZADO" : dono == null ? "EM_IA" : "EM_ATENDIMENTO";
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em, finalizado_em)"
                        + " VALUES (?, ?, ?, CAST(? AS status_atendimento), now(), CASE WHEN ? THEN NULL ELSE now() END)",
                atendimentoId, leadId, dono, statusAtendimento, aberto);
        return new Destino(leadId, atendimentoId, telefone);
    }

    private UUID conversaDireta(String emailCriador, UUID outro) {
        ResponseEntity<String> resposta = chamar(emailCriador, senhaDe(emailCriador), HttpMethod.POST,
                "/api/v1/chat-interno/conversas/direta", Map.of("usuarioId", outro.toString()), null);
        assertThat(resposta.getStatusCode().is2xxSuccessful()).as(resposta.getBody()).isTrue();
        try {
            UUID id = UUID.fromString(json.readTree(resposta.getBody()).path("id").asText());
            conversas.add(id);
            return id;
        } catch (Exception e) {
            throw new AssertionError(resposta.getBody(), e);
        }
    }

    private UUID mensagemDeTexto(UUID conversa, UUID remetente, String texto) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, conteudo) VALUES (?, ?, ?, 'TEXTO'::tipo_mensagem, ?)",
                id, conversa, remetente, texto);
        return id;
    }

    private UUID mensagemDeSistema(UUID conversa, UUID remetente) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, conteudo) VALUES (?, ?, ?, 'SISTEMA'::tipo_mensagem, ?)",
                id, conversa, remetente, "{\"evento\":\"GRUPO_CRIADO\"}");
        return id;
    }

    private UUID mensagemDeMidia(UUID conversa, UUID remetente, String tipo, String mime, String nome, long tamanho,
            String legenda, String referencia) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put("nome_original", nome);
        metadados.put("tamanho_bytes", tamanho);
        metadados.put("mimetype", mime);
        if (legenda != null) {
            metadados.put("legenda", legenda);
        }
        jdbc.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, conteudo, midia_url, midia_metadados)"
                        + " VALUES (?, ?, ?, CAST(? AS tipo_mensagem), ?, ?, CAST(? AS jsonb))",
                id, conversa, remetente, tipo, legenda, referencia, json.writeValueAsString(metadados));
        return id;
    }

    private static Map<String, Object> corpo(UUID conversa, UUID mensagem) {
        return Map.of("conversaId", conversa.toString(), "mensagemId", mensagem.toString());
    }

    private ResponseEntity<String> encaminhar(String email, UUID atendimento, Map<String, Object> corpo, String chave) {
        HttpHeaders extras = new HttpHeaders();
        if (chave != null) {
            extras.set("Idempotency-Key", chave);
        }
        return chamar(email, senhaDe(email), HttpMethod.POST, ENCAMINHAR.formatted(atendimento), corpo, extras);
    }

    private ResponseEntity<String> destinos(String email, String busca) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token(email, senhaDe(email)));
        // Variavel de URI: o RestTemplate codifica uma vez so (codificar antes dobraria o %).
        return http.exchange("/api/v1/atendimentos/encaminhamento-do-chat-interno/destinos?busca={busca}",
                HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class, busca);
    }

    private List<String> idsDosDestinos(ResponseEntity<String> resposta) throws Exception {
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        List<String> ids = new ArrayList<>();
        json.readTree(resposta.getBody()).forEach(item -> ids.add(item.path("atendimentoId").asText()));
        return ids;
    }

    private ResponseEntity<String> previa(String email, UUID atendimento, UUID conversa, UUID mensagem) {
        return chamar(email, senhaDe(email), HttpMethod.GET,
                ENCAMINHAR.formatted(atendimento) + "/previa?conversaId=" + conversa + "&mensagemId=" + mensagem, null, null);
    }

    private String statusNaLista(String email, UUID conversa, UUID mensagem) throws Exception {
        ResponseEntity<String> resposta = chamar(email, senhaDe(email), HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + conversa + "/mensagens/" + mensagem + "/encaminhamentos-ao-cliente", null, null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode lista = json.readTree(resposta.getBody());
        assertThat(lista).hasSize(1);
        return lista.get(0).path("statusEntrega").asText();
    }

    private CanalGateway.Envio envioDe(UUID mensagemExterna) {
        return canal.enviados().stream()
                .filter(envio -> mensagemExterna.equals(envio.mensagemId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("a mensagem externa nao chegou ao canal"));
    }

    private String statusExterno(UUID mensagemExterna) {
        return jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id = ?", String.class, mensagemExterna);
    }

    private UUID responsavelDoLead(Destino destino) {
        return jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, destino.leadId());
    }

    private UUID responsavelDoAtendimento(Destino destino) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, destino.atendimentoId());
    }

    private int convitesPendentes(Destino destino) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM pedido_entrada_atendimento WHERE atendimento_id = ? AND tipo = 'CONVITE' AND status = 'PENDENTE'",
                Integer.class, destino.atendimentoId());
    }

    private int contarMensagens(Destino destino) {
        return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, destino.atendimentoId());
    }

    private int contarAtendimentosDoLead(Destino destino) {
        return jdbc.queryForObject("SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, destino.leadId());
    }

    private List<String> acoesAuditadas(Destino destino) {
        return jdbc.queryForList("SELECT acao FROM audit_log WHERE lead_id = ?", String.class, destino.leadId());
    }

    /** Nenhuma mensagem, nenhum evento na outbox, nenhum elo e nenhum convite: o clique nao deixou rastro de envio. */
    private boolean nadaFoiEnviado(Destino destino) {
        return contarMensagens(destino) == 0
                && jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = 'canal.mensagem.enviar' AND payload->>'atendimentoId' = ?",
                        Integer.class, destino.atendimentoId().toString()) == 0
                && jdbc.queryForObject("SELECT count(*) FROM chat_interno_encaminhamento_cliente WHERE atendimento_id = ?",
                        Integer.class, destino.atendimentoId()) == 0
                && convitesPendentes(destino) == 0;
    }

    private String token(String email, String senha) {
        return tokens.computeIfAbsent(email, e -> ApoioAutenticacao.login(http, e, senha).accessToken());
    }

    private static String senhaDe(String email) {
        return EMAIL_GESTOR.equals(email) ? SENHA_GESTOR : SENHA_ATENDENTE;
    }

    private ResponseEntity<String> chamar(String email, String senha, HttpMethod metodo, String url, Object corpo, HttpHeaders extras) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (extras != null) {
            cabecalhos.putAll(extras);
        }
        cabecalhos.setBearerAuth(token(email, senha));
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }
}
