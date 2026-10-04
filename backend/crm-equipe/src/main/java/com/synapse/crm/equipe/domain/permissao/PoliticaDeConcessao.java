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
 *   <li>GESTOR e ADMINISTRADOR editam perfis e excecoes de SUBGESTOR, ATENDENTE e OPERADOR. Perfis fixos
 *       (GESTOR/ADMINISTRADOR) nao sao alvo de ninguem — o que tambem impede um GESTOR de receber
 *       operacao exclusiva de ADMINISTRADOR por esta via.
 *   <li>SUBGESTOR so alcanca ATENDENTE, e so com a delegacao concedida por um superior:
 *       {@link Capacidade#EQUIPE_EXCECOES_ATENDENTES} para as excecoes de cada atendente e
 *       {@link Capacidade#EQUIPE_PERFIS} para o perfil ATENDENTE. Nunca a si mesmo, ao proprio perfil,
 *       a outro subgestor ou a superior; nunca mexe em acao fora do conjunto delegavel; e nunca liga o
 *       que ele mesmo nao tem. Nivel de modulo e livre desde que o efeito dele respeite essas regras.
 *   <li>ATENDENTE nao concede nada.
 * </ul>
 */
public final class PoliticaDeConcessao {

    private static final Set<PapelUsuario> ALVOS_CONFIGURAVEIS = EnumSet.of(
            PapelUsuario.SUBGESTOR, PapelUsuario.ATENDENTE, PapelUsuario.OPERADOR);

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
    public static void exigirConcessaoNoPerfil(Ator ator, PapelUsuario perfil, ConfiguracaoDePermissoes atual,
            ConfiguracaoDePermissoes novo, Set<String> flags) {
        exigirAlcadaSobrePerfil(ator, perfil);
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return;
        }
        ConfiguracaoDePermissoes semExcecoes = ConfiguracaoDePermissoes.vazia();
        exigirDentroDaDelegacao(ator, atual, novo,
                PoliticaDePermissoes.calcular(perfil, atual, semExcecoes, flags),
                PoliticaDePermissoes.calcular(perfil, novo, semExcecoes, flags));
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
     * Confere a diferenca entre as excecoes atuais e as novas, sobre o perfil armazenado do alvo.
     * Superiores so passam pela alcada; o SUBGESTOR passa tambem pelo conjunto delegavel e pelo
     * proprio efetivo.
     */
    public static void exigirConcessao(Ator ator, UUID alvoId, PapelUsuario papelDoAlvo,
            ConfiguracaoDePermissoes perfilDoAlvo, ConfiguracaoDePermissoes atuais, ConfiguracaoDePermissoes novas,
            Set<String> flags) {
        exigirAlcadaSobre(ator, alvoId, papelDoAlvo);
        if (PoliticaDePermissoes.perfilFixo(ator.papel())) {
            return;
        }
        exigirDentroDaDelegacao(ator, atuais, novas,
                PoliticaDePermissoes.calcular(papelDoAlvo, perfilDoAlvo, atuais, flags),
                PoliticaDePermissoes.calcular(papelDoAlvo, perfilDoAlvo, novas, flags));
    }

    /**
     * So o conjunto delegavel muda, e nunca para ligado o que o proprio ator nao tem. Vale para o
     * interruptor gravado e para o efeito: nivel de modulo e dependencia tambem ligam e desligam
     * acoes, entao toda acao cujo efetivo muda passa pela mesma regra — e e isso que deixa o nivel
     * delegavel sem virar atalho para conceder o que o interruptor nao concederia.
     */
    private static void exigirDentroDaDelegacao(Ator ator, ConfiguracaoDePermissoes atuais,
            ConfiguracaoDePermissoes novas, PermissoesEfetivas antes, PermissoesEfetivas depois) {
        for (Capacidade c : Capacidade.values()) {
            if (c.estrutural()) {
                continue;
            }
            Boolean gravadoAntes = atuais.acao(c).orElse(null);
            Boolean gravadoDepois = novas.acao(c).orElse(null);
            if (!Objects.equals(gravadoAntes, gravadoDepois)) {
                exigirAlteravel(ator, c, Boolean.TRUE.equals(gravadoDepois));
            }
            if (antes.permite(c) != depois.permite(c)) {
                exigirAlteravel(ator, c, depois.permite(c));
            }
        }
    }

    private static void exigirAlteravel(Ator ator, Capacidade c, boolean paraLigado) {
        if (!c.delegavel()) {
            throw new ConcessaoNegadaException(Codigo.FORA_DO_CONJUNTO_DELEGAVEL, c.id());
        }
        if (paraLigado && !ator.efetivas().permite(c)) {
            throw new ConcessaoNegadaException(Codigo.ACIMA_DA_PROPRIA_PERMISSAO, c.id());
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
