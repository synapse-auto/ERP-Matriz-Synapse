package com.synapse.crm.equipe.domain.permissao;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Copiar permissoes nunca eleva papel nem transmite limite superior.
 *
 * <p>A copia parte do <b>efetivo</b> da origem (o que ela realmente pode) e o recorta pelo teto do
 * papel do destino. Tudo que ficou de fora volta como {@link Impedimento}, com motivo, para a tela
 * mostrar antes da confirmacao. Origem com acesso fixo (GESTOR/ADMINISTRADOR) nao e aceita: "acesso
 * total" nao e uma lista de permissoes que se transfira.
 *
 * <p>O resultado e um rascunho; so vira permissao quando salvo pelo caminho normal, que valida de
 * novo, confere revisao e grava historico.
 */
public final class PoliticaDeCopia {

    private PoliticaDeCopia() {}

    public record Impedimento(String chave, Motivo motivo) {}

    public enum Motivo {
        /** O papel do destino nunca teve esta acao, ou o nivel da origem passa do maximo dele. */
        TETO_DO_PAPEL,
        /** Quem copia (SUBGESTOR delegado) nao pode conceder isto. */
        FORA_DA_ALCADA
    }

    public record Resultado(ConfiguracaoDePermissoes configuracao, List<Impedimento> impedidos) {}

    public static void exigirOrigemValida(PapelUsuario papelDaOrigem) {
        if (PoliticaDePermissoes.perfilFixo(papelDaOrigem)) {
            throw new PermissaoInvalidaException(new Violacao("origem", Violacao.Codigo.ORIGEM_INVALIDA));
        }
    }

    /** Copia para um perfil: devolve o perfil completo resultante (todas as chaves disponiveis). */
    public static Resultado paraPerfil(PermissoesEfetivas origem, PapelUsuario destino, Set<String> flags) {
        exigirOrigemValida(origem.papel());
        List<Impedimento> impedidos = new ArrayList<>();
        Map<Modulo, NivelDeAcesso> niveis = niveisDesejados(origem, destino, flags, impedidos);
        Map<Capacidade, Boolean> acoes = new EnumMap<>(Capacidade.class);
        for (Capacidade c : Capacidade.values()) {
            if (c.estrutural() || !PoliticaDePermissoes.disponivel(c.modulo(), flags)) continue;
            if (!c.noTetoDe(destino)) {
                if (origem.permite(c)) impedidos.add(new Impedimento(c.id(), Motivo.TETO_DO_PAPEL));
                continue;
            }
            acoes.put(c, origem.permite(c));
        }
        return new Resultado(new ConfiguracaoDePermissoes(niveis, acoes), impedidos);
    }

    /**
     * Copia para um usuario: devolve so as excecoes necessarias sobre o perfil do destino. Com
     * {@code ator} SUBGESTOR, o que ele nao pode conceder fica como estava e vira impedimento.
     */
    public static Resultado paraUsuario(
            PermissoesEfetivas origem,
            PapelUsuario papelDoDestino,
            ConfiguracaoDePermissoes perfilDoDestino,
            ConfiguracaoDePermissoes excecoesAtuais,
            PoliticaDeConcessao.Ator ator,
            Set<String> flags) {
        exigirOrigemValida(origem.papel());
        ConfiguracaoDePermissoes perfil = PoliticaDePermissoes.perfilCompleto(papelDoDestino, perfilDoDestino);
        boolean superior = PoliticaDePermissoes.perfilFixo(ator.papel());
        List<Impedimento> impedidos = new ArrayList<>();

        Map<Modulo, NivelDeAcesso> desejados = niveisDesejados(origem, papelDoDestino, flags, impedidos);
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        for (Map.Entry<Modulo, NivelDeAcesso> e : desejados.entrySet()) {
            Modulo m = e.getKey();
            NivelDeAcesso atual = excecoesAtuais.nivel(m).orElse(null);
            NivelDeAcesso novo = e.getValue().equals(perfil.niveis().get(m)) ? null : e.getValue();
            if (!superior && !java.util.Objects.equals(atual, novo)) {
                impedidos.add(new Impedimento(ConfiguracaoDePermissoes.chaveDeNivel(m), Motivo.FORA_DA_ALCADA));
                novo = atual;
            }
            if (novo != null) niveis.put(m, novo);
        }

        Map<Capacidade, Boolean> acoes = new EnumMap<>(Capacidade.class);
        for (Capacidade c : Capacidade.values()) {
            if (c.estrutural() || !PoliticaDePermissoes.disponivel(c.modulo(), flags)) continue;
            if (!c.noTetoDe(papelDoDestino)) {
                if (origem.permite(c)) impedidos.add(new Impedimento(c.id(), Motivo.TETO_DO_PAPEL));
                continue;
            }
            boolean desejado = origem.permite(c);
            Boolean atual = excecoesAtuais.acao(c).orElse(null);
            Boolean novo = desejado == perfil.acoes().getOrDefault(c, false) ? null : desejado;
            if (!superior && !java.util.Objects.equals(atual, novo)
                    && !PoliticaDeConcessao.podeAlterar(ator, c, Boolean.TRUE.equals(novo))) {
                impedidos.add(new Impedimento(c.id(), Motivo.FORA_DA_ALCADA));
                novo = atual;
            }
            NivelDeAcesso nivelResultante = niveis.getOrDefault(c.modulo(), perfil.niveis().get(c.modulo()));
            if (Boolean.TRUE.equals(novo) && !nivelResultante.alcanca(c.nivelMinimo())) {
                // So acontece quando o nivel ficou onde estava (SUBGESTOR nao mexe em nivel):
                // ligar a acao deixaria um interruptor "ligado" e bloqueado.
                impedidos.add(new Impedimento(c.id(), Motivo.FORA_DA_ALCADA));
                novo = Boolean.TRUE.equals(atual) ? null : atual;
            }
            if (novo != null) acoes.put(c, novo);
        }
        return new Resultado(new ConfiguracaoDePermissoes(niveis, acoes), impedidos);
    }

    private static Map<Modulo, NivelDeAcesso> niveisDesejados(
            PermissoesEfetivas origem, PapelUsuario destino, Set<String> flags, List<Impedimento> impedidos) {
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        for (Modulo m : Modulo.values()) {
            if (!PoliticaDePermissoes.disponivel(m, flags)) continue;
            NivelDeAcesso maximo = PoliticaDePermissoes.nivelMaximo(destino, m);
            NivelDeAcesso daOrigem = origem.nivel(m);
            if (daOrigem.compareTo(maximo) > 0) {
                impedidos.add(new Impedimento(ConfiguracaoDePermissoes.chaveDeNivel(m), Motivo.TETO_DO_PAPEL));
            }
            niveis.put(m, NivelDeAcesso.maior(NivelDeAcesso.menor(daOrigem, maximo), m.nivelMinimoPermitido()));
        }
        return niveis;
    }
}
