package com.synapse.crm.equipe.domain.usuario;

/** Quem mudou a presenca: o proprio usuario (clique) ou o sistema (conexao, desconexao, desativacao). */
public enum OrigemDaPresenca {
    MANUAL,
    SISTEMA
}
