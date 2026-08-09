package com.synapse.crm.core.domain.filtro;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Allowlist de campos filtraveis de lead.
 *
 * <p><strong>Esta lista e a fronteira.</strong> O JSON manda {@code "campo": "..."}; se o texto nao
 * casar com um destes apelidos, a requisicao morre aqui com 400. Nao existe caminho em que um nome
 * vindo do cliente vire identificador SQL — nem saneado, nem entre aspas, nem nada. E por isso que a
 * lista e um enum e nao um {@code Set<String>} de configuracao: acrescentar campo exige recompilar,
 * e recompilar passa pelos switches exaustivos do interpretador.
 *
 * <p>Duas ausencias deliberadas: {@code notas} e {@code resumoIa}. Sao os campos longos que
 * {@code LeadResumo} ja recusa em tela de lista; filtrar por eles significaria varredura de texto
 * longo em toda a base para montar uma tela que nem os exibe.
 *
 * <p>{@link #ATENDENTE_RESPONSAVEL} <em>esta</em> na lista, e de proposito. Filtrar por dono e util
 * para gestor, e para atendente e inofensivo: o filtro do usuario entra com {@code AND} por cima da
 * {@code VisibilidadeLeadSpecification}, entao ele so consegue reduzir o que ja enxerga. Pedir os
 * leads de um colega devolve conjunto vazio, nunca os leads do colega.
 */
public enum CampoFiltravel {

    NOME("nome", "Nome", TipoDeCampo.TEXTO),
    EMPRESA("empresa", "Empresa", TipoDeCampo.TEXTO),
    EMAIL("email", "E-mail", TipoDeCampo.TEXTO),
    TELEFONE("telefone", "Telefone", TipoDeCampo.TEXTO),
    CPF("cpf", "CPF/CNPJ", TipoDeCampo.TEXTO),
    LOCALIZACAO("localizacao", "Localização", TipoDeCampo.TEXTO),

    STATUS("status", "Status", TipoDeCampo.STATUS),
    ETAPA("etapa", "Etapa", TipoDeCampo.REFERENCIA),
    CANAL_ORIGEM("canalOrigem", "Canal de origem", TipoDeCampo.REFERENCIA),
    ATENDENTE_RESPONSAVEL("atendenteResponsavel", "Atendente responsável", TipoDeCampo.REFERENCIA),

    /**
     * Tag do lead. Unico campo que nao e coluna de {@code lead}: mora em {@code lead_tag}, e a
     * traducao vira {@code EXISTS}. Por ser conjunto, {@code PREENCHIDO}/{@code VAZIO} teriam
     * leitura ambigua ("tem alguma tag?" vs. "a tag e nula?") e ficam de fora.
     */
    TAG("tag", "Tag", TipoDeCampo.REFERENCIA, Operadores.CONJUNTO),

    NUM_ATENDIMENTOS("numAtendimentos", "Nº de atendimentos", TipoDeCampo.NUMERO),
    NUM_MENSAGENS("numMensagens", "Nº de mensagens", TipoDeCampo.NUMERO),
    CRIADO_EM("criadoEm", "Criado em", TipoDeCampo.DATA),

    /**
     * Dias desde a ultima interacao com o lead.
     *
     * <p>O unico campo <em>derivado</em> da lista, e o unico que nao le como o proprio nome sugere:
     * "semRetornoDias MAIOR_QUE 30" vira, em SQL, uma data <em>menor</em> que agora menos 30 dias. A
     * inversao mora no interpretador porque e ali que da para escrever uma vez so.
     *
     * <p>Igualdade nao entra: "exatamente 30 dias sem retorno" seria uma faixa de 24 horas disfarcada
     * de igualdade, e ninguem acertaria o resultado na primeira tentativa.
     */
    SEM_RETORNO_DIAS("semRetornoDias", "Dias sem retorno", TipoDeCampo.NUMERO, Operadores.JANELA);

    /**
     * Teto de {@link #SEM_RETORNO_DIAS}: cem anos.
     *
     * <p>Nao e preciosismo. {@code Instant.now().minus(n, DIAS)} com {@code n} perto de
     * {@code Long.MAX_VALUE} estoura com {@code ArithmeticException} — um 500 servido por um numero
     * no corpo da requisicao.
     */
    public static final long MAXIMO_DE_DIAS_SEM_RETORNO = 36_500L;

    private static final Map<String, CampoFiltravel> POR_APELIDO = Arrays.stream(values())
            .collect(Collectors.toMap(campo -> campo.apelido.toLowerCase(), Function.identity()));

    private final String apelido;
    private final String rotulo;
    private final TipoDeCampo tipo;
    private final Set<Operador> operadores;

    CampoFiltravel(String apelido, String rotulo, TipoDeCampo tipo) {
        this(apelido, rotulo, tipo, tipo.operadoresNaturais());
    }

    CampoFiltravel(String apelido, String rotulo, TipoDeCampo tipo, Set<Operador> operadores) {
        this.apelido = apelido;
        this.rotulo = rotulo;
        this.tipo = tipo;
        this.operadores = operadores;
    }

    /** Nome do campo como o JSON o escreve. */
    public String apelido() {
        return apelido;
    }

    /**
     * Nome legivel para a barra de filtros (E16). Vem do backend, e nao de um catalogo de textos do
     * frontend, pelo mesmo motivo de {@link #apelido()}: campo novo no enum tem que aparecer na tela
     * sem ninguem tocar no React.
     */
    public String rotulo() {
        return rotulo;
    }

    public TipoDeCampo tipo() {
        return tipo;
    }

    /** Operadores aceitos por este campo — o teto que {@link #exigirOperadorCompativel(Operador)} aplica. */
    public Set<Operador> operadores() {
        return operadores;
    }

    /** Traduz o texto recebido no JSON. Fora da lista, rejeita — nunca tenta adivinhar. */
    public static CampoFiltravel de(String recebido) {
        CampoFiltravel campo = recebido == null ? null : POR_APELIDO.get(recebido.trim().toLowerCase());
        if (campo == null) {
            throw new FiltroInvalidoException("campo nao permitido: "
                    + FiltroInvalidoException.eco(recebido) + ". Permitidos: " + apelidos());
        }
        return campo;
    }

    public static List<String> apelidos() {
        return POR_APELIDO.keySet().stream().sorted().toList();
    }

    /**
     * Como {@link #de(String)}, mas sem lancar — para o controller decidir, sem excecao como controle
     * de fluxo, se {@code recebido} e um campo estatico ou se deve tentar a allowlist dinamica de
     * campo customizado (E06b).
     */
    public static java.util.Optional<CampoFiltravel> tentar(String recebido) {
        return recebido == null
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(POR_APELIDO.get(recebido.trim().toLowerCase()));
    }

    void exigirOperadorCompativel(Operador operador) {
        if (!operadores.contains(operador)) {
            throw new FiltroInvalidoException("o operador " + operador + " nao se aplica ao campo "
                    + apelido + ". Aplicaveis: "
                    + operadores.stream().map(Enum::name).sorted().toList());
        }
    }

    /**
     * Conjuntos que fogem do natural do tipo.
     *
     * <p>Numa classe aninhada porque constante de enum nao consegue referenciar campo estatico do
     * proprio enum — os constantes sao inicializados primeiro.
     */
    private static final class Operadores {

        static final Set<Operador> CONJUNTO =
                EnumSet.of(Operador.IGUAL, Operador.DIFERENTE, Operador.EM);

        static final Set<Operador> JANELA =
                EnumSet.of(Operador.MAIOR_QUE, Operador.MENOR_QUE, Operador.ENTRE);

        private Operadores() {}
    }
}
