package com.synapse.crm.atendimento.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Fachada única dos comandos do n8n, com reserva transacional de Idempotency-Key. */
@Service
public class ComandosAutomacaoUseCase {

    private final ResponderAtendimentoDaAutomacaoUseCase responder;
    private final TransferirAtendimentoDaAutomacaoUseCase transferir;
    private final TransferirAtendimentoUseCase transferirAtendimento;
    private final IdempotenciaDeComandoAutomacao idempotencia;
    private final ObjectMapper json;

    public ComandosAutomacaoUseCase(
            ResponderAtendimentoDaAutomacaoUseCase responder,
            TransferirAtendimentoDaAutomacaoUseCase transferir,
            TransferirAtendimentoUseCase transferirAtendimento,
            IdempotenciaDeComandoAutomacao idempotencia,
            ObjectMapper json) {
        this.responder = responder;
        this.transferir = transferir;
        this.transferirAtendimento = transferirAtendimento;
        this.idempotencia = idempotencia;
        this.json = json;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resposta resposta(UUID atendimentoId, String chave, String conteudo) {
        return executar(
                chave,
                "RESPONDER",
                atendimentoId,
                conteudo,
                Resposta.class,
                () -> Resposta.de(responder.executar(atendimentoId, conteudo)));
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public TransferenciaResposta transferir(UUID atendimentoId, String chave, UUID atendenteId) {
        return executar(
                chave,
                "TRANSFERIR",
                atendimentoId,
                atendenteId.toString(),
                TransferenciaResposta.class,
                () -> TransferenciaResposta.de(transferir.executar(atendimentoId, atendenteId)));
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public TransferenciaResposta modoIa(UUID atendimentoId, String chave) {
        return executar(
                chave,
                "MODO_IA",
                atendimentoId,
                "",
                TransferenciaResposta.class,
                () -> TransferenciaResposta.de(transferirAtendimento.devolverParaIaPelaAutomacao(atendimentoId)));
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public TransferenciaResposta transferirProximoHumano(UUID atendimentoId, String chave) {
        return executar(
                chave,
                "TRANSFERIR_PROXIMO_HUMANO",
                atendimentoId,
                "",
                TransferenciaResposta.class,
                () -> TransferenciaResposta.de(transferir.executar(atendimentoId)));
    }

    private <T> T executar(
            String chave,
            String operacao,
            UUID atendimentoId,
            String requisicao,
            Class<T> tipoResposta,
            Supplier<T> efeito) {
        exigirChave(chave);
        String hash = hash(operacao + "\n" + atendimentoId + "\n" + requisicao);
        IdempotenciaDeComandoAutomacao.Reserva reserva = idempotencia.reservar(
                chave, operacao, atendimentoId, hash);
        if (!reserva.nova()) {
            if (!reserva.operacao().equals(operacao)
                    || !reserva.atendimentoId().equals(atendimentoId)
                    || !reserva.hashDaRequisicao().equals(hash)) {
                throw new ChaveIdempotenciaReutilizadaException(chave, operacao, atendimentoId);
            }
            if (reserva.respostaJson() == null) {
                throw new IllegalStateException("reserva de Idempotency-Key sem resposta concluida");
            }
            return desserializar(reserva.respostaJson(), tipoResposta);
        }

        T resultado = efeito.get();
        idempotencia.concluir(chave, serializar(resultado));
        return resultado;
    }

    private static void exigirChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new IdempotencyKeyInvalidaException();
        }
    }

    private String serializar(Object objeto) {
        try {
            return json.writeValueAsString(objeto);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("falha ao serializar resposta idempotente", erro);
        }
    }

    private <T> T desserializar(String bruto, Class<T> tipo) {
        try {
            return json.readValue(bruto, tipo);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("resposta idempotente ilegivel", erro);
        }
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(64);
            for (byte parte : digest) {
                hexadecimal.append(String.format("%02x", parte));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel", erro);
        }
    }

    public record Resposta(
            UUID atendimentoId, UUID mensagemId, String statusEntrega, Instant enviadoEm) {
        static Resposta de(ResponderAtendimentoDaAutomacaoUseCase.Resultado resultado) {
            return new Resposta(
                    resultado.atendimentoId(),
                    resultado.mensagemId(),
                    resultado.statusEntrega().name(),
                    resultado.enviadoEm());
        }
    }

    public record TransferenciaResposta(UUID atendimentoId, UUID atendenteId, String status) {
        static TransferenciaResposta de(Atendimento atendimento) {
            return new TransferenciaResposta(
                    atendimento.id(), atendimento.atendenteId(), atendimento.status().name());
        }
    }
}
