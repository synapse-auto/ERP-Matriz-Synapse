package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Onde a autorizacao <b>de verdade</b> acontece para quem ja esta assinado: na entrega, nao na
 * publicacao.
 *
 * <p>{@link RelayDeTempoRealListener} publica sem filtrar ninguem — publicar no Redis e barato e nao
 * sabe quem esta conectado a qual instancia. Esta classe e quem decide, mensagem por mensagem, para
 * quais SESSOES desta instancia especifica o conteudo realmente vai, consultando
 * {@link RegistroDeAssinaturas} — nunca um {@code /topic} de broadcast nativo do STOMP.
 *
 * <p>Cada instancia so entrega para sessoes locais: se nenhum atendente conectado a esta instancia
 * assinou o atendimento X, a mensagem chega, e simplesmente nao ha ninguem para entregar. Isso e o que
 * torna o mecanismo correto sem <i>sticky sessions</i> — a sessao pode estar em qualquer instancia, e
 * so aquela instancia entrega.
 */
@Component
class RedisSubscriberDeAtendimento implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisSubscriberDeAtendimento.class);
    // Nao comeca com "/queue/atendimento." de proposito: esse e o mesmo prefixo
    // que AutorizacaoDeAssinaturaInterceptor usa para reconhecer topico de
    // atendimento e tentar ler um UUID depois do ponto. Um destino que colidisse
    // com o prefixo faria a PROPRIA assinatura de revogacao ser cancelada como
    // "destino malformado" — o aviso nunca chegaria a quem mais precisa dele.
    private static final String DESTINO_REVOGACAO = "/queue/revogacoes";

    private final RegistroDeAssinaturas registro;
    private final SimpMessagingTemplate template;
    private final ObjectMapper json;

    RedisSubscriberDeAtendimento(
            RegistroDeAssinaturas registro, SimpMessagingTemplate template, ObjectMapper json) {
        this.registro = registro;
        this.template = template;
        this.json = json;
    }

    @Override
    public void onMessage(Message mensagem, byte[] padrao) {
        String canal = new String(mensagem.getChannel(), StandardCharsets.UTF_8);
        UUID atendimentoId = CanaisRedis.atendimentoDoCanal(canal);
        if (atendimentoId == null) {
            return;
        }

        String corpo = new String(mensagem.getBody(), StandardCharsets.UTF_8);
        JsonNode envelope;
        try {
            envelope = json.readTree(corpo);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Envelope de tempo real ilegivel no canal {}.", canal, e);
            return;
        }

        String tipo = envelope.path("tipo").asText();
        JsonNode dados = envelope.path("dados");

        if ("TRANSFERENCIA".equals(tipo)) {
            revogarQuemPerdeuAcesso(atendimentoId, dados);
        }

        // Um usuario pode ter mais de uma sessao (duas abas) autorizadas ao mesmo
        // atendimento; convertAndSendToUser ja entrega a TODAS as sessoes daquele
        // usuario de uma vez, entao um usuario so pode receber uma vez por rodada.
        Set<UUID> usuariosEntregues = new java.util.HashSet<>();
        for (AssinaturaAutorizada assinatura : registro.doAtendimento(atendimentoId)) {
            if (usuariosEntregues.add(assinatura.usuarioId())) {
                enviarParaUsuario(assinatura.usuarioId(), "/queue/atendimento." + atendimentoId, corpo);
            }
        }
    }

    /**
     * A revogacao (E06 secao 1). Reusa os dois mesmos ingredientes de {@code Atendimento.visivelPara}:
     * {@link PapelUsuario#enxergaTodosOsLeads()} — a fonte unica do que "papel amplo" significa — e o
     * novo dono que a transferencia acabou de definir. Nao reabre transacao em nome do dono anterior:
     * ele nao tem requisicao em andamento para carregar aquele contexto.
     */
    private void revogarQuemPerdeuAcesso(UUID atendimentoId, JsonNode dados) {
        JsonNode paraNo = dados.path("paraAtendenteId");
        UUID paraAtendenteId =
                paraNo.isNull() || paraNo.isMissingNode() ? null : UUID.fromString(paraNo.asText());

        for (AssinaturaAutorizada assinatura : Set.copyOf(registro.doAtendimento(atendimentoId))) {
            boolean continuaAutorizado = assinatura.papel().enxergaTodosOsLeads()
                    // devolveu para a IA: sem dono, "Potenciais" continua visivel a qualquer atendente
                    || paraAtendenteId == null
                    || paraAtendenteId.equals(assinatura.usuarioId());

            if (!continuaAutorizado) {
                registro.remover(assinatura);
                enviarParaUsuario(assinatura.usuarioId(), DESTINO_REVOGACAO,
                        "{\"atendimentoId\":\"" + atendimentoId + "\"}");
                log.debug(
                        "Assinatura de {} revogada para o usuario {} (atendimento transferido).",
                        atendimentoId,
                        assinatura.usuarioId());
            }
        }
    }

    /**
     * Endereca pelo <b>nome</b> da autenticacao — que, para {@code JwtAuthenticationToken}, e a claim
     * {@code sub} do token, ou seja, o proprio {@code usuarioId} (ver {@code JwtGeradorDeAccessToken}).
     * E o mecanismo padrao de destino-por-usuario do Spring, resolvido via {@code SimpUserRegistry} a
     * partir do principal associado a sessao no handshake — entrega a TODAS as sessoes daquele usuario
     * de uma vez, o que e o comportamento correto: visibilidade e por usuario, nao por aba.
     */
    private void enviarParaUsuario(UUID usuarioId, String destino, String payload) {
        template.convertAndSendToUser(usuarioId.toString(), destino, payload);
    }
}
