package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.evento.ReacaoDaMensagemParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

/**
 * Publica no backplane Redis — nunca antes do commit.
 *
 * <p>{@code AFTER_COMMIT}, como os demais reatores do modulo (timeline, auditoria): se a transacao que
 * gravou a mensagem reverter, nenhum evento sai. E o publisher roda no que a E06 pede — "contexto de
 * servico", nao no do usuario — porque a autorizacao de quem recebe acontece na ENTREGA, dentro de
 * {@link RedisSubscriberDeAtendimento}, nunca aqui. Publicar e barato e nao filtra ninguem; a
 * seguranca esta inteira do outro lado do canal.
 *
 * <p>Nao escreve no banco, entao nao precisa de {@code @Transactional(REQUIRES_NEW)} como os
 * listeners que gravam timeline/auditoria — so fala com o Redis.
 */
@Component
class RelayDeTempoRealListener {

    private static final Logger log = LoggerFactory.getLogger(RelayDeTempoRealListener.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ArmazenamentoDeMidia armazenamento;
    private final MidiaProperties midiaPropriedades;

    RelayDeTempoRealListener(
            StringRedisTemplate redis,
            ObjectMapper json,
            ArmazenamentoDeMidia armazenamento,
            MidiaProperties midiaPropriedades) {
        this.redis = redis;
        this.json = json;
        this.armazenamento = armazenamento;
        this.midiaPropriedades = midiaPropriedades;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoReceberMensagem(MensagemParaTempoReal evento) {
        ObjectNode dados = json.createObjectNode();
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("leadId", evento.leadId().toString());
        dados.put("mensagemId", evento.mensagemId().toString());
        dados.put("remetenteTipo", evento.remetenteTipo());
        dados.put("remetenteId", evento.remetenteId() == null ? null : evento.remetenteId().toString());
        dados.put("tipo", evento.tipo());
        dados.put("conteudo", evento.conteudo());
        // Referencia opaca -> URL assinada aqui, no ponto de saida: quem recebe este evento
        // ja passou pela autorizacao de assinatura do canal do atendimento (E06/E07).
        dados.put(
                "midiaUrl",
                evento.midiaUrl() == null
                        ? null
                        : armazenamento.urlAssinada(
                                evento.midiaUrl(), midiaPropriedades.expiracaoLeitura()));
        dados.put("midiaMetadados", evento.midiaMetadados());
        dados.put("opcoes", evento.opcoes());
        dados.put("statusEntrega", evento.statusEntrega());
        dados.put("enviadoEm", evento.enviadoEm().toString());
        publicar(evento.atendimentoId(), "MENSAGEM", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoMudarStatus(MudancaDeStatusDeEntrega evento) {
        ObjectNode dados = json.createObjectNode();
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("leadId", evento.leadId().toString());
        dados.put("mensagemId", evento.mensagemId().toString());
        dados.put("statusEntrega", evento.statusEntrega());
        dados.put("ocorridoEm", evento.ocorridoEm().toString());
        publicar(evento.atendimentoId(), "STATUS", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoTransferir(EventoDeAtendimento.AtendimentoTransferido evento) {
        ObjectNode dados = json.createObjectNode();
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("leadId", evento.leadId().toString());
        dados.put("leadNome", evento.leadNome());
        dados.put(
                "deAtendenteId", evento.deAtendenteId() == null ? null : evento.deAtendenteId().toString());
        dados.put(
                "paraAtendenteId",
                evento.paraAtendenteId() == null ? null : evento.paraAtendenteId().toString());
        dados.put("quemTransferiu", evento.atorId() == null ? null : evento.atorId().toString());
        dados.put("atorTipo", evento.atorTipo().name());
        dados.put("ocorridoEm", evento.ocorridoEm().toString());
        // Revogacao (E06 secao 1): esta e a linha que faz o dono anterior parar de
        // receber. O assinante consulta o registro LOCAL de cada instancia; sem
        // este relay, so quem esta na mesma instancia que processou a transferencia
        // seria revogado.
        publicar(evento.atendimentoId(), "TRANSFERENCIA", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoEnviarComTransferencia(EventoDeAtendimento.MensagemEnviada evento) {
        if (!evento.transferiu() || evento.donoAnterior().isEmpty()) return;
        ObjectNode dados = json.createObjectNode();
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("leadId", evento.leadId().toString());
        dados.put("leadNome", evento.leadNome());
        dados.put("deAtendenteId", evento.donoAnterior().get().toString());
        dados.put("paraAtendenteId", evento.remetenteId().toString());
        dados.put("quemTransferiu", evento.remetenteId().toString());
        dados.put("atorTipo", "USUARIO");
        dados.put("ocorridoEm", evento.ocorridoEm().toString());
        publicar(evento.atendimentoId(), "TRANSFERENCIA", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoFinalizar(EventoDeAtendimento.AtendimentoFinalizado evento) {
        ObjectNode dados = json.createObjectNode();
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("leadId", evento.leadId().toString());
        dados.put("quemFinalizou", evento.quemFinalizou().toString());
        dados.put("ocorridoEm", evento.ocorridoEm().toString());
        publicar(evento.atendimentoId(), "FINALIZACAO", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoPedirEntrada(EventoDeAtendimento.PedidoEntradaSolicitado evento) {
        ObjectNode dados=json.createObjectNode(); dados.put("atendimentoId",evento.atendimentoId().toString());
        dados.put("leadId",evento.leadId().toString()); dados.put("solicitanteId",evento.solicitanteId().toString());
        dados.put("solicitanteNome",evento.solicitanteNome()); dados.put("donoId",evento.donoId().toString()); dados.put("ocorridoEm",evento.ocorridoEm().toString());
        publicar(evento.atendimentoId(), "PEDIDO_ENTRADA", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoResponderPedido(EventoDeAtendimento.PedidoEntradaRespondido evento) {
        ObjectNode dados=json.createObjectNode(); dados.put("atendimentoId",evento.atendimentoId().toString());
        dados.put("leadId",evento.leadId().toString()); dados.put("solicitanteId",evento.solicitanteId().toString());
        dados.put("donoId",evento.donoId().toString()); dados.put("aprovado",evento.aprovado()); dados.put("ocorridoEm",evento.ocorridoEm().toString());
        publicar(evento.atendimentoId(), "RESPOSTA_PEDIDO_ENTRADA", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoSair(EventoDeAtendimento.ParticipanteSaiu evento) {
        ObjectNode dados=json.createObjectNode(); dados.put("atendimentoId",evento.atendimentoId().toString());
        dados.put("participanteId",evento.participanteId().toString()); dados.put("ocorridoEm",evento.ocorridoEm().toString());
        publicar(evento.atendimentoId(), "PARTICIPANTE_SAIU", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoReagir(ReacaoDaMensagemParaTempoReal evento) {
        ObjectNode dados = json.createObjectNode();
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("mensagemId", evento.mensagemId().toString());
        dados.put("enviadoEm", evento.enviadoEm().toString());
        dados.put("atorId", evento.atorId().toString());
        if (evento.emojiDoAtor() == null) {
            dados.putNull("emojiDoAtor");
        } else {
            dados.put("emojiDoAtor", evento.emojiDoAtor());
        }
        dados.set("reacoes", resumoPublico(evento.reacoes()));
        publicar(evento.atendimentoId(), "REACAO", dados);
    }

    private ArrayNode resumoPublico(List<ResumoDeReacao> reacoes) {
        var lista = json.createArrayNode();
        for (ResumoDeReacao resumo : reacoes) {
            ObjectNode item = json.createObjectNode();
            item.put("emoji", resumo.emoji());
            item.put("quantidade", resumo.quantidade());
            lista.add(item);
        }
        return lista;
    }

    private void publicar(UUID atendimentoId, String tipo, ObjectNode dados) {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("tipo", tipo);
        envelope.set("dados", dados);
        try {
            redis.convertAndSend(CanaisRedis.doAtendimento(atendimentoId), envelope.toString());
        } catch (RuntimeException e) {
            // Redis fora do ar nao pode derrubar o listener AFTER_COMMIT nem, pior,
            // propagar e fazer parecer que a mensagem falhou — ela ja esta gravada e
            // confirmada. So a entrega em tempo real desta rodada se perde; a
            // reconciliacao por HTTP ao reconectar (secao 4) cobre o resto.
            log.warn("Falha ao publicar evento de tempo real no Redis (tipo={}).", tipo, e);
        }
    }
}
