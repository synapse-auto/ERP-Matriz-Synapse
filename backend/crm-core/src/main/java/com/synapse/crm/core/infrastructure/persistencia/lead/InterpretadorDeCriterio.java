package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;
import com.synapse.crm.core.domain.filtro.CampoFiltravel;
import com.synapse.crm.core.domain.filtro.Criterio;
import com.synapse.crm.core.domain.filtro.CriterioComposto;
import com.synapse.crm.core.domain.filtro.CriterioCustomizado;
import com.synapse.crm.core.domain.filtro.CriterioSimples;
import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.filtro.Operador;
import com.synapse.crm.core.domain.filtro.TipoDeCampo;
import com.synapse.crm.core.domain.filtro.ValorDeFiltro;

/**
 * Traduz a arvore de criterios (Composite) em condicao SQL (Interpreter).
 *
 * <p>Tres switches exaustivos guardam este arquivo: sobre {@link Criterio}, sobre {@link
 * CampoFiltravel} e sobre {@link Operador}. Acrescentar um no, um campo ou um operador <b>quebra a
 * compilacao aqui</b> ate que a traducao exista. Nao ha ramo {@code default}, e e por isso: um
 * {@code default} silencioso transformaria a novidade em "sem condicao", e uma condicao que some e um
 * filtro que devolve mais linhas do que deveria.
 *
 * <p>Nada nesta classe monta texto SQL. Toda constante do usuario entra pela API de Criteria como
 * parametro; os unicos identificadores que aparecem sao os nomes de atributo de {@link
 * LeadEntity.Campos}, que sao constantes do nosso codigo. O que chega do cliente nunca e
 * identificador — na pior das hipoteses e um valor, e valor vai para bind.
 *
 * <p>Esta classe nao sabe nada sobre visibilidade, e isso e deliberado: quem compoe e o {@link
 * LeadRepositorioJpa}, que poe a {@code VisibilidadeLeadSpecification} sempre do lado esquerdo do
 * {@code AND}.
 */
final class InterpretadorDeCriterio {

    /** Curinga do {@code LIKE} escapado com barra invertida, declarada no proprio {@code LIKE}. */
    private static final char ESCAPE = '\\';

    private InterpretadorDeCriterio() {}

    /**
     * @param agora instante unico para toda a arvore — dois criterios de janela na mesma consulta
     *     precisam concordar sobre que horas sao
     */
    static Specification<LeadEntity> interpretar(FiltroDeLeads filtro, Instant agora) {
        return (raiz, consulta, cb) -> traduzir(filtro.raiz(), raiz, consulta, cb, agora);
    }

    private static Predicate traduzir(
            Criterio criterio,
            Root<LeadEntity> raiz,
            CriteriaQuery<?> consulta,
            CriteriaBuilder cb,
            Instant agora) {

        return switch (criterio) {
            case CriterioSimples simples -> folha(simples, raiz, consulta, cb, agora);
            case CriterioCustomizado customizado -> folhaCustomizada(customizado, raiz, cb);
            case CriterioComposto composto -> {
                Predicate[] partes = composto.criterios().stream()
                        .map(filho -> traduzir(filho, raiz, consulta, cb, agora))
                        .toArray(Predicate[]::new);
                yield switch (composto.conector()) {
                    case E -> cb.and(partes);
                    case OU -> cb.or(partes);
                };
            }
        };
    }

    // --- folhas ---------------------------------------------------------------

    private static Predicate folha(
            CriterioSimples criterio,
            Root<LeadEntity> raiz,
            CriteriaQuery<?> consulta,
            CriteriaBuilder cb,
            Instant agora) {

        return switch (criterio.campo()) {
            // Os dois campos que nao sao "coluna comparada com valor".
            case TAG -> porTag(criterio, raiz, consulta, cb);
            case SEM_RETORNO_DIAS -> porJanelaSemRetorno(criterio, raiz, cb, agora);

            // O resto e coluna de `lead`. Listar um a um, em vez de um `default`,
            // e o que faz o compilador cobrar traducao para um campo novo.
            case NOME,
                    EMPRESA,
                    EMAIL,
                    TELEFONE,
                    CPF,
                    LOCALIZACAO,
                    STATUS,
                    ETAPA,
                    CANAL_ORIGEM,
                    ATENDENTE_RESPONSAVEL,
                    NUM_ATENDIMENTOS,
                    NUM_MENSAGENS,
                    CRIADO_EM ->
                porColuna(criterio, raiz.get(atributoDe(criterio.campo())), cb);
        };
    }

    /** Nome do atributo JPA de cada campo que e coluna direta de {@code lead}. */
    private static String atributoDe(CampoFiltravel campo) {
        return switch (campo) {
            case NOME -> LeadEntity.Campos.NOME;
            case EMPRESA -> LeadEntity.Campos.EMPRESA;
            case EMAIL -> LeadEntity.Campos.EMAIL;
            case TELEFONE -> LeadEntity.Campos.TELEFONE;
            case CPF -> LeadEntity.Campos.CPF;
            case LOCALIZACAO -> LeadEntity.Campos.LOCALIZACAO;
            case STATUS -> LeadEntity.Campos.STATUS_BASICO;
            case ETAPA -> LeadEntity.Campos.ETAPA;
            case CANAL_ORIGEM -> LeadEntity.Campos.CANAL_ORIGEM_ID;
            case ATENDENTE_RESPONSAVEL -> LeadEntity.Campos.ATENDENTE_RESPONSAVEL_ID;
            case NUM_ATENDIMENTOS -> LeadEntity.Campos.NUM_ATENDIMENTOS;
            case NUM_MENSAGENS -> LeadEntity.Campos.NUM_MENSAGENS;
            case CRIADO_EM -> LeadEntity.Campos.CRIADO_EM;
            case TAG, SEM_RETORNO_DIAS ->
                throw new IllegalStateException(campo + " nao e coluna de lead — ver folha(...)");
        };
    }

    private static Predicate porColuna(CriterioSimples criterio, Path<?> coluna, CriteriaBuilder cb) {
        List<Object> valores =
                criterio.valores().stream().map(InterpretadorDeCriterio::bruto).toList();

        return switch (criterio.operador()) {
            case IGUAL -> cb.equal(coluna, valores.get(0));

            // "diferente de X" inclui, para quem le a tela, o lead sem o campo preenchido.
            // Sem o IS NULL essas linhas sumiriam, porque NULL <> 'X' nao e verdadeiro.
            case DIFERENTE -> cb.or(cb.isNull(coluna), cb.notEqual(coluna, valores.get(0)));

            case CONTEM -> like(cb, coluna, "%" + escapar(texto(criterio)) + "%");
            case COMECA_COM -> like(cb, coluna, escapar(texto(criterio)) + "%");

            case MAIOR_QUE -> maiorQue(cb, coluna, valores.get(0));
            case MENOR_QUE -> menorQue(cb, coluna, valores.get(0));
            case ENTRE -> entre(cb, coluna, valores.get(0), valores.get(1));

            case EM -> coluna.in(valores);

            // Em texto, string vazia e ausencia de dado tanto quanto NULL.
            case PREENCHIDO -> ehTexto(criterio)
                    ? cb.and(cb.isNotNull(coluna), cb.notEqual(coluna, ""))
                    : cb.isNotNull(coluna);
            case VAZIO -> ehTexto(criterio)
                    ? cb.or(cb.isNull(coluna), cb.equal(coluna, ""))
                    : cb.isNull(coluna);
        };
    }

    /**
     * Tag mora em {@code lead_tag}: a condicao vira {@code EXISTS} correlacionado.
     *
     * <p>Correlacionado, e nao {@code JOIN}, porque um lead com tres tags casadas apareceria tres
     * vezes — e a contagem em tempo real da tela mostraria o triplo do conjunto real.
     */
    private static Predicate porTag(
            CriterioSimples criterio,
            Root<LeadEntity> raiz,
            CriteriaQuery<?> consulta,
            CriteriaBuilder cb) {

        if (consulta == null) {
            throw new IllegalStateException(
                    "filtro por tag precisa da CriteriaQuery para montar a subconsulta EXISTS");
        }

        List<Object> tags = criterio.valores().stream().map(InterpretadorDeCriterio::bruto).toList();

        Subquery<Integer> existe = consulta.subquery(Integer.class);
        Root<LeadTagEntity> ligacao = existe.from(LeadTagEntity.class);
        existe.select(cb.literal(1));
        existe.where(cb.and(
                cb.equal(ligacao.get(LeadTagEntity.Campos.LEAD_ID), raiz.get(LeadEntity.Campos.ID)),
                ligacao.get(LeadTagEntity.Campos.TAG_ID).in(tags)));

        return switch (criterio.operador()) {
            case IGUAL, EM -> cb.exists(existe);
            case DIFERENTE -> cb.not(cb.exists(existe));
            case CONTEM, COMECA_COM, MAIOR_QUE, MENOR_QUE, ENTRE, PREENCHIDO, VAZIO ->
                throw new IllegalStateException("operador " + criterio.operador()
                        + " nao se aplica a tag — CampoFiltravel ja barra");
        };
    }

    /**
     * "Sem retorno ha N dias" e o unico campo derivado, e o unico em que o operador <b>inverte</b>:
     * mais dias sem retorno significa data de interacao <em>menor</em>.
     *
     * <p>A subtracao acontece em Java e o resultado entra como parametro. Fazer a aritmetica em SQL
     * ({@code now() - interval}) poria o valor do usuario dentro de uma expressao de intervalo, que e
     * um lugar onde numero vira texto com facilidade demais.
     */
    private static Predicate porJanelaSemRetorno(
            CriterioSimples criterio, Root<LeadEntity> raiz, CriteriaBuilder cb, Instant agora) {

        Path<Instant> ultima = raiz.get(LeadEntity.Campos.ULTIMA_INTERACAO_EM);
        Path<Instant> criado = raiz.get(LeadEntity.Campos.CRIADO_EM);
        Expression<Instant> interacao = cb.coalesce(ultima, criado);

        return switch (criterio.operador()) {
            case MAIOR_QUE -> cb.lessThan(interacao, dataLimite(criterio, 0, agora));
            case MENOR_QUE -> cb.greaterThan(interacao, dataLimite(criterio, 0, agora));
            // "entre 30 e 60 dias sem retorno": a interacao caiu entre agora-60 e agora-30.
            // Os extremos trocam de lado junto com a inversao.
            case ENTRE -> cb.between(
                    interacao, dataLimite(criterio, 1, agora), dataLimite(criterio, 0, agora));
            case IGUAL, DIFERENTE, CONTEM, COMECA_COM, EM, PREENCHIDO, VAZIO ->
                throw new IllegalStateException("operador " + criterio.operador()
                        + " nao se aplica a semRetornoDias — CampoFiltravel ja barra");
        };
    }

    private static Instant dataLimite(CriterioSimples criterio, int posicao, Instant agora) {
        long dias = ((ValorDeFiltro.Numero) criterio.valores().get(posicao)).valor();
        return agora.minus(dias, ChronoUnit.DAYS);
    }

    /**
     * Folha de campo customizado (E06b) — o irmao dinamico de {@link #folha}.
     *
     * <p>A chave que chega em {@link CriterioCustomizado#chave()} ja foi validada contra
     * {@code campo_customizado} pelo controller, muito antes deste metodo existir; aqui ela so entra
     * como {@code cb.literal(...)}, nunca concatenada em texto de consulta. O valor de comparacao
     * segue a mesma regra de sempre: vira parametro, nunca literal de SQL montado a mao.
     *
     * <p>{@code NUMERO} usa {@code float8(...)} para comparar como numero — texto puro ordenaria "9"
     * depois de "10". Os demais tipos comparam como texto: {@code DATA} porque o valor foi
     * canonicalizado para ISO-8601 de largura fixa na escrita ({@link
     * com.synapse.crm.core.domain.campocustomizado.ValidadorDeDadosCustomizados}), o que torna a
     * comparacao lexicografica cronologicamente correta sem CAST de data no banco.
     */
    private static Predicate folhaCustomizada(
            CriterioCustomizado criterio, Root<LeadEntity> raiz, CriteriaBuilder cb) {

        Expression<String> texto = extrairTextoCustomizado(raiz, cb, criterio.chave());
        boolean numerico = criterio.tipo() == TipoCampoCustomizado.NUMERO;

        return switch (criterio.operador()) {
            case IGUAL -> numerico
                    ? cb.equal(numeroExtraido(cb, texto), valorNumerico(criterio, 0))
                    : cb.equal(texto, valorTextoCustomizado(criterio, 0));
            case DIFERENTE -> numerico
                    ? cb.or(cb.isNull(texto), cb.notEqual(numeroExtraido(cb, texto), valorNumerico(criterio, 0)))
                    : cb.or(cb.isNull(texto), cb.notEqual(texto, valorTextoCustomizado(criterio, 0)));
            case CONTEM -> like(cb, texto, "%" + escapar(valorTextoCustomizado(criterio, 0)) + "%");
            case COMECA_COM -> like(cb, texto, escapar(valorTextoCustomizado(criterio, 0)) + "%");
            case EM -> texto.in(valoresTextoCustomizados(criterio));
            case MAIOR_QUE -> numerico
                    ? cb.greaterThan(numeroExtraido(cb, texto), valorNumerico(criterio, 0))
                    : cb.greaterThan(texto, valorTextoCustomizado(criterio, 0));
            case MENOR_QUE -> numerico
                    ? cb.lessThan(numeroExtraido(cb, texto), valorNumerico(criterio, 0))
                    : cb.lessThan(texto, valorTextoCustomizado(criterio, 0));
            case ENTRE -> numerico
                    ? cb.between(numeroExtraido(cb, texto), valorNumerico(criterio, 0), valorNumerico(criterio, 1))
                    : cb.between(texto, valorTextoCustomizado(criterio, 0), valorTextoCustomizado(criterio, 1));
            case PREENCHIDO -> cb.and(cb.isNotNull(texto), cb.notEqual(texto, ""));
            case VAZIO -> cb.or(cb.isNull(texto), cb.equal(texto, ""));
        };
    }

    private static Expression<String> extrairTextoCustomizado(
            Root<LeadEntity> raiz, CriteriaBuilder cb, String chave) {
        return cb.function(
                "jsonb_extract_path_text",
                String.class,
                raiz.get(LeadEntity.Campos.DADOS_CUSTOMIZADOS),
                cb.literal(chave));
    }

    private static Expression<Double> numeroExtraido(CriteriaBuilder cb, Expression<String> texto) {
        return cb.function("float8", Double.class, texto);
    }

    private static double valorNumerico(CriterioCustomizado criterio, int indice) {
        return ((ValorDeFiltro.Numero) criterio.valores().get(indice)).valor();
    }

    private static List<String> valoresTextoCustomizados(CriterioCustomizado criterio) {
        return criterio.valores().stream()
                .map(valor -> valorTextoCustomizado(criterio.tipo(), valor))
                .toList();
    }

    private static String valorTextoCustomizado(CriterioCustomizado criterio, int indice) {
        return valorTextoCustomizado(criterio.tipo(), criterio.valores().get(indice));
    }

    /** Texto de comparacao no mesmo formato em que {@code dados_customizados} guarda o valor. */
    private static String valorTextoCustomizado(TipoCampoCustomizado tipo, ValorDeFiltro valor) {
        return switch (tipo) {
            case TEXTO, LISTA -> ((ValorDeFiltro.Texto) valor).valor();
            case NUMERO -> String.valueOf(((ValorDeFiltro.Numero) valor).valor());
            case DATA -> ((ValorDeFiltro.Data) valor).valor().toString();
            case BOOLEANO -> String.valueOf(((ValorDeFiltro.Booleano) valor).valor());
        };
    }

    // --- apoio ----------------------------------------------------------------

    /**
     * Desembrulha o valor tipado para o que a API de Criteria consome.
     *
     * <p>Switch exaustivo sobre um tipo selado: um tipo de valor novo quebra o build aqui.
     */
    private static Object bruto(ValorDeFiltro valor) {
        return switch (valor) {
            case ValorDeFiltro.Texto texto -> texto.valor();
            case ValorDeFiltro.Numero numero -> numero.valor();
            case ValorDeFiltro.Data data -> data.valor();
            case ValorDeFiltro.Status status -> status.valor();
            case ValorDeFiltro.Referencia referencia -> referencia.valor();
            case ValorDeFiltro.Booleano booleano -> booleano.valor();
        };
    }

    private static boolean ehTexto(CriterioSimples criterio) {
        return criterio.campo().tipo() == TipoDeCampo.TEXTO;
    }

    private static String texto(CriterioSimples criterio) {
        return ((ValorDeFiltro.Texto) criterio.valores().get(0)).valor();
    }

    /**
     * Neutraliza os curingas do proprio {@code LIKE}.
     *
     * <p>Sem isto, buscar por {@code "100%"} casaria com qualquer coisa comecada em {@code 100}, e um
     * {@code _} no meio de um telefone viraria "qualquer caractere". Nao e falha de seguranca — o
     * padrao ja e parametro —, e resultado errado, que e mais dificil de perceber.
     */
    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @SuppressWarnings("unchecked")
    private static Predicate like(CriteriaBuilder cb, Expression<?> coluna, String padrao) {
        return cb.like(cb.lower((Expression<String>) coluna), padrao.toLowerCase(), ESCAPE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate maiorQue(CriteriaBuilder cb, Path<?> coluna, Object valor) {
        return cb.greaterThan((Path) coluna, (Comparable) valor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate menorQue(CriteriaBuilder cb, Path<?> coluna, Object valor) {
        return cb.lessThan((Path) coluna, (Comparable) valor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate entre(CriteriaBuilder cb, Path<?> coluna, Object inicio, Object fim) {
        return cb.between((Path) coluna, (Comparable) inicio, (Comparable) fim);
    }
}
