package com.synapse.crm.atendimento.application.painel;

/**
 * Retrato de um pool de conexoes num instante (E225, PR 5). So numeros e o nome do pool: nada de usuario nem de consulta.
 *
 * @param ativas conexoes emprestadas agora
 * @param ociosas conexoes livres no pool
 * @param esperando threads aguardando uma conexao
 * @param total conexoes abertas (ativas + ociosas)
 * @param maximo tamanho maximo configurado do pool
 */
public record EstadoDoPool(String nome, int ativas, int ociosas, int esperando, int total, int maximo) {

    /** Todas as conexoes possiveis estao emprestadas: quem chegar agora espera. */
    public boolean saturado() {
        return maximo > 0 && ativas >= maximo;
    }
}
