package com.synapse.crm.atendimento.application.painel;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;

import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

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

    /** Tudo que a RLS deixa um usuario de gestao alcancar, sem filtro extra. */
    TODOS;

    /**
     * Confere a autorizacao da visao antes de qualquer adaptador de leitura.
     *
     * <p>A visao {@code TODOS} e uma capacidade de gestao, nao uma forma alternativa de pedir a
     * mesma lista. Centralizar a pergunta aqui impede que o endpoint legado, a inbox unificada e
     * a contagem acabem com regras diferentes quando surgir outro papel.
     */
    public void exigirAcesso(UsuarioAutenticado usuario) {
        if (!podeSerSolicitadaPor(usuario)) {
            throw new AccessDeniedException("a visao TODOS exige um papel de gestao");
        }
    }

    private boolean podeSerSolicitadaPor(UsuarioAutenticado usuario) {
        return this != TODOS || usuario.enxergaTodosOsLeads();
    }

    /** As visoes que a tela pode exibir para o papel autenticado. */
    public static List<VisaoAtendimento> disponiveisPara(UsuarioAutenticado usuario) {
        return Arrays.stream(values()).filter(visao -> visao.podeSerSolicitadaPor(usuario)).toList();
    }
}
