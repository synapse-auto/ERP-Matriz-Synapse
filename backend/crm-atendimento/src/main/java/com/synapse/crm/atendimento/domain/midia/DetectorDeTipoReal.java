package com.synapse.crm.atendimento.domain.midia;

/**
 * Mimetype real, por assinatura de bytes (magic bytes) — nunca por extensao nem pelo
 * {@code Content-Type} que o cliente declarou no upload.
 *
 * <p>Existe porque extensao e {@code Content-Type} sao so texto que o remetente escolheu: um
 * arquivo {@code .jpg} pode ser um executavel. A implementacao (Apache Tika) fica na
 * infraestrutura — o dominio so conhece a pergunta.
 */
public interface DetectorDeTipoReal {

    /** O mimetype real do conteudo, ex. {@code image/jpeg}. */
    String detectar(byte[] conteudo);
}
