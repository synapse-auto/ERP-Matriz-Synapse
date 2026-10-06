package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.equipe.application.chat.ConsultarMensagemDoChatParaEncaminhamentoUseCase;
import com.synapse.crm.equipe.application.chat.MensagemDoChatParaCliente;

/** A tela de confirmação: cliente, telefone mascarado, atendimento, conteúdo e o que o envio vai fazer. */
@Service
public class PreVisualizarEncaminhamentoDoChatUseCase {

    private final ConsultarMensagemDoChatParaEncaminhamentoUseCase mensagensDoChat;
    private final DestinoDoEncaminhamentoDoChat destino;

    public PreVisualizarEncaminhamentoDoChatUseCase(
            ConsultarMensagemDoChatParaEncaminhamentoUseCase mensagensDoChat,
            DestinoDoEncaminhamentoDoChat destino) {
        this.mensagensDoChat = mensagensDoChat;
        this.destino = destino;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.responder')")
    public PreviaDoEncaminhamento executar(UUID conversaId, UUID mensagemId, UUID atendimentoId) {
        MensagemDoChatParaCliente mensagem = mensagensDoChat.executar(conversaId, mensagemId);
        return destino.previa(mensagem, atendimentoId);
    }
}
