package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.finalizacaomassa.MotivoDoItemDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDaFinalizacaoEmMassa;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDoItemDeFinalizacao;

/**
 * Persistencia da finalizacao em massa. Os metodos de consulta e criacao rodam sob a visibilidade (RLS) de
 * quem pede; os de worker, em contexto de servico.
 */
public interface FinalizacaoEmMassaRepositorio {

    record OperacaoDeFinalizacao(
            UUID id,
            UUID solicitanteId,
            List<UUID> atendenteIds,
            Instant periodoInicio,
            Instant periodoFim,
            String fuso,
            LocalDate dataDe,
            LocalDate dataAte,
            LocalTime horaInicio,
            LocalTime horaFim,
            StatusDaFinalizacaoEmMassa status,
            int encontrados,
            int finalizados,
            int ignorados,
            int falhas,
            Instant criadaEm,
            Instant iniciadaEm,
            Instant concluidaEm,
            String impressaoDosFiltros) {}

    record ContagemPorAtendente(UUID atendenteId, String nome, long quantidade) {}

    record ContagemDosItens(int pendentes, int finalizados, int ignorados, int falhas) {

        public int processados() {
            return finalizados + ignorados + falhas;
        }
    }

    record ItemDeFinalizacao(
            UUID atendimentoId,
            UUID atendenteId,
            String atendenteNome,
            String leadNome,
            StatusDoItemDeFinalizacao status,
            MotivoDoItemDeFinalizacao motivo,
            Instant processadoEm) {}

    /** Item ainda por processar, com o necessario para revalidar antes de finalizar. */
    record ItemPendente(UUID operacaoId, UUID atendimentoId, UUID atendenteId) {}

    // --- pedido (sob a visibilidade de quem pede) -------------------------------------------------

    /** Elegiveis por atendente, ja ordenados por nome; atendentes sem resultado vem com zero. */
    List<ContagemPorAtendente> contarElegiveis(FiltroDeFinalizacao filtro);

    /** Ids existentes entre os informados (um id inexistente e erro de quem chamou). */
    Set<UUID> usuariosExistentes(Collection<UUID> ids);

    Optional<OperacaoDeFinalizacao> porChaveDeIdempotencia(UUID solicitanteId, String chave);

    Optional<UUID> operacaoAtiva();

    /**
     * Cria a operacao e congela, na mesma transacao, os atendimentos elegiveis visiveis a quem pede.
     *
     * @throws OperacaoAtivaJaExisteException se ja houver uma operacao ativa na instancia
     */
    OperacaoDeFinalizacao criarCongelandoElegiveis(
            UUID solicitanteId, String chaveDeIdempotencia, FiltroDeFinalizacao filtro, int limite);

    Optional<OperacaoDeFinalizacao> porId(UUID id);

    ContagemDosItens contarItens(UUID operacaoId);

    List<ItemDeFinalizacao> itens(UUID operacaoId, StatusDoItemDeFinalizacao status, int limite, int deslocamento);

    List<OperacaoDeFinalizacao> recentes(UUID solicitanteId, int limite);

    // --- worker (contexto de servico) ---------------------------------------------------------------

    /** Reserva a proxima operacao ativa (lease), ou vazio. Operacao com lease vigente de outro no fica de fora. */
    Optional<OperacaoDeFinalizacao> reivindicarProxima(Instant agora, java.time.Duration lease);

    List<ItemPendente> proximosPendentes(UUID operacaoId, int limite);

    /** Marca o item; so age se ele ainda estiver PENDENTE (idempotente). @return se alterou. */
    boolean marcarItem(
            UUID operacaoId, UUID atendimentoId, StatusDoItemDeFinalizacao status,
            MotivoDoItemDeFinalizacao motivo, Instant quando);

    /**
     * Se nao restam pendentes, conclui a operacao (contadores finais) e grava o aviso de cada usuario
     * efetivamente afetado, tudo na mesma transacao. @return se concluiu agora.
     */
    boolean concluirSeNaoHaPendentes(UUID operacaoId, Instant agora);

    void liberarLease(UUID operacaoId);

    class OperacaoAtivaJaExisteException extends RuntimeException {
        public OperacaoAtivaJaExisteException() {
            super("ja existe uma finalizacao em massa em andamento");
        }
    }
}
