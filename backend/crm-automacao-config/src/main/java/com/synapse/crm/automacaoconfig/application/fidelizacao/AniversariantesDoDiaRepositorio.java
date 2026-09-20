package com.synapse.crm.automacaoconfig.application.fidelizacao;

import java.time.MonthDay;
import java.util.List;

import com.synapse.crm.automacaoconfig.domain.fidelizacao.LeadAniversariante;

/**
 * Porta para os aniversariantes do dia.
 *
 * <p>O lead pertence a crm-core e a data de nascimento nao e coluna: vive em
 * {@code lead.dados_customizados}, sob a chave reservada {@code data_nascimento} do
 * {@code campo_customizado} (tipo {@code DATA}). Por isso o adaptador mora em crm-app, onde a
 * composicao enxerga os dois lados — o modulo declara o que precisa e nao conhece o SQL.
 *
 * <p>Tenant sem o campo customizado cadastrado devolve lista vazia, nunca erro: ausencia de
 * configuracao e comportamento neutro, nao falha.
 */
public interface AniversariantesDoDiaRepositorio {

    /** Leads cuja data de nascimento cai neste dia e mes, em qualquer ano. */
    List<LeadAniversariante> doDia(MonthDay diaEMes);
}
