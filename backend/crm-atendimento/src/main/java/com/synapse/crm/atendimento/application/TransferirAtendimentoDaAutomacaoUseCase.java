package com.synapse.crm.atendimento.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.equipe.application.disponibilidade.ListarAtendentesDisponiveisUseCase;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Escolhe o destino elegivel e delega a mudanca ao caso de uso canonico de transferencia.
 *
 * <p>O contrato nao recebe um usuario: disponibilidade e carga atual sao fatos do servidor, e nao
 * dados que a Automacao possa forjar para furar a distribuicao comercial.
 */
@Service
public class TransferirAtendimentoDaAutomacaoUseCase {

    /** Marcador de busca no log; a Automacao recebe 409 e, sem isto, a causa nao aparecia em lugar nenhum. */
    static final String MARCADOR_SEM_DESTINO = "[TRANSFERENCIA_SEM_DESTINO]";

    private static final Logger log = LoggerFactory.getLogger(TransferirAtendimentoDaAutomacaoUseCase.class);

    private final ListarAtendentesDisponiveisUseCase listarDisponiveis;
    private final AtendimentoRepositorio atendimentos;
    private final AtendenteParaTransferenciaRepositorio destinos;
    private final TransferirAtendimentoUseCase transferir;

    public TransferirAtendimentoDaAutomacaoUseCase(
            ListarAtendentesDisponiveisUseCase listarDisponiveis,
            AtendimentoRepositorio atendimentos,
            AtendenteParaTransferenciaRepositorio destinos,
            TransferirAtendimentoUseCase transferir) {
        this.listarDisponiveis = listarDisponiveis;
        this.atendimentos = atendimentos;
        this.destinos = destinos;
        this.transferir = transferir;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId) {
        atendimentos.bloquearDistribuicaoDaAutomacao();

        AtendenteDisponivelParaIa destino = listarDisponiveis.executar().stream()
                .findFirst()
                .orElseThrow(() -> semDestino(atendimentoId));

        return transferir.executarPelaAutomacao(atendimentoId, destino.usuarioId());
    }

    /**
     * Monta a recusa sem mudar a regra: registra o funil do rodizio (contagens apos cada filtro) e devolve o motivo
     * para o corpo do 409. O diagnostico e so leitura e nunca pode trocar o 409 por outro erro: se falhar, o motivo
     * vira {@code NAO_DETERMINADO} e o log diz que o diagnostico nao estava disponivel.
     */
    private NenhumAtendenteDisponivelException semDestino(UUID atendimentoId) {
        MotivoSemAtendente motivo = MotivoSemAtendente.NAO_DETERMINADO;
        try {
            DiagnosticoDoRodizio funil = listarDisponiveis.diagnosticar();
            motivo = MotivoSemAtendente.de(funil);
            var situacao = atendimentos.situacaoParaDiagnostico(atendimentoId);
            log.warn(
                    "{} atendimentoId={} motivo={} estrategia={} ativos={} comPapelPermitido={}"
                            + " disponiveisParaIa={} online={} semRegistroDeDisponibilidade={}"
                            + " atendimentoStatus={} atendimentoComAtendente={} leadComResponsavel={}",
                    MARCADOR_SEM_DESTINO,
                    atendimentoId,
                    motivo,
                    funil.estrategia(),
                    funil.ativos(),
                    funil.comPapelPermitido(),
                    funil.disponiveisParaIa(),
                    funil.online(),
                    funil.semRegistroDeDisponibilidade(),
                    situacao.map(AtendimentoRepositorio.SituacaoParaDiagnostico::status).orElse("DESCONHECIDO"),
                    situacao.map(AtendimentoRepositorio.SituacaoParaDiagnostico::comAtendente).orElse(false),
                    situacao.map(AtendimentoRepositorio.SituacaoParaDiagnostico::leadComResponsavel).orElse(false));
        } catch (RuntimeException erro) {
            log.warn(
                    "{} atendimentoId={} motivo={} diagnostico indisponivel: tipoErro={}",
                    MARCADOR_SEM_DESTINO,
                    atendimentoId,
                    motivo,
                    erro.getClass().getSimpleName());
        }
        return new NenhumAtendenteDisponivelException(motivo);
    }

    /** Transferência explícita: o destino é validado no banco e nunca aceito como UUID arbitrário. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId, UUID atendenteId) {
        destinos.ativoAtendente(atendenteId)
                .orElseThrow(() -> new AtendenteDestinoInvalidoException(atendenteId));
        // Destino explícito representa pedido do cliente e pode mover uma conversa humana aberta;
        // o rodízio (método acima) continua estrito a atendimentos EM_IA.
        return transferir.reatribuirPelaAutomacao(atendimentoId, atendenteId);
    }
}
