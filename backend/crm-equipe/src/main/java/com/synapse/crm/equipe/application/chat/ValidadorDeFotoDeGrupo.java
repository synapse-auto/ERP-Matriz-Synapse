package com.synapse.crm.equipe.application.chat;

/**
 * Valida o conteudo real da imagem antes de qualquer decodificacao completa: o tipo detectado pelos
 * bytes (nao pelo nome) tem de ser o declarado, e as dimensoes lidas do cabecalho tem de caber nos
 * limites configurados. Uma imagem de poucos KB pode declarar dezenas de milhares de pixels por
 * lado; so o cabecalho evita decodifica-la.
 */
public interface ValidadorDeFotoDeGrupo {

    /** @throws FotoDeGrupoInvalidaException quando o conteudo nao e uma imagem aceita */
    void validarConteudo(byte[] conteudo, String tipoDeclarado);
}
