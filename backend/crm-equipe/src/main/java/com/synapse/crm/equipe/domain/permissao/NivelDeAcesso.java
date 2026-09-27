package com.synapse.crm.equipe.domain.permissao;

/**
 * Nivel de um modulo para um perfil ou usuario. E preset e limite ao mesmo tempo:
 *
 * <ul>
 *   <li><b>limite</b>: acao cujo {@link Capacidade#nivelMinimo()} esta acima do nivel do modulo fica
 *       bloqueada, mesmo que o interruptor dela esteja ligado;
 *   <li><b>preset</b>: escolher um nivel na tela liga todas as acoes ate ele e desliga as acima. O
 *       preset e aplicado no rascunho; o servidor recebe niveis e interruptores explicitos e so
 *       valida a coerencia.
 * </ul>
 *
 * <p>A ordem das constantes e a ordem do nivel — {@link #compareTo} e o criterio.
 */
public enum NivelDeAcesso {
    SEM_ACESSO,
    VER,
    EDITAR,
    GERENCIAR;

    public boolean alcanca(NivelDeAcesso minimo) {
        return compareTo(minimo) >= 0;
    }

    public static NivelDeAcesso maior(NivelDeAcesso a, NivelDeAcesso b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static NivelDeAcesso menor(NivelDeAcesso a, NivelDeAcesso b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
