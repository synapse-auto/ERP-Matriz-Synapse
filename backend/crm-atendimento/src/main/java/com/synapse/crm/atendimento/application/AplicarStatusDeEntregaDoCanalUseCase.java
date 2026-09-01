package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Aplica {@code statuses[]} do provedor no ciclo de entrega.
 *
 * <p>{@code REQUIRES_NEW} de proposito: o {@code POST} do webhook ja abre uma transacao sem
 * contexto de servico (nao ha usuario). Sem uma transacao nova, o {@code SET LOCAL} da RLS ja
 * teria rodado vazio e o UPDATE em {@code mensagem} via {@code atendimento} viraria no-op. O job
 * de entrada faz o mesmo — marca {@link
 * com.synapse.crm.sharedkernel.identidade.ContextoDeServico} e so depois abre a transacao.
 *
 * <p>Nao tem {@code @PreAuthorize}: a autorizacao e a assinatura HMAC na borda.
 */
@Service
public class AplicarStatusDeEntregaDoCanalUseCase {

    private static final Logger log = LoggerFactory.getLogger(AplicarStatusDeEntregaDoCanalUseCase.class);

    private final MensagemRepositorio mensagens;
    private final MensagemIdExternoRepositorio idsExternos;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AplicarStatusDeEntregaDoCanalUseCase(
            MensagemRepositorio mensagens,
            MensagemIdExternoRepositorio idsExternos,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.mensagens = mensagens;
        this.idsExternos = idsExternos;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void executar(List<StatusDeEntregaDoCanal> atualizacoes) {
        if (atualizacoes == null || atualizacoes.isEmpty()) {
            return;
        }
        Instant agora = Instant.now(relogio);
        int desconhecidos = 0;
        for (StatusDeEntregaDoCanal atualizacao : atualizacoes) {
            StatusEntrega novo;
            try {
                novo = StatusEntrega.valueOf(atualizacao.statusEntrega());
            } catch (RuntimeException e) {
                continue;
            }
            var aplicado = mensagens.aplicarStatusDoProvedor(
                    atualizacao.wamid(), novo, atualizacao.codigoErro(), atualizacao.tituloErro());
            if (aplicado.isEmpty()) {
                if (!idsExternos.existe(atualizacao.wamid())) {
                    desconhecidos++;
                }
                continue;
            }
            var linha = aplicado.get();
            eventos.publishEvent(new MudancaDeStatusDeEntrega(
                    linha.mensagemId(),
                    linha.atendimentoId(),
                    linha.leadId(),
                    linha.status().name(),
                    agora));
        }
        if (desconhecidos > 0) {
            log.info(
                    "Webhook de status com {} wamid(s) desconhecido(s); ignorados.",
                    desconhecidos);
        }
    }
}
