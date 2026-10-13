package com.synapse.crm.equipe.domain.setor;

import java.util.UUID;

/**
 * Setor da instância (Vendas, Suporte, Diretoria...): dado cadastrável, nunca enum.
 *
 * <p>{@code recebeDaTriagem} é falso para setores que não recebem lead distribuído pela IA (ex.: Diretoria); só a
 * distribuição lê esse campo. Setor não concede acesso a lead nenhum: a visibilidade (RN-CRM-01) continua sendo
 * decidida por papel e propriedade, nunca por setor.
 */
public record Setor(UUID id, String nome, boolean recebeDaTriagem, boolean ativo, int quantidadeDeUsuarios) {

    public static final int TAMANHO_MAXIMO_DO_NOME = 80;

    /** Nome sem espaços nas pontas; vazio ou longo demais é recusado antes de chegar ao banco. */
    public static String normalizarNome(String bruto) {
        String nome = bruto == null ? "" : bruto.strip();
        if (nome.isEmpty()) {
            throw new NomeDeSetorInvalidoException("O nome do setor é obrigatório.");
        }
        if (nome.length() > TAMANHO_MAXIMO_DO_NOME) {
            throw new NomeDeSetorInvalidoException(
                    "O nome do setor deve ter no máximo " + TAMANHO_MAXIMO_DO_NOME + " caracteres.");
        }
        return nome;
    }
}
