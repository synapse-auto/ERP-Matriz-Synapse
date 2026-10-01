package com.synapse.crm.atendimento.application.origem;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Grava a origem de uma mensagem automatica na transacao de quem a registra e avisa no log quando
 * ela falta. O marcador {@code [ORIGEM_NAO_INFORMADA]} e o que se procura para achar o fluxo do n8n
 * ainda sem os campos.
 */
@Component
public class RegistroDeOrigemDaMensagem {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeOrigemDaMensagem.class);

    private final OrigemDeMensagemAutomaticaRepositorio origens;

    public RegistroDeOrigemDaMensagem(OrigemDeMensagemAutomaticaRepositorio origens) {
        this.origens = origens;
    }

    public void registrar(
            String caminho, UUID mensagemId, UUID atendimentoId, UUID leadId, Instant enviadoEm, OrigemDaMensagem origem) {
        if (!origem.informada()) {
            log.warn(
                    "[ORIGEM_NAO_INFORMADA] {} gravou mensagem automatica sem origem ({}): mensagem={} atendimento={} execucao={}",
                    caminho,
                    origem.aviso(),
                    mensagemId,
                    atendimentoId,
                    origem.execucaoId());
        }
        origens.registrar(mensagemId, atendimentoId, leadId, enviadoEm, origem);
    }
}
