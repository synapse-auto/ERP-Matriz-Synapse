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

import com.synapse.crm.atendimento.application.informacoeschatbot.RegistrarInformacoesDoChatbotUseCase;
import com.synapse.crm.atendimento.application.internal.CriarLembreteDaAutomacaoUseCase;
import com.synapse.crm.atendimento.application.origem.OrigemDaMensagem;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Fachada única dos comandos do n8n, com reserva transacional de Idempotency-Key. */
@Service
public class ComandosAutomacaoUseCase {

    private final ResponderAtendimentoDaAutomacaoUseCase responder;
    private final TransferirAtendimentoDaAutomacaoUseCase transferir;
    private final TransferirAtendimentoUseCase transferirAtendimento;
    private final FinalizarAtendimentoUseCase finalizarAtendimento;
    private final CriarLembreteDaAutomacaoUseCase criarLembrete;
    private final ClassificarNegociacaoDoAtendimentoUseCase classificarNegociacao;
    private final RegistrarInformacoesDoChatbotUseCase informacoesDoChatbot;
    private final IdempotenciaDeComandoAutomacao idempotencia;
    private final ObjectMapper json;

    public ComandosAutomacaoUseCase(
            ResponderAtendimentoDaAutomacaoUseCase responder,
            TransferirAtendimentoDaAutomacaoUseCase transferir,
            TransferirAtendimentoUseCase transferirAtendimento,
            FinalizarAtendimentoUseCase finalizarAtendimento,
            CriarLembreteDaAutomacaoUseCase criarLembrete,
            ClassificarNegociacaoDoAtendimentoUseCase classificarNegociacao,
            RegistrarInformacoesDoChatbotUseCase informacoesDoChatbot,
            IdempotenciaDeComandoAutomacao idempotencia,
            ObjectMapper json) {
        this.responder = responder;
        this.transferir = transferir;
        this.transferirAtendimento = transferirAtendimento;
        this.finalizarAtendimento = finalizarAtendimento;
        this.criarLembrete = criarLembrete;
        this.classificarNegociacao = classificarNegociacao;
        this.informacoesDoChatbot = informacoesDoChatbot;
        this.idempotencia = idempotencia;
        this.json = json;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public RespostaComandoAutomacao resposta(UUID atendimentoId, String chave, String conteudo) {
        return resposta(atendimentoId, chave, conteudo, OrigemDaMensagem.declaradaPelaAutomacao(null, null, null));
    }

    /**
     * A origem nao entra no hash da Idempotency-Key: a mesma resposta repetida por outra execucao do
     * n8n (outro execucaoId) continua sendo a mesma operacao.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public RespostaComandoAutomacao resposta(
            UUID atendimentoId, String chave, String conteudo, OrigemDaMensagem origem) {
        return executar(
                chave,
                "RESPONDER",
                atendimentoId,
                conteudo,
                RespostaComandoAutomacao.class,
                () -> RespostaComandoAutomacao.de(responder.executar(atendimentoId, conteudo, origem)));
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

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public FinalizacaoResposta finalizar(UUID atendimentoId, String chave) {
        return executar(
                chave,
                "FINALIZAR",
                atendimentoId,
                "",
                FinalizacaoResposta.class,
                () -> finalizarAtendimento.validarPelaAutomacao(atendimentoId),
                () -> FinalizacaoResposta.de(finalizarAtendimento.executarPelaAutomacao(atendimentoId)));
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ClassificarNegociacaoDoAtendimentoUseCase.ClassificacaoNegociacaoResposta classificarNegociacao(
            UUID atendimentoId, String chave, boolean emNegociacao) {
        return executar(
                chave,
                "CLASSIFICAR_NEGOCIACAO",
                atendimentoId,
                Boolean.toString(emNegociacao),
                ClassificarNegociacaoDoAtendimentoUseCase.ClassificacaoNegociacaoResposta.class,
                () -> classificarNegociacao.validar(atendimentoId),
                () -> classificarNegociacao.executar(atendimentoId, emNegociacao));
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public LembreteResposta criarLembrete(
            UUID atendimentoId, String chave, String texto, Instant dataHora) {
        return executar(
                chave,
                "CRIAR_LEMBRETE",
                atendimentoId,
                texto.trim() + "\n" + dataHora,
                LembreteResposta.class,
                () -> LembreteResposta.de(
                        atendimentoId, criarLembrete.executar(atendimentoId, texto, dataHora)));
    }

    /**
     * Card interno com o que o chatbot coletou antes da transferencia. Ordem: chave (400) → replay de
     * operacao ja concluida (resposta original, mesmo com a flag desligada ou o limite alterado) →
     * so para operacao nova: flag (409), conteudo (422) e destino. O conteudo normalizado entra no
     * hash, entao o mesmo texto reenviado com outra quebra de linha continua sendo o mesmo pedido.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public InformacoesDoChatbotResposta registrarInformacoesDoChatbot(
            UUID atendimentoId, String chave, String conteudo) {
        // O hash usa so a normalizacao: o replay de uma operacao concluida precisa do mesmo hash de
        // quando foi aceita, independentemente da flag e do limite de hoje.
        String normalizado = informacoesDoChatbot.normalizarParaIdempotencia(conteudo);
        return executar(
                chave,
                "REGISTRAR_INFORMACOES_CHATBOT",
                atendimentoId,
                normalizado,
                InformacoesDoChatbotResposta.class,
                // So roda para operacao NOVA (o replay ja foi devolvido antes): configuracao atual
                // valida o que ainda nao aconteceu e nunca invalida o que ja foi concluido.
                () -> {
                    informacoesDoChatbot.exigirHabilitada();
                    informacoesDoChatbot.validarConteudo(conteudo);
                    informacoesDoChatbot.validarDestino(atendimentoId);
                },
                () -> InformacoesDoChatbotResposta.de(
                        informacoesDoChatbot.executar(atendimentoId, chave, normalizado)));
    }

    private <T> T executar(
            String chave,
            String operacao,
            UUID atendimentoId,
            String requisicao,
            Class<T> tipoResposta,
            Supplier<T> efeito) {
        return executar(chave, operacao, atendimentoId, requisicao, tipoResposta, () -> {}, efeito);
    }

    private <T> T executar(
            String chave,
            String operacao,
            UUID atendimentoId,
            String requisicao,
            Class<T> tipoResposta,
            Runnable validarAntesDaReserva,
            Supplier<T> efeito) {
        exigirChave(chave);
        String hash = hash(operacao + "\n" + atendimentoId + "\n" + requisicao);
        var existente = idempotencia.buscar(chave);
        if (existente.isPresent()) {
            return resolverReserva(existente.get(), chave, operacao, atendimentoId, hash, tipoResposta);
        }

        // A tabela de idempotencia referencia atendimento por FK. Validar antes da reserva evita
        // transformar um atendimento inexistente em 500 por violacao de integridade, sem perder o
        // replay: reservas existentes foram resolvidas acima antes desta validacao. Se outra
        // requisicao com a mesma chave concluir enquanto aguardamos o lock do atendimento, ela
        // pode ter tornado a validacao um conflito; nesse caso, o replay que apareceu no intervalo
        // ainda tem precedencia sobre o erro de estado.
        try {
            validarAntesDaReserva.run();
        } catch (RuntimeException erro) {
            var corrida = idempotencia.buscar(chave);
            if (corrida.isPresent()) {
                return resolverReserva(corrida.get(), chave, operacao, atendimentoId, hash, tipoResposta);
            }
            throw erro;
        }
        IdempotenciaDeComandoAutomacao.Reserva reserva = idempotencia.reservar(
                chave, operacao, atendimentoId, hash);
        if (!reserva.nova()) {
            return resolverReserva(reserva, chave, operacao, atendimentoId, hash, tipoResposta);
        }

        T resultado = efeito.get();
        idempotencia.concluir(chave, serializar(resultado));
        return resultado;
    }

    private <T> T resolverReserva(
            IdempotenciaDeComandoAutomacao.Reserva reserva,
            String chave,
            String operacao,
            UUID atendimentoId,
            String hash,
            Class<T> tipoResposta) {
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

    public record RespostaComandoAutomacao(
            UUID atendimentoId, UUID mensagemId, String statusEntrega, Instant enviadoEm) {
        static RespostaComandoAutomacao de(ResponderAtendimentoDaAutomacaoUseCase.Resultado resultado) {
            return new RespostaComandoAutomacao(
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

    /** Resumo sem historico ou dados de contato, suficiente para o workflow confirmar a transicao. */
    public record FinalizacaoResposta(
            UUID atendimentoId,
            UUID leadId,
            String status,
            Instant finalizadoEm,
            String origem) {

        static FinalizacaoResposta de(Atendimento atendimento) {
            return new FinalizacaoResposta(
                    atendimento.id(),
                    atendimento.leadId(),
                    atendimento.status().name(),
                    atendimento.finalizadoEm(),
                    "AUTOMACAO");
        }
    }

    /** So ids e instante: o texto recebido nao volta na resposta nem fica no log. */
    public record InformacoesDoChatbotResposta(UUID id, UUID atendimentoId, Instant registradoEm) {

        static InformacoesDoChatbotResposta de(RegistrarInformacoesDoChatbotUseCase.Resultado resultado) {
            return new InformacoesDoChatbotResposta(
                    resultado.id(), resultado.atendimentoId(), resultado.registradoEm());
        }
    }

    public record LembreteResposta(
            UUID id,
            UUID atendimentoId,
            UUID leadId,
            UUID atendenteId,
            String texto,
            Instant dataHora,
            boolean origemAutomatica,
            String status) {

        static LembreteResposta de(UUID atendimentoId, Lembrete lembrete) {
            return new LembreteResposta(
                    lembrete.id(),
                    atendimentoId,
                    lembrete.leadId(),
                    lembrete.atendenteId(),
                    lembrete.texto(),
                    lembrete.dataHora(),
                    lembrete.origemAutomatica(),
                    lembrete.status().name());
        }
    }
}
