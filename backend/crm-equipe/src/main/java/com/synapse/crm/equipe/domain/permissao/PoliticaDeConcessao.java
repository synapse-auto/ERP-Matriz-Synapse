package com.synapse.crm.equipe.domain.permissao;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException.Codigo;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Quem pode conceder o que a quem. Permissao de executar nao e permissao de conceder.
 *
 * <ul>
 *   <li>GESTOR e ADMINISTRADOR editam perfis e excecoes de SUBGESTOR e ATENDENTE. Perfis fixos
 *       (GESTOR/ADMINISTRADOR) nao sao alvo de ninguem — o que tambem impede um GESTOR de receber
 *       operacao exclusiva de ADMINISTRADOR por esta via.
 *   <li>SUBGESTOR so edita excecoes de ATENDENTE, e so com {@link Capacidade#EQUIPE_EXCECOES_ATENDENTES}
 *       concedida por um superior. Nunca a si mesmo, a outro subgestor ou a superior; nunca nivel de
 *       modulo; nunca acao fora do conjunto delegavel; e nunca liga o que ele mesmo nao tem.
 *   <li>ATENDENTE nao concede nada.
 * </ul>
 */
public final class PoliticaDeConcessao {

    private static final Set<PapelUsuario> ALVOS_CONFIGURAVEIS = EnumSet.of(PapelUsuario.SUBGESTOR, PapelUsuario.ATENDENTE);

    private PoliticaDeConcessao() {}

    /** Ator de uma operacao de concessao: quem e e o que efetivamente pode. */
    public record Ator(UUID id, PapelUsuario papel, PermissoesEfetivas efetivas) {
        public Ator {
            Objects.requireNonNull(id);
            Objects.requireNonNull(papel);
            Objects.requireNonNull(efetivas);
        }
    }

    public static void exigirEdicaoDePerfil(Ator ator) {
        if (!PoliticaDePermissoes.perfilFixo(ator.papel()) || !ator.efetivas().permite(Capacidade.EQUIPE_PERFIS)) {
            throw new ConcessaoNegadaException(Codigo.PERFIL_SO_PARA_SUPERIORES);
        }
    }

    /** Pode ao menos abrir as excecoes deste alvo para editar? */
    public static boolean podeEditarExcecoesDe(Ator ator, UUID alvoId, PapelUsuario papelDoAlvo) {
        try {
            exigirAlcadaSobre(ator, alvoId, papelDoAlvo);
            return true;
        } catch (ConcessaoNegadaException e) {
            return false;
        }
    }

    public static void exigirAlcadaSobre(Ator ator, UUID alvoId, PapelUsuario papelDoAlvo) {
        if (ator.id().equals(alvoId)) {
            throw new ConcessaoNegadaException(Codigo.ALVO_PROPRIO);
        }
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            if (!ALVOS_CONFIGURAVEIS.contains(papelDoAlvo)) {
                throw new ConcessaoNegadaException(Codigo.ALVO_FORA_DA_ALCADA);
            }
            return;
        }
        if (ator.papel() != PapelUsuario.SUBGESTOR) {
            throw new ConcessaoNegadaException(Codigo.SEM_DELEGACAO);
        }
        if (!ator.efetivas().permite(Capacidade.EQUIPE_EXCECOES_ATENDENTES)) {
            throw new ConcessaoNegadaException(Codigo.SEM_DELEGACAO);
        }
        if (papelDoAlvo != PapelUsuario.ATENDENTE) {
            throw new ConcessaoNegadaException(Codigo.ALVO_FORA_DA_ALCADA);
        }
    }

    /**
     * Confere a diferenca entre as excecoes atuais e as novas. Superiores so passam pela alcada; o
     * SUBGESTOR passa tambem pelo conjunto delegavel e pelo proprio efetivo.
     */
    public static void exigirConcessao(
            Ator ator, UUID alvoId, PapelUsuario papelDoAlvo,
            ConfiguracaoDePermissoes atuais, ConfiguracaoDePermissoes novas) {
        exigirAlcadaSobre(ator, alvoId, papelDoAlvo);
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return;
        }
        for (Modulo m : Modulo.values()) {
            if (!Objects.equals(atuais.nivel(m).orElse(null), novas.nivel(m).orElse(null))) {
                throw new ConcessaoNegadaException(Codigo.NIVEL_NAO_DELEGAVEL, ConfiguracaoDePermissoes.chaveDeNivel(m));
            }
        }
        for (Capacidade c : Capacidade.values()) {
            Boolean antes = atuais.acao(c).orElse(null);
            Boolean depois = novas.acao(c).orElse(null);
            if (Objects.equals(antes, depois)) {
                continue;
            }
            if (!c.delegavel()) {
                throw new ConcessaoNegadaException(Codigo.FORA_DO_CONJUNTO_DELEGAVEL, c.id());
            }
            if (Boolean.TRUE.equals(depois) && !ator.efetivas().permite(c)) {
                throw new ConcessaoNegadaException(Codigo.ACIMA_DA_PROPRIA_PERMISSAO, c.id());
            }
        }
    }

    /** O que o ator pode mexer numa excecao de ATENDENTE (a tela usa para travar interruptores). */
    public static boolean podeAlterar(Ator ator, Capacidade c, boolean paraLigado) {
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return true;
        }
        return c.delegavel() && (!paraLigado || ator.efetivas().permite(c));
    }
}
