package com.synapse.crm.equipe.application.disponibilidade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;

public interface AtendenteDisponivelRepositorio {

    default List<AtendenteDisponivelParaIa> listarDisponiveisParaIa() {
        return listarDisponiveisParaIa(EscopoDoRodizio.GLOBAL);
    }

    /** Mesmo rodízio, restrito ao escopo. {@link EscopoDoRodizio#GLOBAL} é exatamente a consulta de sempre. */
    List<AtendenteDisponivelParaIa> listarDisponiveisParaIa(EscopoDoRodizio escopo);

    /**
     * Quantos usuarios sobram depois de cada filtro de {@link #listarDisponiveisParaIa()}, para explicar uma lista
     * vazia. So le contagens; nao altera quem e elegivel nem a ordem.
     */
    default DiagnosticoDoRodizio diagnosticar() {
        return diagnosticar(EscopoDoRodizio.GLOBAL);
    }

    DiagnosticoDoRodizio diagnosticar(EscopoDoRodizio escopo);

    /**
     * Um usuario especifico, se ele passa nos MESMOS filtros do rodizio (ativo, papel que recebe, disponivel para a
     * IA, ONLINE) e, com {@code comSetores} (capacidade "setores" ligada), se pertence a um setor ativo que recebe da
     * triagem — quem nao tem setor nao e elegivel aqui (E231 (a)). Busca por chave primaria; usada para honrar o
     * atendente vinculado sem reordenar a fila. O rodizio em si continua tratando usuario sem setor como elegivel.
     */
    Optional<AtendenteDisponivelParaIa> disponivelPorId(UUID usuarioId, boolean comSetores);
}
