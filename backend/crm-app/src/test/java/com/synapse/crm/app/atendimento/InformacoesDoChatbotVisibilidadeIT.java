package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Os cards seguem EXATAMENTE as permissoes que ja existem para o historico do atendimento — nao
 * ampliam o acesso a atendimentos de colegas.
 *
 * <p>A referencia e a leitura de mensagens ({@code GET /mensagens}), protegida pela RLS de
 * {@code atendimento}. Para cada cenario de acesso e cada usuario, os dois endpoints precisam dar o
 * MESMO status; e o texto do card so aparece quando esse status e 200. Os cenarios cobrem convite
 * pendente, convite vencido, pedido de entrada, participante ativo, participante que saiu e usuario
 * sem nenhuma relacao com o atendimento.
 *
 * <p>Este teste NAO decide o que cada papel deveria ver: ele prova que o card nao ve mais nem menos
 * que a mensagem. O que a regra existente concede (ver docs/70) e decisao de produto, nao deste PR.
 */
class InformacoesDoChatbotVisibilidadeIT extends InformacoesDoChatbotITBase {

    private static final String SEGREDO = "SEGREDO-DO-CARD-";

    @Test
    @DisplayName("cartoes e mensagens dao o mesmo status para cada usuario em cada cenario de acesso")
    void cardsSeguemAsPermissoesDasMensagens() {
        UUID bruno = idDoUsuario(EMAIL_BRUNO);
        Map<String, String> bearers = new LinkedHashMap<>();
        bearers.put("ana (dona)", loginAna());
        bearers.put("bruno (atendente)", bearerDe(EMAIL_BRUNO, SENHA_ATENDENTE));
        bearers.put("gestor", bearerDe(EMAIL_GESTOR, SENHA_GESTOR));
        bearers.put("subgestor", bearerDe(EMAIL_SUBGESTOR, SENHA_SUBGESTOR));

        Map<String, UUID> cenarios = new LinkedHashMap<>();
        cenarios.put("sem-relacao", comCard("sem-relacao", "EM_ATENDIMENTO"));
        UUID comParticipante = comCard("participante-ativo", "EM_ATENDIMENTO");
        jdbc.update("INSERT INTO atendimento_participante(atendimento_id, usuario_id) VALUES (?, ?)", comParticipante, bruno);
        cenarios.put("participante-ativo", comParticipante);
        UUID participanteQueSaiu = comCard("participante-saiu", "EM_ATENDIMENTO");
        jdbc.update(
                "INSERT INTO atendimento_participante(atendimento_id, usuario_id, saiu_em) VALUES (?, ?, now())",
                participanteQueSaiu, bruno);
        cenarios.put("participante-saiu", participanteQueSaiu);
        UUID convitePendente = comCard("convite-pendente", "EM_ATENDIMENTO");
        jdbc.update(
                "INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id, status, tipo)"
                        + " VALUES (?, ?, 'PENDENTE', 'CONVITE')",
                convitePendente, bruno);
        cenarios.put("convite-pendente", convitePendente);
        UUID conviteVencido = comCard("convite-vencido", "EM_ATENDIMENTO");
        jdbc.update(
                "INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id, status, tipo, solicitado_em)"
                        + " VALUES (?, ?, 'PENDENTE', 'CONVITE', now() - interval '90 days')",
                conviteVencido, bruno);
        cenarios.put("convite-vencido", conviteVencido);
        UUID pedidoDeEntrada = comCard("pedido-de-entrada", "EM_ATENDIMENTO");
        jdbc.update(
                "INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id, status, tipo)"
                        + " VALUES (?, ?, 'PENDENTE', 'SOLICITACAO')",
                pedidoDeEntrada, bruno);
        cenarios.put("pedido-de-entrada", pedidoDeEntrada);
        cenarios.put("em-ia", comCard("em-ia", "EM_IA"));
        cenarios.put("finalizado", comCard("finalizado", "FINALIZADO"));

        StringBuilder tabela = new StringBuilder();
        for (var cenario : cenarios.entrySet()) {
            for (var leitor : bearers.entrySet()) {
                ResponseEntity<String> doCard = lerCards(cenario.getValue(), leitor.getValue());
                ResponseEntity<String> daMensagem = lerMensagens(cenario.getValue(), leitor.getValue());
                String contexto = cenario.getKey() + " / " + leitor.getKey();

                assertThat(doCard.getStatusCode())
                        .as("paridade com a leitura de mensagens em: %s", contexto)
                        .isEqualTo(daMensagem.getStatusCode());
                assertThat(doCard.getStatusCode()).as(contexto).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
                boolean viu = doCard.getBody() != null && doCard.getBody().contains(SEGREDO + cenario.getKey());
                assertThat(viu)
                        .as("o texto do card so aparece quando o acesso e liberado: %s", contexto)
                        .isEqualTo(doCard.getStatusCode() == HttpStatus.OK);
                tabela.append(contexto).append(" -> ").append(doCard.getStatusCode().value()).append('\n');
            }
        }

        // Regras que o historico ja tem, fixadas para o card nao divergir silenciosamente delas.
        assertThat(status(cenarios.get("sem-relacao"), bearers.get("bruno (atendente)")))
                .as("atendente sem relacao com atendimento em andamento de colega: sem acesso\n%s", tabela)
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(cenarios.get("participante-ativo"), bearers.get("bruno (atendente)"))).isEqualTo(HttpStatus.OK);
        assertThat(status(cenarios.get("participante-saiu"), bearers.get("bruno (atendente)"))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(cenarios.get("convite-pendente"), bearers.get("bruno (atendente)"))).isEqualTo(HttpStatus.OK);
        assertThat(status(cenarios.get("convite-vencido"), bearers.get("bruno (atendente)"))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(cenarios.get("pedido-de-entrada"), bearers.get("bruno (atendente)"))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("colega sem relacao nao le o card nem tendo o id do atendimento")
    void leituraDoCardNaoAlcancaAlemDoAtendimento() {
        UUID semRelacao = comCard("sql-sem-relacao", "EM_ATENDIMENTO");
        assertThat(cards(semRelacao)).isEqualTo(1);

        ResponseEntity<String> doColega = lerCards(semRelacao, bearerDe(EMAIL_BRUNO, SENHA_ATENDENTE));

        assertThat(doColega.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(doColega.getBody()).doesNotContain(SEGREDO);
    }

    private HttpStatus status(UUID atendimentoId, String bearer) {
        return HttpStatus.valueOf(lerCards(atendimentoId, bearer).getStatusCode().value());
    }

    /** Atendimento da Ana no estado pedido, ja com um card cujo texto carrega o nome do cenario. */
    private UUID comCard(String cenario, String statusAtendimento) {
        UUID dono = "EM_IA".equals(statusAtendimento) ? null : ana;
        String statusDoLead = switch (statusAtendimento) {
            case "EM_IA" -> "IA";
            case "FINALIZADO" -> "FINALIZADO";
            default -> "EM_ATENDIMENTO";
        };
        UUID leadId = criarLead(cenario, dono, statusDoLead);
        UUID atendimentoId = criarAtendimento(leadId, dono, statusAtendimento);
        inserirCardPorSql(atendimentoId, "chave-visibilidade-" + atendimentoId, SEGREDO + cenario);
        return atendimentoId;
    }
}
