package com.synapse.crm.atendimento.application;

import java.util.Optional;
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
    private final VinculoDoLeadRepositorio vinculos;

    public TransferirAtendimentoDaAutomacaoUseCase(
            ListarAtendentesDisponiveisUseCase listarDisponiveis,
            AtendimentoRepositorio atendimentos,
            AtendenteParaTransferenciaRepositorio destinos,
            TransferirAtendimentoUseCase transferir,
            VinculoDoLeadRepositorio vinculos) {
        this.listarDisponiveis = listarDisponiveis;
        this.atendimentos = atendimentos;
        this.destinos = destinos;
        this.transferir = transferir;
        this.vinculos = vinculos;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId) {
        return executarNoSetor(atendimentoId, null);
    }

    /**
     * Rodizio, opcionalmente restrito a <em>um setor</em> (D3a). O chamador escolhe o setor, nunca o usuario. Antes
     * do rodizio, com a chave {@code ia.distribuicao.atendente_vinculado} ligada, o atendente vinculado ao lead
     * recebe direto se ainda estiver elegivel agora (ativo, online, disponivel para a IA, fora de setor que nao
     * recebe da triagem); senao vale o rodizio e quem recebe passa a ser o vinculado (listener de reacao).
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executarNoSetor(UUID atendimentoId, UUID setorId) {
        atendimentos.bloquearDistribuicaoDaAutomacao();

        AtendenteDisponivelParaIa destino = vinculadoElegivel(atendimentoId)
                .or(() -> primeiroDoRodizio(setorId))
                .orElseThrow(() -> semDestino(atendimentoId, setorId));

        return transferir.executarPelaAutomacao(atendimentoId, destino.usuarioId());
    }

    private Optional<AtendenteDisponivelParaIa> vinculadoElegivel(UUID atendimentoId) {
        return vinculos.vinculadoDoAtendimento(atendimentoId).flatMap(listarDisponiveis::disponivelPorId);
    }

    /** Sem setor pedido chama exatamente o que sempre chamou ({@code executar()}), sem argumento. */
    private Optional<AtendenteDisponivelParaIa> primeiroDoRodizio(UUID setorId) {
        return (setorId == null ? listarDisponiveis.executar() : listarDisponiveis.executar(setorId))
                .stream()
                .findFirst();
    }

    /**
     * Monta a recusa sem mudar a regra: registra o funil do rodizio (contagens apos cada filtro) e devolve o motivo
     * para o corpo do 409. O diagnostico e so leitura e nunca pode trocar o 409 por outro erro: se falhar, o motivo
     * vira {@code NAO_DETERMINADO} e o log diz que o diagnostico nao estava disponivel.
     */
    private NenhumAtendenteDisponivelException semDestino(UUID atendimentoId, UUID setorId) {
        MotivoSemAtendente motivo = MotivoSemAtendente.NAO_DETERMINADO;
        try {
            DiagnosticoDoRodizio funil =
                    setorId == null ? listarDisponiveis.diagnosticar() : listarDisponiveis.diagnosticar(setorId);
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
