package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;

/**
 * Leituras de destinatarios para a tela e o CSV. Roda no pool geral: listar e exportar milhares de linhas
 * nao pode disputar conexao com o caminho de mensagens.
 */
public interface ConsultasDeDestinatarios {

    Pagina<Linha> listar(Filtro filtro, int pagina, int tamanho);

    /** Percorre todos os destinatarios que casam com o filtro, em ordem estavel, sem materializar a lista. */
    void percorrer(Filtro filtro, Consumer<Linha> consumidor);

    /**
     * @param status nulo = qualquer
     * @param motivo nulo = qualquer
     * @param soConferencia so os sinalizados para conferencia manual
     */
    record Filtro(UUID campanhaId, StatusDoDestinatario status, MotivoDoDestinatario motivo, boolean soConferencia) {}

    record Linha(
            UUID id,
            UUID leadId,
            String nome,
            String telefone,
            StatusDoDestinatario status,
            MotivoDoDestinatario motivo,
            Integer codigoDeErro,
            Instant enviadoEm,
            Instant entregueEm,
            Instant lidoEm,
            Instant respondeuEm,
            Instant conferenciaEm) {}
}
