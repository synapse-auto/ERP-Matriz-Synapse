package com.synapse.crm.equipe.application.disponibilidade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.setor.HabilitacaoDeSetores;
import com.synapse.crm.equipe.application.setor.SetorRepositorio;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;
import com.synapse.crm.equipe.domain.setor.Setor;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * {@code GET /internal/v1/atendentes/disponiveis} — roteamento da IA. So a Automacao chama isto: nao
 * e tela de gestor, e a lista que a IA consulta para decidir a quem entregar um lead sem dono.
 *
 * <p>Com a capacidade "setores" desligada tudo aqui e o rodizio de sempre. Ligada, quem esta num setor que nao
 * recebe da triagem (Diretoria) nunca e destino; e o chamador pode pedir <em>um setor</em> — nunca um usuario:
 * quem recebe continua sendo decisao do servidor.
 */
@Service
public class ListarAtendentesDisponiveisUseCase {

    private final AtendenteDisponivelRepositorio disponibilidade;
    private final HabilitacaoDeSetores setoresHabilitados;
    private final SetorRepositorio setores;

    public ListarAtendentesDisponiveisUseCase(
            AtendenteDisponivelRepositorio disponibilidade,
            HabilitacaoDeSetores setoresHabilitados,
            SetorRepositorio setores) {
        this.disponibilidade = disponibilidade;
        this.setoresHabilitados = setoresHabilitados;
        this.setores = setores;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<AtendenteDisponivelParaIa> executar() {
        return disponibilidade.listarDisponiveisParaIa(escopo(null));
    }

    /** Rodizio restrito a um setor. {@code setorId} invalido vira {@link SetorDeDistribuicaoInvalidoException}. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<AtendenteDisponivelParaIa> executar(UUID setorId) {
        return disponibilidade.listarDisponiveisParaIa(escopo(setorId));
    }

    /** Funil do rodizio (so contagens), para a Automacao explicar por que ele ficou vazio. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public DiagnosticoDoRodizio diagnosticar() {
        return disponibilidade.diagnosticar(escopo(null));
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public DiagnosticoDoRodizio diagnosticar(UUID setorId) {
        return disponibilidade.diagnosticar(escopo(setorId));
    }

    /**
     * O usuario, se ele esta elegivel agora pelas mesmas regras do rodizio (inclusive "setor que nao recebe da
     * triagem" quando a capacidade esta ligada). Vazio = ativo/online/disponivel falhou: quem chama cai no rodizio.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Optional<AtendenteDisponivelParaIa> disponivelPorId(UUID usuarioId) {
        return disponibilidade.disponivelPorId(usuarioId, setoresHabilitados.habilitada());
    }

    /**
     * Sem setor pedido e sem a capacidade ligada: {@link EscopoDoRodizio#GLOBAL}, a consulta original. Setor pedido
     * com a capacidade desligada e erro explicito, nao silencio: ignorar o pedido entregaria o lead a quem nao
     * e do setor.
     */
    private EscopoDoRodizio escopo(UUID setorId) {
        boolean ligada = setoresHabilitados.habilitada();
        if (setorId == null) {
            return ligada ? new EscopoDoRodizio(null, true) : EscopoDoRodizio.GLOBAL;
        }
        if (!ligada) {
            throw new SetorDeDistribuicaoInvalidoException(SetorDeDistribuicaoInvalidoException.Motivo.SETORES_DESLIGADOS);
        }
        Setor setor = setores.porId(setorId)
                .orElseThrow(() -> new SetorDeDistribuicaoInvalidoException(
                        SetorDeDistribuicaoInvalidoException.Motivo.SETOR_INEXISTENTE));
        if (!setor.ativo()) {
            throw new SetorDeDistribuicaoInvalidoException(SetorDeDistribuicaoInvalidoException.Motivo.SETOR_INATIVO);
        }
        if (!setor.recebeDaTriagem()) {
            throw new SetorDeDistribuicaoInvalidoException(
                    SetorDeDistribuicaoInvalidoException.Motivo.SETOR_NAO_RECEBE_DA_TRIAGEM);
        }
        return new EscopoDoRodizio(setorId, true);
    }
}
