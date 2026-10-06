package com.synapse.crm.atendimento.application;

import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;

/**
 * Por que o rodizio da Automacao ficou sem destino. Vai no corpo do 409 (campo aditivo {@code motivo}) para a
 * Automacao e quem opera distinguirem "ninguem online" de "ninguem marcado para a IA", que pedem acoes diferentes.
 *
 * <p>A escolha olha o primeiro filtro do funil que zera, na mesma ordem em que a consulta os aplica.
 */
public enum MotivoSemAtendente {
    /** Nao ha usuario ativo com papel que recebe atendimento (ATENDENTE ou SUBGESTOR). */
    SEM_ATENDENTE_ELEGIVEL,
    /** Ha elegiveis, mas nenhum esta marcado como disponivel para a IA (ou nao tem linha de disponibilidade). */
    SEM_ATENDENTE_DISPONIVEL_PARA_IA,
    /** Ha marcados para a IA, mas nenhum com presenca ONLINE. */
    SEM_ATENDENTE_ONLINE,
    /** O funil nao zera em nenhum passo (corrida entre a consulta e o diagnostico) ou o diagnostico falhou. */
    NAO_DETERMINADO;

    static MotivoSemAtendente de(DiagnosticoDoRodizio funil) {
        if (funil.comPapelPermitido() == 0) {
            return SEM_ATENDENTE_ELEGIVEL;
        }
        if (funil.disponiveisParaIa() == 0) {
            return SEM_ATENDENTE_DISPONIVEL_PARA_IA;
        }
        if (funil.online() == 0) {
            return SEM_ATENDENTE_ONLINE;
        }
        return NAO_DETERMINADO;
    }
}
