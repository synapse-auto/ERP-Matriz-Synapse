package com.synapse.crm.atendimento.application.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.IdempotenciaDeComandoAutomacao;
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.core.application.lead.AutomacaoEv05LeadRepositorio;
import com.synapse.crm.core.application.lead.EscritaEv05ObsoletaException;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Casos de uso do EV-05; o n8n conhece apenas este contrato e nunca o banco. */
@Service
public class Ev05LeadUseCase {
    private static final String OPERACAO_RESUMO = "EV05_RESUMO";
    private static final String OPERACAO_PREENCHIMENTO = "EV05_PREENCHIMENTO";

    private final AutomacaoEv05LeadRepositorio leads;
    private final AtendimentosEmAndamentoRepositorio atendimentos;
    private final IdempotenciaDeComandoAutomacao idempotencia;
    private final ObjectMapper json;
    private final Clock relogio;
    private final int resumoMaximo;

    public Ev05LeadUseCase(
            AutomacaoEv05LeadRepositorio leads,
            AtendimentosEmAndamentoRepositorio atendimentos,
            IdempotenciaDeComandoAutomacao idempotencia,
            ObjectMapper json,
            Clock relogio,
            @Value("${synapse.automacao.resumo-ia-tamanho-maximo}") int resumoMaximo) {
        this.leads = leads;
        this.atendimentos = atendimentos;
        this.idempotencia = idempotencia;
        this.json = json;
        this.relogio = relogio;
        this.resumoMaximo = resumoMaximo;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public EstadoResumo estadoResumo(UUID leadId) {
        exigirAtendimentoElegivel(leadId);
        var estado = leads.resumo(leadId);
        boolean contextoNovo = estado.ultimaInteracaoEm() != null
                && (estado.atualizadoEm() == null || estado.ultimaInteracaoEm().isAfter(estado.atualizadoEm()));
        return new EstadoResumo(estado.leadId(), estado.existe(), estado.atualizadoEm(), contextoNovo);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public EstadoPreenchimento estadoPreenchimento(UUID leadId) {
        exigirAtendimentoElegivel(leadId);
        var estado = leads.preenchimento(leadId);
        return new EstadoPreenchimento(
                estado.leadId(),
                Campo.de(estado.email()),
                Campo.de(estado.cpf()),
                Campo.de(estado.empresa()),
                Campo.de(estado.localizacao()),
                estado.ultimaAvaliacaoEm());
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ResultadoResumo gravarResumo(
            UUID leadId, String resumo, Instant contextoGeradoEm, String chave) {
        exigirChave(chave);
        String normalizado = normalizarResumo(resumo);
        String hash = hash(OPERACAO_RESUMO + "\n" + leadId + "\n" + normalizado + "\n" + contextoGeradoEm);
        var existente = idempotencia.buscar(chave);
        if (existente.isPresent()) {
            var atendimentoAtual = atendimentos.porLeadEmAtendimento(leadId);
            return resolver(
                    existente.get(),
                    chave,
                    OPERACAO_RESUMO,
                    hash,
                    atendimentoAtual.map(AtendimentosEmAndamentoRepositorio.Item::atendimentoId).orElse(null),
                    ResultadoResumo.class);
        }
        var atendimento = atendimentos.porLeadEmAtendimento(leadId)
                .orElseThrow(() -> new Ev05LeadSemAtendimentoException(leadId));
        var reserva = idempotencia.reservar(chave, OPERACAO_RESUMO, atendimento.atendimentoId(), hash);
        if (!reserva.nova()) {
            return resolver(
                    reserva, chave, OPERACAO_RESUMO, hash, atendimento.atendimentoId(), ResultadoResumo.class);
        }
        Instant agora = Instant.now(relogio);
        try {
            var escrito = leads.gravarResumo(leadId, normalizado, contextoGeradoEm, agora);
            var resultado = new ResultadoResumo(escrito.leadId(), escrito.atualizadoEm(), true);
            idempotencia.concluir(chave, serializar(resultado));
            return resultado;
        } catch (EscritaEv05ObsoletaException erro) {
            throw erro;
        }
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ResultadoPreenchimento preencher(
            UUID leadId,
            String email,
            String cpf,
            String empresa,
            String localizacao,
            String chave) {
        exigirChave(chave);
        Valores valores = normalizarValores(email, cpf, empresa, localizacao);
        String hash = hash(OPERACAO_PREENCHIMENTO + "\n" + leadId + "\n" + valores);
        var existente = idempotencia.buscar(chave);
        if (existente.isPresent()) {
            var atendimentoAtual = atendimentos.porLeadEmAtendimento(leadId);
            return resolver(
                    existente.get(),
                    chave,
                    OPERACAO_PREENCHIMENTO,
                    hash,
                    atendimentoAtual.map(AtendimentosEmAndamentoRepositorio.Item::atendimentoId).orElse(null),
                    ResultadoPreenchimento.class);
        }
        var atendimento = atendimentos.porLeadEmAtendimento(leadId)
                .orElseThrow(() -> new Ev05LeadSemAtendimentoException(leadId));
        var reserva = idempotencia.reservar(chave, OPERACAO_PREENCHIMENTO, atendimento.atendimentoId(), hash);
        if (!reserva.nova()) {
            return resolver(
                    reserva,
                    chave,
                    OPERACAO_PREENCHIMENTO,
                    hash,
                    atendimento.atendimentoId(),
                    ResultadoPreenchimento.class);
        }
        Instant agora = Instant.now(relogio);
        var escrito = leads.aplicarPreenchimento(
                leadId, valores.email(), valores.cpf(), valores.empresa(), valores.localizacao(), agora,
                valores.invalidos());
        var resultado = ResultadoPreenchimento.de(escrito);
        idempotencia.concluir(chave, serializar(resultado));
        return resultado;
    }

    private String normalizarResumo(String resumo) {
        if (resumo == null || resumo.isBlank()) {
            throw new Ev05ResumoInvalidoException("resumo vazio");
        }
        String valor = resumo.trim();
        if (valor.length() > resumoMaximo) {
            throw new Ev05ResumoInvalidoException("resumo excede o limite da instancia");
        }
        return valor;
    }

    private static Valores normalizarValores(String email, String cpf, String empresa, String localizacao) {
        String emailNormalizado = texto(email, 200);
        if (emailNormalizado != null) {
            emailNormalizado = emailNormalizado.toLowerCase(Locale.ROOT);
        }
        String cpfNormalizado = cpf == null || cpf.isBlank() ? null : cpf.replaceAll("\\D", "");
        String empresaNormalizada = texto(empresa, 150);
        String localizacaoNormalizada = texto(localizacao, 200);
        Set<String> invalidos = new HashSet<>();
        if (email != null && !email.isBlank() && emailNormalizado == null) {
            invalidos.add("email");
        } else if (emailNormalizado != null && !emailNormalizado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            invalidos.add("email");
        }
        if (cpfNormalizado != null && !cpfValido(cpfNormalizado)) {
            invalidos.add("cpf");
        }
        if (empresa != null && !empresa.isBlank() && empresaNormalizada == null) {
            invalidos.add("empresa");
        }
        if (localizacao != null && !localizacao.isBlank() && localizacaoNormalizada == null) {
            invalidos.add("localizacao");
        }
        // Uma entrada inválida não pode aplicar outro campo parcialmente. A avaliação, porém,
        // continua sendo registrada para que o próximo ciclo respeite o intervalo configurado.
        if (!invalidos.isEmpty()) {
            if (emailNormalizado != null) invalidos.add("email");
            if (cpfNormalizado != null) invalidos.add("cpf");
            if (empresaNormalizada != null) invalidos.add("empresa");
            if (localizacaoNormalizada != null) invalidos.add("localizacao");
        }
        return new Valores(emailNormalizado, cpfNormalizado, empresaNormalizada, localizacaoNormalizada, Set.copyOf(invalidos));
    }

    private static String texto(String valor, int maximo) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim().replaceAll("\\s+", " ");
        return normalizado.length() > maximo ? null : normalizado;
    }

    private static boolean cpfValido(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) return false;
        int soma = 0;
        for (int i = 0; i < 9; i++) soma += (cpf.charAt(i) - '0') * (10 - i);
        int d1 = (soma * 10) % 11;
        if (d1 == 10) d1 = 0;
        if (d1 != cpf.charAt(9) - '0') return false;
        soma = 0;
        for (int i = 0; i < 10; i++) soma += (cpf.charAt(i) - '0') * (11 - i);
        int d2 = (soma * 10) % 11;
        if (d2 == 10) d2 = 0;
        return d2 == cpf.charAt(10) - '0';
    }

    private static void exigirChave(String chave) {
        if (chave == null || chave.isBlank()) throw new IdempotencyKeyInvalidaException();
    }

    private void exigirAtendimentoElegivel(UUID leadId) {
        atendimentos.porLeadEmAtendimento(leadId)
                .orElseThrow(() -> new Ev05LeadSemAtendimentoException(leadId));
    }

    private String serializar(Object resposta) {
        try {
            return json.writeValueAsString(resposta);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("falha ao serializar resposta EV-05", erro);
        }
    }

    private <T> T resolver(
            IdempotenciaDeComandoAutomacao.Reserva reserva,
            String chave,
            String operacao,
            String hash,
            UUID atendimentoAtual,
            Class<T> tipo) {
        if (!operacao.equals(reserva.operacao())
                || !reserva.hashDaRequisicao().equals(hash)
                || (atendimentoAtual != null && !atendimentoAtual.equals(reserva.atendimentoId()))) {
            throw new ChaveIdempotenciaReutilizadaException(chave, operacao, reserva.atendimentoId());
        }
        if (reserva.respostaJson() == null) throw new IllegalStateException("reserva EV-05 sem resposta concluida");
        try {
            return json.readValue(reserva.respostaJson(), tipo);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("resposta EV-05 ilegivel", erro);
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

    private record Valores(String email, String cpf, String empresa, String localizacao, Set<String> invalidos) {}

    public record EstadoResumo(UUID leadId, boolean existe, Instant atualizadoEm, boolean contextoNovoSuficiente) {}

    public record Campo(boolean preenchido, String origem) {
        static Campo de(AutomacaoEv05LeadRepositorio.Campo campo) {
            return new Campo(campo.preenchido(), campo.origem());
        }
    }

    public record EstadoPreenchimento(
            UUID leadId, Campo email, Campo cpf, Campo empresa, Campo localizacao, Instant ultimaAvaliacaoEm) {}

    public record ResultadoResumo(UUID leadId, Instant atualizadoEm, boolean aplicado) {}

    public record ResultadoPreenchimento(
            UUID leadId,
            AutomacaoEv05LeadRepositorio.ResultadoCampo email,
            AutomacaoEv05LeadRepositorio.ResultadoCampo cpf,
            AutomacaoEv05LeadRepositorio.ResultadoCampo empresa,
            AutomacaoEv05LeadRepositorio.ResultadoCampo localizacao,
            Instant avaliadoEm) {
        static ResultadoPreenchimento de(AutomacaoEv05LeadRepositorio.EscritaPreenchimento valor) {
            return new ResultadoPreenchimento(
                    valor.leadId(), valor.email(), valor.cpf(), valor.empresa(), valor.localizacao(), valor.avaliadoEm());
        }
    }
}
