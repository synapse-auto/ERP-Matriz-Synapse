package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.encaminhamentodochat.EnvioDoEncaminhamentoDoChat.Comando;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.equipe.application.chat.ConsultarMensagemDoChatParaEncaminhamentoUseCase;
import com.synapse.crm.equipe.application.chat.MensagemDoChatParaCliente;

/**
 * Encaminha uma mensagem do Chat Interno para o cliente de um atendimento, pelo fluxo oficial de
 * envio (outbox, idempotência, janela, adaptador do provedor ativo).
 *
 * <p>Não é transacional de propósito: a leitura da mensagem interna roda no pool geral, o envio no
 * pool do chat ({@link EnvioDoEncaminhamentoDoChat}), e uma transação aqui os misturaria. A ordem
 * importa: a repetição da mesma chave responde antes de qualquer outra leitura, então um clique
 * duplicado devolve o resultado original mesmo que a mensagem interna tenha sido apagada depois.
 *
 * <p>Duas autorizações independentes: participar da conversa interna (lido em
 * {@link ConsultarMensagemDoChatParaEncaminhamentoUseCase}) e poder responder no atendimento de destino
 * (RLS do alcance + {@code atendimentos.responder}, no envio).
 */
@Service
public class EncaminharDoChatInternoParaClienteUseCase {

    private final ConsultarMensagemDoChatParaEncaminhamentoUseCase mensagensDoChat;
    private final MontadorDeConteudoDoChatParaCliente montador;
    private final EnvioDoEncaminhamentoDoChat envio;

    public EncaminharDoChatInternoParaClienteUseCase(
            ConsultarMensagemDoChatParaEncaminhamentoUseCase mensagensDoChat,
            MontadorDeConteudoDoChatParaCliente montador,
            EnvioDoEncaminhamentoDoChat envio) {
        this.mensagensDoChat = mensagensDoChat;
        this.montador = montador;
        this.envio = envio;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.responder')")
    public EncaminhamentoDoChatParaCliente executar(
            UUID conversaId, UUID mensagemId, UUID atendimentoId, String chaveIdempotencia) {
        Comando comando = new Comando(conversaId, mensagemId, atendimentoId, normalizar(chaveIdempotencia));
        return envio.repeticao(comando).orElseGet(() -> enviarNovo(comando));
    }

    private EncaminhamentoDoChatParaCliente enviarNovo(Comando comando) {
        MensagemDoChatParaCliente mensagem = mensagensDoChat.executar(comando.conversaId(), comando.mensagemId());
        ConteudoDeEnvio conteudo = montador.montar(mensagem);
        return envio.executar(comando, conteudo);
    }

    private static String normalizar(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new IdempotencyKeyInvalidaException();
        }
        String normalizada = chave.trim();
        if (normalizada.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key excede 255 caracteres");
        }
        return normalizada;
    }
}
