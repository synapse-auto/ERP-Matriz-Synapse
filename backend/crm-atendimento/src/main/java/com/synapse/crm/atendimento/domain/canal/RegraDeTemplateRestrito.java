package com.synapse.crm.atendimento.domain.canal;

import java.util.Locale;
import java.util.Objects;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Templates de uso interno: o nome carrega um termo reservado e so o ADMINISTRADOR os ve e opera.
 *
 * <p>Correspondencia exata: o {@code nome} do template, convertido para minusculas com
 * {@link Locale#ROOT}, <b>contem</b> o termo tambem em minusculas — em qualquer posicao. Com o
 * termo {@code interno}: {@code aviso_interno_cliente}, {@code INTERNO_x} e {@code subinterno}
 * sao restritos; {@code aviso_cliente} e {@code internacional} nao. O corpo da mensagem nunca e
 * olhado: o que classifica o template e o nome, que e o identificador estavel na Meta.
 *
 * <p>A restricao e do papel, nao de capacidade: nenhuma permissao da Gestao a delega. GESTOR nao
 * e excecao.
 */
public record RegraDeTemplateRestrito(String termo) {

    public RegraDeTemplateRestrito {
        if (termo == null || termo.isBlank()) {
            throw new IllegalArgumentException("termo de template restrito nao pode ser vazio");
        }
        termo = termo.trim().toLowerCase(Locale.ROOT);
    }

    public boolean restringe(String nome) {
        return nome != null && nome.toLowerCase(Locale.ROOT).contains(termo);
    }

    public boolean permite(PapelUsuario papel, String nome) {
        Objects.requireNonNull(papel, "papel e obrigatorio para decidir acesso a template");
        return papel == PapelUsuario.ADMINISTRADOR || !restringe(nome);
    }
}
