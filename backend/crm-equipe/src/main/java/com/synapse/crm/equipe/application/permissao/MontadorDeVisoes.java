package com.synapse.crm.equipe.application.permissao;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.EstadoDaCapacidade;
import com.synapse.crm.equipe.domain.permissao.Modulo;
import com.synapse.crm.equipe.domain.permissao.NivelDeAcesso;
import com.synapse.crm.equipe.domain.permissao.PermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Converte configuracao + efetivo em linhas de tela. Sem regra nova: so apresenta o que o dominio decidiu. */
final class MontadorDeVisoes {

    private MontadorDeVisoes() {}

    static List<Visoes.LinhaDeModulo> modulos(PapelUsuario papel, ConfiguracaoDePermissoes perfilArmazenado,
            ConfiguracaoDePermissoes excecoes, PermissoesEfetivas efetivas, Set<String> flags) {
        ConfiguracaoDePermissoes perfil = PoliticaDePermissoes.perfilCompleto(papel, perfilArmazenado);
        List<Visoes.LinhaDeModulo> linhas = new ArrayList<>();
        for (Modulo m : Modulo.values()) {
            if (!PoliticaDePermissoes.disponivel(m, flags)) continue;
            linhas.add(new Visoes.LinhaDeModulo(m, perfil.niveis().get(m), excecoes.nivel(m).orElse(null),
                    efetivas.nivel(m), m.nivelMinimoPermitido(), PoliticaDePermissoes.nivelMaximo(papel, m)));
        }
        return linhas;
    }

    static List<Visoes.LinhaDeCapacidade> capacidades(PapelUsuario papel, ConfiguracaoDePermissoes perfilArmazenado,
            ConfiguracaoDePermissoes excecoes, PermissoesEfetivas efetivas, Set<String> flags,
            PoliticaDeConcessao.Ator ator, boolean editavel) {
        ConfiguracaoDePermissoes perfil = PoliticaDePermissoes.perfilCompleto(papel, perfilArmazenado);
        List<Visoes.LinhaDeCapacidade> linhas = new ArrayList<>();
        for (Capacidade c : Capacidade.values()) {
            if (!PoliticaDePermissoes.disponivel(c.modulo(), flags)) continue;
            EstadoDaCapacidade estado = efetivas.estado(c);
            boolean configuravel = !c.estrutural() && c.noTetoDe(papel) && PoliticaDePermissoes.perfilConfiguravel(papel);
            boolean alteravel = editavel && configuravel && (ator == null
                    || PoliticaDeConcessao.podeAlterar(ator, c, true)
                    || PoliticaDeConcessao.podeAlterar(ator, c, false));
            linhas.add(new Visoes.LinhaDeCapacidade(c,
                    configuravel ? perfil.acoes().getOrDefault(c, false) : null,
                    excecoes.acao(c).orElse(null),
                    estado,
                    c.estrutural() ? c.alcancePara(papel) : null,
                    alteravel));
        }
        return linhas;
    }

    static Map<String, String> niveisComoTexto(ConfiguracaoDePermissoes configuracao) {
        Map<String, String> niveis = new LinkedHashMap<>();
        configuracao.niveis().forEach((m, n) -> niveis.put(m.id(), n.name()));
        return niveis;
    }

    static Map<String, Boolean> acoesComoTexto(ConfiguracaoDePermissoes configuracao) {
        Map<String, Boolean> acoes = new LinkedHashMap<>();
        configuracao.acoes().forEach((c, v) -> acoes.put(c.id(), v));
        return acoes;
    }

    /** Diferencas de efetivo entre antes e depois: o "o que muda" da previa. */
    static List<Visoes.Alteracao> alteracoes(PermissoesEfetivas antes, PermissoesEfetivas depois, Set<String> flags) {
        List<Visoes.Alteracao> alteracoes = new ArrayList<>();
        Map<Modulo, NivelDeAcesso> niveisAntes = new EnumMap<>(antes.niveis());
        for (Modulo m : Modulo.values()) {
            if (!PoliticaDePermissoes.disponivel(m, flags)) continue;
            if (niveisAntes.get(m) != depois.nivel(m)) {
                alteracoes.add(new Visoes.Alteracao(ConfiguracaoDePermissoes.chaveDeNivel(m),
                        antes.nivel(m).name(), depois.nivel(m).name()));
            }
        }
        for (Capacidade c : Capacidade.values()) {
            if (c.estrutural() || !PoliticaDePermissoes.disponivel(c.modulo(), flags)) continue;
            if (antes.permite(c) != depois.permite(c)) {
                alteracoes.add(new Visoes.Alteracao(c.id(), rotulo(antes.permite(c)), rotulo(depois.permite(c))));
            }
        }
        return alteracoes;
    }

    private static String rotulo(boolean permitido) {
        return permitido ? "PERMITIDO" : "NEGADO";
    }
}
