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
 *   <li>SUBGESTOR so alcanca ATENDENTE, e so com a delegacao concedida por um superior:
 *       {@link Capacidade#EQUIPE_EXCECOES_ATENDENTES} para as excecoes de cada atendente e
 *       {@link Capacidade#EQUIPE_PERFIS} para o perfil ATENDENTE. Nunca a si mesmo, ao proprio perfil,
 *       a outro subgestor ou a superior; nunca nivel de modulo; nunca acao fora do conjunto delegavel;
 *       e nunca liga o que ele mesmo nao tem.
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

    /** Pode ao menos abrir este perfil para editar? Perfil fixo nunca e editavel. */
    public static boolean podeEditarPerfil(Ator ator, PapelUsuario perfil) {
        if (PoliticaDePermissoes.perfilFixo(perfil)) {
            return false;
        }
        try {
            exigirAlcadaSobrePerfil(ator, perfil);
            return true;
        } catch (ConcessaoNegadaException e) {
            return false;
        }
    }

    /**
     * Superiores alcancam qualquer perfil (o fixo e recusado adiante como payload invalido). O
     * SUBGESTOR delegado alcanca so o perfil ATENDENTE: editar o proprio perfil seria autoelevacao.
     */
    public static void exigirAlcadaSobrePerfil(Ator ator, PapelUsuario perfil) {
        if (!ator.efetivas().permite(Capacidade.EQUIPE_PERFIS)) {
            throw new ConcessaoNegadaException(Codigo.SEM_DELEGACAO);
        }
        if (!PoliticaDePermissoes.perfilFixo(ator.papel()) && perfil != PapelUsuario.ATENDENTE) {
            throw new ConcessaoNegadaException(Codigo.ALVO_FORA_DA_ALCADA);
        }
    }

    /**
     * Confere a diferenca entre o perfil atual e o novo, ambos completos (chave nao salva vale o
     * padrao). Superiores so passam pela alcada; o SUBGESTOR passa pelas mesmas regras das excecoes
     * que ele concede — o perfil e um alvo maior, nao uma alcada maior.
     */
    public static void exigirConcessaoNoPerfil(
            Ator ator, PapelUsuario perfil, ConfiguracaoDePermissoes atual, ConfiguracaoDePermissoes novo) {
        exigirAlcadaSobrePerfil(ator, perfil);
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return;
        }
        exigirDentroDaDelegacao(ator, atual, novo);
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
        exigirDentroDaDelegacao(ator, atuais, novas);
    }

    /** Nunca nivel de modulo; so o conjunto delegavel; e nunca liga o que o proprio ator nao tem. */
    private static void exigirDentroDaDelegacao(
            Ator ator, ConfiguracaoDePermissoes atuais, ConfiguracaoDePermissoes novas) {
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

    /**
     * O que o ator pode mexer numa excecao ou no perfil de ATENDENTE (a tela usa para travar
     * interruptores).
     */
    public static boolean podeAlterar(Ator ator, Capacidade c, boolean paraLigado) {
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return true;
        }
        return c.delegavel() && (!paraLigado || ator.efetivas().permite(c));
    }

    /**
     * Copiar de outro papel transmitiria ao destino o que o SUBGESTOR delegado nao alcanca: ele so
     * copia de ATENDENTE. Superiores copiam de qualquer origem configuravel.
     */
    public static boolean origemDeCopiaNaAlcada(Ator ator, PapelUsuario papelDaOrigem) {
        return PoliticaDePermissoes.perfilFixo(ator.papel()) || papelDaOrigem == PapelUsuario.ATENDENTE;
    }
}
