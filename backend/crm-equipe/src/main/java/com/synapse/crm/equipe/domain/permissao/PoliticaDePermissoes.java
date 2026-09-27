package com.synapse.crm.equipe.domain.permissao;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.synapse.crm.equipe.domain.permissao.EstadoDaCapacidade.Motivo;
import com.synapse.crm.equipe.domain.permissao.EstadoDaCapacidade.Origem;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Regras puras de heranca, calculo efetivo e validacao. Sem Spring, sem banco: tudo que decide
 * "pode ou nao pode" passa por aqui, e so aqui.
 *
 * <p>Ordem do calculo de uma acao, do mais forte ao mais fraco — nenhuma camada de baixo supera uma
 * de cima:
 *
 * <ol>
 *   <li>teto do papel (estrutural, copia do {@code hasAnyRole} anterior);
 *   <li>feature flag do modulo;
 *   <li>perfil fixo (GESTOR/ADMINISTRADOR) = tudo que o teto permite;
 *   <li>nivel do modulo (excecao ou perfil) contra o nivel minimo da acao;
 *   <li>interruptor (excecao ou perfil);
 *   <li>dependencias.
 * </ol>
 */
public final class PoliticaDePermissoes {

    private PoliticaDePermissoes() {}

    public static boolean perfilFixo(PapelUsuario papel) {
        return papel == PapelUsuario.GESTOR || papel == PapelUsuario.ADMINISTRADOR;
    }

    public static boolean perfilConfiguravel(PapelUsuario papel) {
        return !perfilFixo(papel);
    }

    public static boolean disponivel(Modulo modulo, Set<String> flagsHabilitadas) {
        return modulo.flag() == null || flagsHabilitadas.contains(modulo.flag());
    }

    /** Maior nivel com efeito para o papel: acima dele so haveria acoes fora do teto. */
    public static NivelDeAcesso nivelMaximo(PapelUsuario papel, Modulo modulo) {
        NivelDeAcesso maximo = modulo.nivelMinimoPermitido();
        for (Capacidade c : Capacidade.values()) {
            if (c.modulo() == modulo && c.noTetoDe(papel)) {
                maximo = NivelDeAcesso.maior(maximo, c.nivelMinimo());
            }
        }
        return maximo;
    }

    /**
     * Nivel padrao: o menor que ainda libera tudo que o papel ja fazia antes desta etapa. E o que
     * preserva o acesso operacional atual numa instancia que nunca salvou um perfil.
     */
    public static NivelDeAcesso nivelPadrao(PapelUsuario papel, Modulo modulo) {
        if (perfilFixo(papel)) {
            return nivelMaximo(papel, modulo);
        }
        NivelDeAcesso nivel = modulo.nivelMinimoPermitido();
        for (Capacidade c : Capacidade.values()) {
            if (c.modulo() == modulo && c.permitidaPorPadraoPara(papel)) {
                nivel = NivelDeAcesso.maior(nivel, c.nivelMinimo());
            }
        }
        return nivel;
    }

    public static ConfiguracaoDePermissoes padrao(PapelUsuario papel) {
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        for (Modulo m : Modulo.values()) {
            niveis.put(m, nivelPadrao(papel, m));
        }
        Map<Capacidade, Boolean> acoes = new EnumMap<>(Capacidade.class);
        for (Capacidade c : Capacidade.values()) {
            if (!c.estrutural() && c.noTetoDe(papel)) {
                acoes.put(c, c.permitidaPorPadraoPara(papel));
            }
        }
        return new ConfiguracaoDePermissoes(niveis, acoes);
    }

    /** Perfil armazenado sobre o padrao do catalogo: chave nao salva ainda vale o padrao. */
    public static ConfiguracaoDePermissoes perfilCompleto(PapelUsuario papel, ConfiguracaoDePermissoes armazenado) {
        ConfiguracaoDePermissoes padrao = padrao(papel);
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(padrao.niveis());
        Map<Capacidade, Boolean> acoes = new EnumMap<>(padrao.acoes());
        if (perfilConfiguravel(papel)) {
            armazenado.niveis().forEach(niveis::put);
            armazenado.acoes().forEach((c, v) -> {
                if (acoes.containsKey(c)) acoes.put(c, v);
            });
        }
        return new ConfiguracaoDePermissoes(niveis, acoes);
    }

    public static PermissoesEfetivas calcular(
            PapelUsuario papel,
            ConfiguracaoDePermissoes perfilArmazenado,
            ConfiguracaoDePermissoes excecoes,
            Set<String> flagsHabilitadas) {
        ConfiguracaoDePermissoes perfil = perfilCompleto(papel, perfilArmazenado);
        ConfiguracaoDePermissoes excecoesValidas = perfilFixo(papel) ? ConfiguracaoDePermissoes.vazia() : excecoes;

        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        for (Modulo m : Modulo.values()) {
            NivelDeAcesso nivel = excecoesValidas.nivel(m).orElse(perfil.niveis().get(m));
            // Um valor salvo fora do limite (catalogo mudou, papel mudou) nunca amplia nada.
            nivel = NivelDeAcesso.menor(nivel, nivelMaximo(papel, m));
            nivel = NivelDeAcesso.maior(nivel, m.nivelMinimoPermitido());
            niveis.put(m, disponivel(m, flagsHabilitadas) ? nivel : NivelDeAcesso.SEM_ACESSO);
        }

        Map<Capacidade, EstadoDaCapacidade> estados = new EnumMap<>(Capacidade.class);
        // Ordem do enum = ordem topologica (garantida no carregamento de Capacidade).
        for (Capacidade c : Capacidade.values()) {
            estados.put(c, decidir(c, papel, perfil, excecoesValidas, niveis, estados, flagsHabilitadas));
        }
        return new PermissoesEfetivas(papel, niveis, estados);
    }

    private static EstadoDaCapacidade decidir(
            Capacidade c,
            PapelUsuario papel,
            ConfiguracaoDePermissoes perfil,
            ConfiguracaoDePermissoes excecoes,
            Map<Modulo, NivelDeAcesso> niveis,
            Map<Capacidade, EstadoDaCapacidade> jaDecididos,
            Set<String> flags) {
        if (!c.noTetoDe(papel)) {
            return new EstadoDaCapacidade(c, false, Motivo.TETO_DO_PAPEL, origemBase(papel));
        }
        if (!disponivel(c.modulo(), flags)) {
            return new EstadoDaCapacidade(c, false, Motivo.FLAG_DESLIGADA, origemBase(papel));
        }
        if (c.estrutural()) {
            return new EstadoDaCapacidade(c, true, Motivo.PERMITIDO, Origem.ESTRUTURAL);
        }
        if (perfilFixo(papel)) {
            return comDependencias(c, new EstadoDaCapacidade(c, true, Motivo.PERMITIDO, Origem.FIXO), jaDecididos);
        }
        boolean nivelDaExcecao = excecoes.nivel(c.modulo()).isPresent();
        if (!niveis.get(c.modulo()).alcanca(c.nivelMinimo())) {
            return new EstadoDaCapacidade(c, false, Motivo.NIVEL_DO_MODULO,
                    nivelDaExcecao ? Origem.EXCECAO : Origem.PERFIL);
        }
        boolean acaoDaExcecao = excecoes.acao(c).isPresent();
        boolean ligado = excecoes.acao(c).orElse(perfil.acoes().getOrDefault(c, false));
        Origem origem = acaoDaExcecao || nivelDaExcecao ? Origem.EXCECAO : Origem.PERFIL;
        if (!ligado) {
            return new EstadoDaCapacidade(c, false, Motivo.DESLIGADO, origem);
        }
        return comDependencias(c, new EstadoDaCapacidade(c, true, Motivo.PERMITIDO, origem), jaDecididos);
    }

    private static EstadoDaCapacidade comDependencias(
            Capacidade c, EstadoDaCapacidade permitido, Map<Capacidade, EstadoDaCapacidade> jaDecididos) {
        for (Capacidade dependencia : c.dependencias()) {
            if (!jaDecididos.get(dependencia).permitido()) {
                return new EstadoDaCapacidade(c, false, Motivo.DEPENDENCIA, permitido.origem());
            }
        }
        return permitido;
    }

    private static Origem origemBase(PapelUsuario papel) {
        return perfilFixo(papel) ? Origem.FIXO : Origem.PERFIL;
    }

    // --- Validacao ------------------------------------------------------------------------------

    /**
     * Valida um perfil inteiro (substituicao completa das chaves dos modulos disponiveis). Todas as
     * violacoes voltam juntas; nada e persistido parcialmente.
     */
    public static void validarPerfil(PapelUsuario papel, ConfiguracaoDePermissoes novo, Set<String> flags) {
        if (perfilFixo(papel)) {
            throw new PermissaoInvalidaException(new Violacao("perfil:" + papel.name(), Violacao.Codigo.PERFIL_FIXO));
        }
        ConfiguracaoDePermissoes completo = perfilCompleto(papel, novo);
        List<Violacao> violacoes = new ArrayList<>();
        validarNiveis(papel, novo.niveis(), flags, violacoes);
        validarAcoes(papel, novo.acoes(), completo.niveis(), flags, violacoes);
        lancarSeHouver(violacoes);
    }

    /** Valida o conjunto completo de excecoes de um usuario sobre o perfil do papel dele. */
    public static void validarExcecoes(
            PapelUsuario papelDoAlvo,
            ConfiguracaoDePermissoes perfilArmazenado,
            ConfiguracaoDePermissoes excecoes,
            Set<String> flags) {
        if (perfilFixo(papelDoAlvo)) {
            throw new PermissaoInvalidaException(
                    new Violacao("perfil:" + papelDoAlvo.name(), Violacao.Codigo.PERFIL_FIXO));
        }
        ConfiguracaoDePermissoes perfil = perfilCompleto(papelDoAlvo, perfilArmazenado);
        Map<Modulo, NivelDeAcesso> niveisResultantes = new EnumMap<>(perfil.niveis());
        niveisResultantes.putAll(excecoes.niveis());
        List<Violacao> violacoes = new ArrayList<>();
        validarNiveis(papelDoAlvo, excecoes.niveis(), flags, violacoes);
        validarAcoes(papelDoAlvo, excecoes.acoes(), niveisResultantes, flags, violacoes);
        lancarSeHouver(violacoes);
    }

    private static void validarNiveis(
            PapelUsuario papel, Map<Modulo, NivelDeAcesso> niveis, Set<String> flags, List<Violacao> violacoes) {
        niveis.forEach((m, nivel) -> {
            String chave = ConfiguracaoDePermissoes.chaveDeNivel(m);
            if (!disponivel(m, flags)) {
                violacoes.add(new Violacao(chave, Violacao.Codigo.FLAG_DESLIGADA));
            } else if (!nivel.alcanca(m.nivelMinimoPermitido()) || nivel.compareTo(nivelMaximo(papel, m)) > 0) {
                violacoes.add(new Violacao(chave, Violacao.Codigo.NIVEL_FORA_DO_LIMITE));
            }
        });
    }

    private static void validarAcoes(
            PapelUsuario papel,
            Map<Capacidade, Boolean> acoes,
            Map<Modulo, NivelDeAcesso> niveisResultantes,
            Set<String> flags,
            List<Violacao> violacoes) {
        acoes.forEach((c, ligado) -> {
            if (c.estrutural()) {
                violacoes.add(new Violacao(c.id(), Violacao.Codigo.ESTRUTURAL));
            } else if (!c.noTetoDe(papel)) {
                violacoes.add(new Violacao(c.id(), Violacao.Codigo.FORA_DO_TETO));
            } else if (!disponivel(c.modulo(), flags)) {
                violacoes.add(new Violacao(c.id(), Violacao.Codigo.FLAG_DESLIGADA));
            } else if (ligado && !niveisResultantes.get(c.modulo()).alcanca(c.nivelMinimo())) {
                violacoes.add(new Violacao(c.id(), Violacao.Codigo.NIVEL_INSUFICIENTE));
            }
        });
    }

    private static void lancarSeHouver(List<Violacao> violacoes) {
        if (!violacoes.isEmpty()) {
            throw new PermissaoInvalidaException(violacoes);
        }
    }
}
