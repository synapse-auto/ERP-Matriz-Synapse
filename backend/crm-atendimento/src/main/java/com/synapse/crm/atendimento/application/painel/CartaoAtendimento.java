package com.synapse.crm.atendimento.application.painel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.atendimento.OrigemResultadoVenda;
import com.synapse.crm.atendimento.domain.atendimento.ResultadoVenda;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

/**
 * Leitura para o card da lista de conversas — nunca escrita. Campos de etapa/atendente/ultima
 * mensagem sao nulos quando nao ha o dado (lead sem etapa, atendimento sem dono, atendimento sem
 * mensagem ainda).
 *
 * @param ultimaMensagemDoLeadEm instante da ultima mensagem com {@code remetenteTipo = LEAD} em
 *     <b>qualquer atendimento do lead</b>, e nao so no atendimento do cartao (E114): a janela de 24h
 *     e do cliente, entao um atendimento novo reaberto para um lead que escreveu ha minutos precisa
 *     herdar essa janela. Usado pelo frontend para estimar a janela antes de o atendente digitar; a
 *     autoridade real continua sendo a checagem no {@code EnviarMensagemUseCase}.
 * @param naoLidas mensagens do lead posteriores a leitura do responsavel exibido, para papeis
 *     de gestao quando houver responsavel; sem responsavel e para atendentes/participantes, usa a
 *     leitura pessoal. Abrange todos os ciclos do mesmo lead, sempre com o responsavel atual.
 * @param convitePendente convite CONVITE vigente destinado ao usuario autenticado;
 *     indicador pessoal de apresentacao, nunca autorizacao de envio.
 */
public record CartaoAtendimento(
        UUID atendimentoId,
        UUID leadId,
        String leadNome,
        String leadFotoUrl,
        String leadEmpresa,
        String leadCodigo,
        String canalTipo,
        UUID etapaId,
        String etapaNome,
        String etapaCor,
        StatusAtendimento status,
        UUID atendenteId,
        String atendenteNome,
        UUID atendimentoAtivoId,
        String ultimaMensagemPreview,
        String ultimaMensagemRemetenteTipo,
        Instant ultimaMensagemEm,
        Instant ultimaMensagemDoLeadEm,
        long naoLidas,
        boolean emNegociacao,
        ResultadoVenda resultadoVenda,
        BigDecimal valorVenda,
        UUID vendaRegistradaPorId,
        String vendaRegistradaPorNome,
        Instant vendaRegistradaEm,
        OrigemResultadoVenda origemResultadoVenda,
        boolean convitePendente) {

    /** Compatibilidade para fixtures e consumidores que ainda não projetam os dados comerciais. */
    public CartaoAtendimento(
            UUID atendimentoId, UUID leadId, String leadNome, String leadFotoUrl, String leadEmpresa,
            String leadCodigo, String canalTipo, UUID etapaId, String etapaNome, String etapaCor,
            StatusAtendimento status, UUID atendenteId, String atendenteNome, UUID atendimentoAtivoId,
            String ultimaMensagemPreview, String ultimaMensagemRemetenteTipo, Instant ultimaMensagemEm,
            Instant ultimaMensagemDoLeadEm, long naoLidas) {
        this(atendimentoId, leadId, leadNome, leadFotoUrl, leadEmpresa, leadCodigo, canalTipo, etapaId,
                etapaNome, etapaCor, status, atendenteId, atendenteNome, atendimentoAtivoId,
                ultimaMensagemPreview, ultimaMensagemRemetenteTipo, ultimaMensagemEm,
                ultimaMensagemDoLeadEm, naoLidas, false, null, null, null, null, null, null, false);
    }
}
