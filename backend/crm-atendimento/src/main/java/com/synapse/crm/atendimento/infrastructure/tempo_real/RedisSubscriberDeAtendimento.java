package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.tempo_real.RevalidarAssinaturaTempoRealUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
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
    static final String DESTINO_NOTIFICACOES = "/queue/notificacoes";

    private final RegistroDeAssinaturas registro;
    private final SimpMessagingTemplate template;
    private final ObjectMapper json;
    private final RevalidarAssinaturaTempoRealUseCase revalidar;

    RedisSubscriberDeAtendimento(
            RegistroDeAssinaturas registro,
            SimpMessagingTemplate template,
            ObjectMapper json,
            RevalidarAssinaturaTempoRealUseCase revalidar) {
        this.registro = registro;
        this.template = template;
        this.json = json;
        this.revalidar = revalidar;
    }

    @Override
    public void onMessage(Message mensagem, byte[] padrao) {
        String canal = new String(mensagem.getChannel(), StandardCharsets.UTF_8);
        String corpo = new String(mensagem.getBody(), StandardCharsets.UTF_8);
        if (CanaisRedis.eChatInterno(canal)) {
            entregarChatInterno(corpo);
            return;
        }
        UUID atendimentoId = CanaisRedis.atendimentoDoCanal(canal);
        if (atendimentoId == null) {
            return;
        }

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
            avisarRecebedor(dados);
            avisarDonoAnteriorQuandoVoltaParaIa(dados);
        }
        if ("PEDIDO_ENTRADA".equals(tipo)) avisarPedidoAoDono(dados);
        if ("RESPOSTA_PEDIDO_ENTRADA".equals(tipo)) avisarRespostaAoSolicitante(dados);
        if ("PARTICIPANTE_SAIU".equals(tipo)) {
            JsonNode p=dados.path("participanteId");
            if (!p.isMissingNode() && !p.isNull()) registro.removerUsuarioDoAtendimento(atendimentoId, UUID.fromString(p.asText()));
        }

        // Um usuario pode ter mais de uma sessao (duas abas) autorizadas ao mesmo
        // atendimento; convertAndSendToUser ja entrega a TODAS as sessoes daquele
        // usuario de uma vez, entao um usuario so pode receber uma vez por rodada.
        Set<UUID> usuariosEntregues = new java.util.HashSet<>();
        for (AssinaturaAutorizada assinatura : registro.doAtendimento(atendimentoId)) {
            if (registro.expirou(assinatura) && !revalidarERenovar(assinatura)) {
                continue;
            }
            if (usuariosEntregues.add(assinatura.usuarioId())) {
                enviarParaUsuario(assinatura.usuarioId(), "/queue/atendimento." + atendimentoId, corpo);
            }
        }
    }

    private void entregarChatInterno(String corpo) {
        try {
            JsonNode envelope = json.readTree(corpo);
            String tipo = envelope.path("tipo").asText("CHAT_INTERNO_MENSAGEM");
            for (JsonNode destinatario : envelope.path("destinatarios")) {
                ObjectNode notificacao = json.createObjectNode();
                notificacao.put("tipo", tipo);
                ObjectNode dados = json.createObjectNode();
                dados.set("conversaId", envelope.path("conversaId"));
                dados.set("mensagemId", envelope.path("mensagemId"));
                if ("CHAT_INTERNO_REACAO".equals(tipo)) {
                    dados.set("reacoes", envelope.path("reacoes"));
                } else {
                    dados.set("remetenteId", envelope.path("remetenteId"));
                    dados.set("conteudo", envelope.path("conteudo"));
                    dados.set("enviadoEm", envelope.path("enviadoEm"));
                }
                notificacao.set("dados", dados);
                enviarParaUsuario(UUID.fromString(destinatario.asText()), DESTINO_NOTIFICACOES, notificacao.toString());
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Envelope de chat interno ilegivel.", e);
        }
    }

    private void avisarRecebedor(JsonNode dados) {
        JsonNode paraNo = dados.path("paraAtendenteId");
        if (paraNo.isMissingNode() || paraNo.isNull() || paraNo.asText().isBlank()) {
            return;
        }
        UUID paraAtendenteId;
        try {
            paraAtendenteId = UUID.fromString(paraNo.asText());
        } catch (IllegalArgumentException e) {
            log.warn("Transferencia com destinatario invalido no evento de tempo real.", e);
            return;
        }

        JsonNode atorNo = dados.path("quemTransferiu");
        if (!atorNo.isNull() && !atorNo.isMissingNode() && paraAtendenteId.toString().equals(atorNo.asText())) {
            return;
        }

        ObjectNode envelope = json.createObjectNode();
        envelope.put("tipo", "TRANSFERENCIA_RECEBIDA");
        ObjectNode aviso = json.createObjectNode();
        aviso.set("atendimentoId", dados.path("atendimentoId"));
        aviso.set("leadId", dados.path("leadId"));
        aviso.set("leadNome", dados.path("leadNome"));
        aviso.set("quemTransferiu", dados.path("quemTransferiu"));
        aviso.set("atorTipo", dados.path("atorTipo"));
        aviso.set("ocorridoEm", dados.path("ocorridoEm"));
        envelope.set("dados", aviso);
        enviarParaUsuario(paraAtendenteId, DESTINO_NOTIFICACOES, envelope.toString());
    }

    private void avisarDonoAnteriorQuandoVoltaParaIa(JsonNode dados) {
        JsonNode paraNo = dados.path("paraAtendenteId");
        JsonNode deNo = dados.path("deAtendenteId");
        if (!paraNo.isNull() && !paraNo.isMissingNode()) return;
        if (deNo.isNull() || deNo.isMissingNode() || deNo.asText().isBlank()) return;

        ObjectNode envelope = json.createObjectNode();
        envelope.put("tipo", "ATENDIMENTO_DEVOLVIDO_PARA_IA");
        ObjectNode aviso = json.createObjectNode();
        aviso.set("atendimentoId", dados.path("atendimentoId"));
        aviso.set("leadId", dados.path("leadId"));
        aviso.set("leadNome", dados.path("leadNome"));
        aviso.set("ocorridoEm", dados.path("ocorridoEm"));
        envelope.set("dados", aviso);
        try {
            enviarParaUsuario(UUID.fromString(deNo.asText()), DESTINO_NOTIFICACOES, envelope.toString());
        } catch (IllegalArgumentException erro) {
            log.warn("Transferencia para IA com dono anterior invalido.", erro);
        }
    }

    private void avisarPedidoAoDono(JsonNode dados) {
        JsonNode dono=dados.path("donoId"); if(dono.isMissingNode()||dono.isNull()) return;
        ObjectNode e=json.createObjectNode(); e.put("tipo","PEDIDO_ENTRADA_ATENDIMENTO"); ObjectNode d=json.createObjectNode();
        d.set("atendimentoId",dados.path("atendimentoId")); d.set("leadId",dados.path("leadId")); d.set("solicitanteId",dados.path("solicitanteId")); d.set("solicitanteNome",dados.path("solicitanteNome")); e.set("dados",d);
        enviarParaUsuario(UUID.fromString(dono.asText()), DESTINO_NOTIFICACOES, e.toString());
    }
    private void avisarRespostaAoSolicitante(JsonNode dados) {
        JsonNode solicitante=dados.path("solicitanteId"); if(solicitante.isMissingNode()||solicitante.isNull()) return;
        ObjectNode e=json.createObjectNode(); e.put("tipo","PEDIDO_ENTRADA_RESPONDIDO"); ObjectNode d=json.createObjectNode(); d.set("atendimentoId",dados.path("atendimentoId")); d.set("aprovado",dados.path("aprovado")); e.set("dados",d);
        enviarParaUsuario(UUID.fromString(solicitante.asText()), DESTINO_NOTIFICACOES, e.toString());
    }

    /**
     * TTL vencido (E07 §0): a revogacao por transferencia e o caminho rapido, mas Redis pub/sub e
     * at-most-once — se aquele evento se perdeu, esta e a rede de seguranca. So consulta o banco
     * quando o TTL da propria assinatura venceu, nunca por mensagem.
     *
     * <p>{@code ContextoDeServico} e publicado aqui — do lado de fora de {@link
     * RevalidarAssinaturaTempoRealUseCase} — porque este listener nunca autenticou ninguem. Ver a
     * nota de classe daquele use case sobre por que o contexto nao pode ser aplicado por
     * auto-invocacao de dentro dele mesmo.
     */
    private boolean revalidarERenovar(AssinaturaAutorizada assinatura) {
        boolean valida = ContextoDeServico.buscarComo(
                "tempo-real.revalidacao-ttl",
                () -> revalidar.aindaValida(
                        assinatura.atendimentoId(), assinatura.usuarioId(), assinatura.papel()));
        if (valida) {
            registro.renovar(assinatura);
            return true;
        }

        registro.remover(assinatura);
        enviarParaUsuario(
                assinatura.usuarioId(),
                DESTINO_REVOGACAO,
                "{\"atendimentoId\":\"" + assinatura.atendimentoId() + "\"}");
        log.debug(
                "Assinatura de {} revogada na revalidacao de TTL para o usuario {}.",
                assinatura.atendimentoId(),
                assinatura.usuarioId());
        return false;
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
