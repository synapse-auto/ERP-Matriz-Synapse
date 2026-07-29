package com.synapse.crm.core.domain.filtro;

import java.util.List;
import java.util.Objects;

import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;

/**
 * Folha do filtro sobre um campo customizado (E06b) — o irmao dinamico de {@link CriterioSimples}.
 *
 * <p>O ponto delicado que a E06b descreveu: a allowlist estatica de {@link CampoFiltravel} e segura
 * porque e fixa em codigo. Aqui parte da allowlist vem do banco ({@code campo_customizado}), e e
 * exatamente onde a injecao voltaria a ser possivel se alguem pulasse a validacao. Por isso este tipo
 * so nasce a partir de {@link #deTextos(CampoCustomizado, String, List)}, que recebe um
 * {@link CampoCustomizado} <b>ja</b> resolvido contra o banco — nunca uma {@code String} crua do
 * cliente. Quem chama o construtor sem passar por esse metodo esta, por definicao, com uma chave que
 * alguem ja verificou.
 */
public record CriterioCustomizado(
        String chave, TipoCampoCustomizado tipo, Operador operador, List<ValorDeFiltro> valores)
        implements Criterio {

    public CriterioCustomizado {
        Objects.requireNonNull(chave, "chave e obrigatoria");
        Objects.requireNonNull(tipo, "tipo e obrigatorio");
        Objects.requireNonNull(operador, "operador e obrigatorio");
        valores = valores == null ? List.of() : List.copyOf(valores);

        if (!tipo.operadoresPermitidos().contains(operador)) {
            throw new FiltroInvalidoException("o operador " + operador + " nao se aplica a campo"
                    + " customizado do tipo " + tipo + ". Aplicaveis: "
                    + tipo.operadoresPermitidos().stream().map(Enum::name).sorted().toList());
        }
        operador.exigirQuantidadeDeValores(valores.size());
    }

    /**
     * @param campo ja resolvido e autorizado contra {@code campo_customizado} — ver a nota de classe
     */
    public static CriterioCustomizado deTextos(
            CampoCustomizado campo, String operador, List<String> valoresBrutos) {
        List<String> brutos = valoresBrutos == null ? List.of() : valoresBrutos;
        List<ValorDeFiltro> valores = brutos.stream().map(campo.tipo()::converterParaFiltro).toList();

        // LISTA: cada valor precisa estar entre as opcoes cadastradas — a mesma
        // ideia da allowlist de chave, aplicada ao valor de um campo fechado.
        if (campo.tipo() == TipoCampoCustomizado.LISTA) {
            for (ValorDeFiltro valor : valores) {
                String texto = ((ValorDeFiltro.Texto) valor).valor();
                if (!campo.opcoes().contains(texto)) {
                    throw new FiltroInvalidoException("valor '" + texto + "' nao esta entre as opcoes de '"
                            + campo.chave() + "': " + campo.opcoes());
                }
            }
        }

        return new CriterioCustomizado(campo.chave(), campo.tipo(), Operador.de(operador), valores);
    }
}
