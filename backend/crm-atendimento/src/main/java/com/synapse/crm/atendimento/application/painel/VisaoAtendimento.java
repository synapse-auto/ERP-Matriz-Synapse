package com.synapse.crm.atendimento.application.painel;

/**
 * Os agrupamentos da tela de Atendimentos (RF-CRM-20/21).
 *
 * <p>O servidor decide, a partir do papel de quem pede, se cada visao e "meu" ou "de todos" — o
 * parametro nunca escolhe isso sozinho. Ver {@link ListarAtendimentosVisiveisUseCase}.
 */
public enum VisaoAtendimento {

    /** Sempre "meus": {@code EM_ATENDIMENTO} e {@code atendente_id} = quem pede, qualquer papel. */
    ATIVOS,

    /**
     * Ultima mensagem veio do lead, ainda sem resposta. "Meus" para atendente, "de todos" para
     * gestor/subgestor.
     */
    PENDENTES,

    /** Sem dono, com a IA — o grupo "Potenciais". Nunca restrito a um atendente. */
    POTENCIAIS,

    /** Tudo que a RLS deixa este usuario alcancar, sem filtro extra. */
    TODOS
}
