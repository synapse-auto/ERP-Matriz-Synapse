package com.synapse.crm.atendimento.application.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.historico.HistoricoDeMensagensRepositorio;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Le contexto limitado, sem URLs de mídia ou metadados do provedor. */
@Service
public class ContextoEv05UseCase {
    private final AtendimentoRepositorio atendimentos;
    private final HistoricoDeMensagensRepositorio historico;
    private final int limite;

    public ContextoEv05UseCase(
            AtendimentoRepositorio atendimentos,
            HistoricoDeMensagensRepositorio historico,
            @Value("${synapse.atendimento.historico.tamanho-pagina}") int limite) {
        this.atendimentos = atendimentos;
        this.historico = historico;
        this.limite = limite;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public ContextoResposta executar(UUID atendimentoId) {
        var atendimento = atendimentos
                .porId(atendimentoId)
                .filter(item -> item.status() == StatusAtendimento.EM_ATENDIMENTO)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "atendimento elegivel", atendimentoId));
        List<MensagemDoHistorico> mensagens = historico.doAtendimento(atendimentoId, limite + 1);
        boolean temMais = mensagens.size() > limite;
        List<MensagemDoHistorico> usadas = temMais ? mensagens.subList(0, limite) : mensagens;
        List<MensagemContextoResposta> itens = usadas.reversed().stream().map(MensagemContextoResposta::de).toList();
        Instant contextoAte = itens.isEmpty() ? atendimento.iniciadoEm() : itens.getLast().enviadoEm();
        return new ContextoResposta(
                atendimento.id(), atendimento.leadId(), itens, temMais, !itens.isEmpty(), contextoAte);
    }

    public int limite() {
        return limite;
    }

    public record ContextoResposta(
            UUID atendimentoId,
            UUID leadId,
            List<MensagemContextoResposta> mensagens,
            boolean temMais,
            boolean conteudoSuficiente,
            Instant contextoAte) {
        public ContextoResposta {
            mensagens = List.copyOf(mensagens);
        }
    }

    public record MensagemContextoResposta(
            UUID mensagemId, String remetente, String tipo, String conteudo, Instant enviadoEm) {
        static MensagemContextoResposta de(MensagemDoHistorico item) {
            var mensagem = item.mensagem();
            return new MensagemContextoResposta(
                    mensagem.id(),
                    item.remetenteNome(),
                    mensagem.tipo().name(),
                    mensagem.conteudo(),
                    mensagem.enviadoEm());
        }
    }
}
