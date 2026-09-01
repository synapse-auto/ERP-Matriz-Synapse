package com.synapse.crm.sharedkernel.identidade;

/**
 * Papel do usuario no CRM (RF-CRM-02).
 *
 * <p>Mora no shared kernel, e nao em crm-equipe, porque a regra de visibilidade de lead
 * (crm-core) precisa do papel para decidir o que enxergar. Enum puro, sem framework: e o
 * vocabulario do negocio, nao um detalhe de persistencia.
 */
public enum PapelUsuario {
    ATENDENTE,
    SUBGESTOR,
    GESTOR,
    ADMINISTRADOR;

    /**
     * Quem enxerga a base inteira de leads.
     *
     * <p>A pergunta vive aqui, e nao espalhada em ifs pelos casos de uso, porque acrescentar um
     * papel no futuro tem de ser uma decisao tomada num lugar so. Os atendentes trabalham por
     * comissao e disputam leads: errar esta resposta e incidente comercial, nao bug de tela.
     */
    public boolean enxergaTodosOsLeads() {
        return this != ATENDENTE;
    }

    /**
     * Quem pode receber lead: entrar na fila da IA e ser destino de transferencia.
     *
     * <p>Separada de enxergaTodosOsLeads() de proposito. Sao duas perguntas diferentes sobre o mesmo
     * papel, e desde que o subgestor passou a atender elas deixaram de ter a mesma resposta: ele
     * recebe lead E continua enxergando a base inteira.
     */
    public boolean recebeAtendimento() {
        return this == ATENDENTE || this == SUBGESTOR;
    }
}
