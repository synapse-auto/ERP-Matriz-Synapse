package com.synapse.crm.core.application.lead;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.ValidadorDeDadosCustomizados;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/**
 * A Automacao grava a data de nascimento do lead lida da conversa (E196), na mesma convencao de
 * campo customizado reservado que a E194 (mensagens de aniversario) ja consome: chave
 * {@code data_nascimento}, tipo {@code DATA}, dentro de {@code lead.dados_customizados}.
 *
 * <p>Reaproveita {@link ValidadorDeDadosCustomizados} — a mesma validacao/canonicalizacao que
 * {@link AtualizarLeadUseCase} usa para qualquer campo customizado — em vez de reimplementar parsing
 * de data. So preenche campo vazio, mesma razao do {@code Ev05LeadUseCase.preencher}: se um humano ja
 * corrigiu a data na ficha, a Automacao nao pode sobrescrever silenciosamente. Quando ja existe um
 * valor diferente, a resposta continua 200 mas com {@code situacao = IGNORADO_JA_PREENCHIDO} — o
 * mesmo vocabulario que {@code AutomacaoEv05LeadRepositorio.ResultadoCampo} ja usa para email/cpf/
 * empresa/localizacao, para nao inventar um segundo jeito de dizer "nao mudei nada" (decisao
 * registrada no relatorio da E196).
 */
@Service
public class DefinirDataNascimentoDoLeadPelaAutomacaoUseCase {

    private static final String OPERACAO = "DATA_NASCIMENTO_LEAD";

    /** Mesma chave reservada que {@code AniversariantesDoDiaRepositorioJdbc} (E194) le de volta. */
    static final String CHAVE_DATA_NASCIMENTO = "data_nascimento";

    private final LeadRepositorio leads;
    private final CampoCustomizadoRepositorio camposCustomizados;
    private final IdempotenciaDeComandoDeLead idempotencia;
    private final ObjectMapper json;

    public DefinirDataNascimentoDoLeadPelaAutomacaoUseCase(
            LeadRepositorio leads,
            CampoCustomizadoRepositorio camposCustomizados,
            IdempotenciaDeComandoDeLead idempotencia,
            ObjectMapper json) {
        this.leads = leads;
        this.camposCustomizados = camposCustomizados;
        this.idempotencia = idempotencia;
        this.json = json;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    @Auditable(
            acao = "DEFINIR_DATA_NASCIMENTO_PELA_AUTOMACAO",
            entidadeTipo = "LEAD",
            capturarDados = false,
            atorTipo = "AUTOMACAO")
    public ResultadoDataNascimentoLead executar(UUID leadId, String dataNascimento, String chave) {
        exigirChave(chave);
        String hash = hash(OPERACAO + "\n" + leadId + "\n" + dataNascimento);
        var existente = idempotencia.buscar(chave);
        if (existente.isPresent()) {
            return resolver(existente.get(), chave, hash, leadId);
        }

        // Formato invalido e recusado aqui, antes de qualquer leitura/escrita do lead: canonicaliza
        // ou lanca DadosCustomizadosInvalidosException, sem tocar em nenhuma linha.
        CampoCustomizado campo = camposCustomizados.porChave(CHAVE_DATA_NASCIMENTO)
                .filter(candidato -> candidato.tipo() == TipoCampoCustomizado.DATA)
                .orElseThrow(CampoDataNascimentoNaoCadastradoException::new);
        String canonico = (String) ValidadorDeDadosCustomizados.validar(
                Map.of(CHAVE_DATA_NASCIMENTO, dataNascimento), List.of(campo))
                .get(CHAVE_DATA_NASCIMENTO);

        Lead atual = leads.porId(leadId).orElseThrow(() -> new LeadDaAutomacaoNaoEncontradoException(leadId));

        var reserva = idempotencia.reservar(chave, OPERACAO, leadId, hash);
        if (!reserva.nova()) {
            return resolver(reserva, chave, hash, leadId);
        }

        ResultadoDataNascimentoLead resultado = aplicar(atual, canonico);
        idempotencia.concluir(chave, serializar(resultado));
        return resultado;
    }

    private ResultadoDataNascimentoLead aplicar(Lead atual, String canonico) {
        Object valorAtual = atual.dadosCustomizados().get(CHAVE_DATA_NASCIMENTO);
        if (valorAtual != null && !String.valueOf(valorAtual).isBlank()) {
            return new ResultadoDataNascimentoLead(
                    atual.id(), String.valueOf(valorAtual), Situacao.IGNORADO_JA_PREENCHIDO);
        }
        Map<String, Object> mesclado = new LinkedHashMap<>(atual.dadosCustomizados());
        mesclado.put(CHAVE_DATA_NASCIMENTO, canonico);
        Lead salvo = leads.salvar(atual.comDadosCustomizados(mesclado))
                .orElseThrow(() -> new LeadDaAutomacaoNaoEncontradoException(atual.id()));
        return new ResultadoDataNascimentoLead(salvo.id(), canonico, Situacao.APLICADO);
    }

    private static void exigirChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new IdempotencyKeyInvalidaException();
        }
    }

    private ResultadoDataNascimentoLead resolver(
            IdempotenciaDeComandoDeLead.Reserva reserva, String chave, String hash, UUID leadId) {
        if (!OPERACAO.equals(reserva.operacao())
                || !reserva.hashDaRequisicao().equals(hash)
                || !leadId.equals(reserva.leadId())) {
            throw new ChaveIdempotenciaReutilizadaException(chave, OPERACAO, reserva.leadId());
        }
        if (reserva.respostaJson() == null) {
            throw new IllegalStateException("reserva de data de nascimento sem resposta concluida");
        }
        try {
            return json.readValue(reserva.respostaJson(), ResultadoDataNascimentoLead.class);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("resposta de data de nascimento ilegivel", erro);
        }
    }

    private String serializar(ResultadoDataNascimentoLead resposta) {
        try {
            return json.writeValueAsString(resposta);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("falha ao serializar resposta de data de nascimento", erro);
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

    public enum Situacao {
        APLICADO,
        IGNORADO_JA_PREENCHIDO
    }

    public record ResultadoDataNascimentoLead(UUID leadId, String dataNascimento, Situacao situacao) {}
}
