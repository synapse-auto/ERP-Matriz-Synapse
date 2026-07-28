package com.synapse.crm.core.domain.filtro;

import java.util.List;
import java.util.Objects;

/**
 * Folha da arvore: um campo, um operador, os valores que o operador consome.
 *
 * <p>Nao existe instancia invalida deste record. O construtor compacto exige, nesta ordem:
 *
 * <ol>
 *   <li>campo na allowlist (ja garantido pelo tipo {@link CampoFiltravel});
 *   <li>operador aplicavel <em>aquele</em> campo — {@code CONTEM} em numero nao passa;
 *   <li>quantidade de valores compativel com a aridade do operador;
 *   <li>cada valor convertido para o tipo do campo, ou rejeicao.
 * </ol>
 *
 * <p>Quem receber um {@code CriterioSimples} nao precisa validar nada: se ele existe, e valido. O
 * interpretador so traduz.
 */
public record CriterioSimples(CampoFiltravel campo, Operador operador, List<ValorDeFiltro> valores)
        implements Criterio {

    public CriterioSimples {
        Objects.requireNonNull(campo, "campo e obrigatorio");
        Objects.requireNonNull(operador, "operador e obrigatorio");
        valores = valores == null ? List.of() : List.copyOf(valores);

        campo.exigirOperadorCompativel(operador);
        operador.exigirQuantidadeDeValores(valores.size());
        exigirJanelaDeDiasSensata(campo, valores);
    }

    /**
     * Monta a partir do que veio no JSON: tudo texto, nada convertido ainda.
     *
     * <p>Este e o unico caminho que a camada HTTP usa. Ele existe para que a conversao aconteca
     * <em>dentro</em> do dominio: se ela morasse no controller, o proximo caller (a Automacao, um
     * job) precisaria lembrar de repeti-la.
     */
    public static CriterioSimples deTextos(String campo, String operador, List<String> valores) {
        CampoFiltravel campoValidado = CampoFiltravel.de(campo);
        List<String> brutos = valores == null ? List.of() : valores;
        return new CriterioSimples(
                campoValidado,
                Operador.de(operador),
                brutos.stream().map(campoValidado.tipo()::converter).toList());
    }

    /**
     * {@code semRetornoDias} vira {@code agora menos N dias} no interpretador. Sem teto, um numero
     * grande o bastante estoura a aritmetica de {@code Instant} e devolve 500 para quem mandou apenas
     * um inteiro no corpo.
     */
    private static void exigirJanelaDeDiasSensata(CampoFiltravel campo, List<ValorDeFiltro> valores) {
        if (campo != CampoFiltravel.SEM_RETORNO_DIAS) {
            return;
        }
        for (ValorDeFiltro valor : valores) {
            long dias = ((ValorDeFiltro.Numero) valor).valor();
            if (dias < 0 || dias > CampoFiltravel.MAXIMO_DE_DIAS_SEM_RETORNO) {
                throw new FiltroInvalidoException("semRetornoDias deve estar entre 0 e "
                        + CampoFiltravel.MAXIMO_DE_DIAS_SEM_RETORNO + ", e recebeu " + dias);
            }
        }
    }
}
