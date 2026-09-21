package com.synapse.crm.core.application.lead;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.etapa.EtapaNaoEncontradaException;
import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.evento.EtapaDoLeadAlterada;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.timeline.OrigemEvento;

/**
 * A Automacao move o lead para outra etapa do funil (E196), lendo a conversa em vez de um humano na
 * tela.
 *
 * <p>Reaproveita a mesma logica de "etapa anterior / etapa nova" de {@link AtualizarLeadUseCase} e
 * publica o mesmo {@link EtapaDoLeadAlterada} que o caminho humano publica — historico e auditoria de
 * etapa nao podem depender de quem fez a mudanca. A diferenca e o ator: aqui nao ha usuario, entao o
 * evento nasce com {@code atorId} nulo e {@code atorTipo = AUTOMACAO}, a mesma convencao que
 * {@code EventoDeAtendimento.AtendimentoTransferido} ja usa quando quem agiu e a Automacao.
 *
 * <p>Sem restricao a atendimento {@code EM_ATENDIMENTO}: essa restricao do EV-05 existe para nao
 * pisar no resumo humano em andamento, e nao se aplica aqui — a etapa e propriedade do lead, nao do
 * atendimento em curso (decisao registrada no relatorio da E196).
 */
@Service
public class AlterarEtapaDoLeadPelaAutomacaoUseCase {

    private static final String OPERACAO = "ETAPA_LEAD";

    private final LeadRepositorio leads;
    private final EtapaRepositorio etapas;
    private final IdempotenciaDeComandoDeLead idempotencia;
    private final ApplicationEventPublisher eventos;
    private final ObjectMapper json;
    private final Clock relogio;

    public AlterarEtapaDoLeadPelaAutomacaoUseCase(
            LeadRepositorio leads,
            EtapaRepositorio etapas,
            IdempotenciaDeComandoDeLead idempotencia,
            ApplicationEventPublisher eventos,
            ObjectMapper json,
            Clock relogio) {
        this.leads = leads;
        this.etapas = etapas;
        this.idempotencia = idempotencia;
        this.eventos = eventos;
        this.json = json;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    public ResultadoEtapaLead executar(UUID leadId, UUID etapaId, String chave) {
        exigirChave(chave);
        String hash = hash(OPERACAO + "\n" + leadId + "\n" + etapaId);
        var existente = idempotencia.buscar(chave);
        if (existente.isPresent()) {
            return resolver(existente.get(), chave, hash, leadId);
        }

        EtapaAtendimento etapaNova =
                etapas.porId(etapaId).orElseThrow(() -> new EtapaNaoEncontradaException(etapaId));
        Lead atual = leads.porId(leadId).orElseThrow(() -> new LeadDaAutomacaoNaoEncontradoException(leadId));

        var reserva = idempotencia.reservar(chave, OPERACAO, leadId, hash);
        if (!reserva.nova()) {
            return resolver(reserva, chave, hash, leadId);
        }

        ResultadoEtapaLead resultado = aplicar(atual, etapaId, etapaNova);
        idempotencia.concluir(chave, serializar(resultado));
        return resultado;
    }

    private ResultadoEtapaLead aplicar(Lead atual, UUID etapaId, EtapaAtendimento etapaNova) {
        if (Objects.equals(atual.etapaAtendimentoId(), etapaId)) {
            return new ResultadoEtapaLead(atual.id(), etapaId, false);
        }
        EtapaAtendimento etapaAnterior = atual.etapaAtendimentoId() == null
                ? null
                : etapas.porId(atual.etapaAtendimentoId())
                        .orElseThrow(() -> new EtapaNaoEncontradaException(atual.etapaAtendimentoId()));
        Lead salvo = leads.salvar(atual.comEtapaAtendimento(etapaId))
                .orElseThrow(() -> new LeadDaAutomacaoNaoEncontradoException(atual.id()));
        eventos.publishEvent(new EtapaDoLeadAlterada(
                salvo.id(),
                etapaAnterior,
                etapaNova,
                salvo.atendenteResponsavelId(),
                null,
                OrigemEvento.AUTOMACAO,
                Instant.now(relogio)));
        return new ResultadoEtapaLead(salvo.id(), etapaId, true);
    }

    private static void exigirChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new IdempotencyKeyInvalidaException();
        }
    }

    private ResultadoEtapaLead resolver(
            IdempotenciaDeComandoDeLead.Reserva reserva, String chave, String hash, UUID leadId) {
        if (!OPERACAO.equals(reserva.operacao())
                || !reserva.hashDaRequisicao().equals(hash)
                || !leadId.equals(reserva.leadId())) {
            throw new ChaveIdempotenciaReutilizadaException(chave, OPERACAO, reserva.leadId());
        }
        if (reserva.respostaJson() == null) {
            throw new IllegalStateException("reserva de etapa da Automacao sem resposta concluida");
        }
        try {
            return json.readValue(reserva.respostaJson(), ResultadoEtapaLead.class);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("resposta de etapa da Automacao ilegivel", erro);
        }
    }

    private String serializar(ResultadoEtapaLead resposta) {
        try {
            return json.writeValueAsString(resposta);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("falha ao serializar resposta de etapa da Automacao", erro);
        }
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel", erro);
        }
    }

    public record ResultadoEtapaLead(UUID leadId, UUID etapaId, boolean alterado) {}
}
