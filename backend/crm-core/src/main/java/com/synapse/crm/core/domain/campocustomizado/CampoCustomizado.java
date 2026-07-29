package com.synapse.crm.core.domain.campocustomizado;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Metadado de um campo especifico do filho — o que a tela de gestao (fase 2) vai gerenciar, e o que
 * hoje entra por migration ou SQL.
 *
 * <p>A chave valida contra o mesmo identificador seguro do {@code CHECK} do banco
 * ({@code ^[a-z][a-z0-9_]{2,59}$}), <b>de proposito duplicado</b>: a chave vira caminho JSONB dentro
 * de uma consulta ({@code jsonb_extract_path_text}) e, quando {@link #filtravel()}, entra na
 * allowlist dinamica do filtro modular. A aplicacao valida antes de qualquer consulta acontecer; o
 * {@code CHECK} e quem garante que nem um {@code INSERT} direto no banco cria uma chave fora do
 * formato — as duas paredes descritas na E06b, nao uma so.
 */
public record CampoCustomizado(
        String chave,
        String rotulo,
        TipoCampoCustomizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean filtravel,
        short ordem) {

    /** Mesmo padrao do {@code CHECK chk_campo_customizado_chave} (V18) — nao pode divergir. */
    private static final Pattern CHAVE_SEGURA = Pattern.compile("^[a-z][a-z0-9_]{2,59}$");

    public CampoCustomizado {
        if (chave == null || !CHAVE_SEGURA.matcher(chave).matches()) {
            throw new IllegalArgumentException(
                    "chave de campo customizado invalida: " + chave + ". Esperado " + CHAVE_SEGURA);
        }
        Objects.requireNonNull(rotulo, "rotulo e obrigatorio");
        Objects.requireNonNull(tipo, "tipo e obrigatorio");
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);

        if (tipo == TipoCampoCustomizado.LISTA && opcoes.isEmpty()) {
            throw new IllegalArgumentException(
                    "campo customizado '" + chave + "' e do tipo LISTA e precisa de ao menos uma opcao");
        }
    }
}
