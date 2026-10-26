package com.synapse.crm.atendimento.application.canal;

import java.time.Clock;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Service
public class AplicarStatusLegadoUseCase {
    private final StatusLegadoDeCanalRepositorio repositorio;
    private final MensagemRepositorio mensagens;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    public AplicarStatusLegadoUseCase(StatusLegadoDeCanalRepositorio repositorio,MensagemRepositorio mensagens,
            ApplicationEventPublisher eventos,Clock relogio){this.repositorio=repositorio;this.mensagens=mensagens;this.eventos=eventos;this.relogio=relogio;}
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER,propagation=Propagation.REQUIRES_NEW)
    public void executar(List<StatusDeEntregaDoCanal> statuses) {
        // Recusa alias alternativo em vez de deixar o aplicador cair em um ID nao conferido.
        var seguros=statuses.stream().filter(s->repositorio.mensagemAnteriorAoCorte(s.wamid()))
                .map(s->new StatusDeEntregaDoCanal(s.wamid(),s.statusEntrega(),s.codigoErro(),s.tituloErro(),null)).toList();
        for(var status:seguros) {
            StatusEntrega novo;
            try {novo=StatusEntrega.valueOf(status.statusEntrega());}catch(IllegalArgumentException desconhecido){continue;}
            mensagens.aplicarStatusDoProvedor(status.wamid(),novo,status.codigoErro(),status.tituloErro())
                .ifPresent(m->eventos.publishEvent(new MudancaDeStatusDeEntrega(m.mensagemId(),m.atendimentoId(),m.leadId(),m.status().name(),relogio.instant())));
        }
    }
}
