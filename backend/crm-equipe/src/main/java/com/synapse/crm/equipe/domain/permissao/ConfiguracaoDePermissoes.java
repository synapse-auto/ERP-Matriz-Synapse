package com.synapse.crm.equipe.domain.permissao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Niveis por modulo e interruptores por acao — o formato comum de um perfil e das excecoes de um
 * usuario.
 *
 * <p>Num perfil, a ausencia de chave significa "valor padrao do catalogo"; numa excecao, significa
 * "herdar do perfil". Cada chave presente numa excecao conta como uma excecao persistida — inclusive
 * as de nivel (docs/47, regra de contagem).
 */
public record ConfiguracaoDePermissoes(Map<Modulo, NivelDeAcesso> niveis, Map<Capacidade, Boolean> acoes) {

    public static final String PREFIXO_NIVEL = "modulo:";

    public ConfiguracaoDePermissoes {
        niveis = Collections.unmodifiableMap(copiar(niveis, Modulo.class));
        acoes = Collections.unmodifiableMap(copiar(acoes, Capacidade.class));
    }

    public static ConfiguracaoDePermissoes vazia() {
        return new ConfiguracaoDePermissoes(Map.of(), Map.of());
    }

    public Optional<NivelDeAcesso> nivel(Modulo modulo) {
        return Optional.ofNullable(niveis.get(modulo));
    }

    public Optional<Boolean> acao(Capacidade capacidade) {
        return Optional.ofNullable(acoes.get(capacidade));
    }

    public int quantidade() {
        return niveis.size() + acoes.size();
    }

    public boolean vaziaDeFato() {
        return quantidade() == 0;
    }

    /** Mantem so as chaves cujo modulo esta disponivel; as outras ficam intocadas no banco. */
    public ConfiguracaoDePermissoes somente(java.util.function.Predicate<Modulo> modulos) {
        Map<Modulo, NivelDeAcesso> n = new EnumMap<>(Modulo.class);
        niveis.forEach((m, v) -> {
            if (modulos.test(m)) n.put(m, v);
        });
        Map<Capacidade, Boolean> a = new EnumMap<>(Capacidade.class);
        acoes.forEach((c, v) -> {
            if (modulos.test(c.modulo())) a.put(c, v);
        });
        return new ConfiguracaoDePermissoes(n, a);
    }

    /** Chaves estaveis gravadas no banco e trafegadas nas violacoes: {@code modulo:tags} ou {@code tags.criar}. */
    public static String chaveDeNivel(Modulo modulo) {
        return PREFIXO_NIVEL + modulo.id();
    }

    /**
     * Converte o que chegou pela API. Identificador desconhecido nunca e ignorado: vira violacao e o
     * payload inteiro e recusado antes de persistir qualquer coisa.
     */
    public static ConfiguracaoDePermissoes interpretar(
            Map<String, String> niveisBrutos, Map<String, Boolean> acoesBrutas) {
        List<Violacao> violacoes = new ArrayList<>();
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        Map<Capacidade, Boolean> acoes = new EnumMap<>(Capacidade.class);
        if (niveisBrutos != null) {
            niveisBrutos.forEach((id, valor) -> {
                Optional<Modulo> modulo = Modulo.porId(id);
                Optional<NivelDeAcesso> nivel = nivelDe(valor);
                if (modulo.isEmpty()) {
                    violacoes.add(new Violacao(PREFIXO_NIVEL + id, Violacao.Codigo.DESCONHECIDA));
                } else if (nivel.isEmpty()) {
                    violacoes.add(new Violacao(chaveDeNivel(modulo.get()), Violacao.Codigo.VALOR_INVALIDO));
                } else {
                    niveis.put(modulo.get(), nivel.get());
                }
            });
        }
        if (acoesBrutas != null) {
            acoesBrutas.forEach((id, valor) -> {
                Optional<Capacidade> capacidade = Capacidade.porId(id);
                if (capacidade.isEmpty()) {
                    violacoes.add(new Violacao(id, Violacao.Codigo.DESCONHECIDA));
                } else if (valor == null) {
                    violacoes.add(new Violacao(id, Violacao.Codigo.VALOR_INVALIDO));
                } else {
                    acoes.put(capacidade.get(), valor);
                }
            });
        }
        if (!violacoes.isEmpty()) {
            throw new PermissaoInvalidaException(violacoes);
        }
        return new ConfiguracaoDePermissoes(niveis, acoes);
    }

    private static Optional<NivelDeAcesso> nivelDe(String valor) {
        if (valor == null) return Optional.empty();
        try {
            return Optional.of(NivelDeAcesso.valueOf(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static <K extends Enum<K>, V> Map<K, V> copiar(Map<K, V> origem, Class<K> tipo) {
        Map<K, V> copia = new EnumMap<>(tipo);
        if (origem != null) {
            origem.forEach((k, v) -> copia.put(Objects.requireNonNull(k), Objects.requireNonNull(v)));
        }
        return copia;
    }
}
