package com.synapse.crm.sharedkernel.midia;

import java.time.Duration;

/**
 * Porta neutra para o object storage (S3/MinIO).
 *
 * <p>Esta interface garante que nenhum consumidor armazene a URL diretamente, mantendo a regra
 * de negocio e acesso baseada na geracao dinamica de URL assinada.
 */
public interface ArmazenamentoDeMidia {

    /** Grava os bytes e devolve a referencia opaca do objeto. */
    String salvar(byte[] conteudo, String nomeArquivoSanitizado, String mimetype);

    /** Os bytes do objeto (utilizados quando e necessario reenviar a midia, ex: Meta). */
    byte[] baixar(String referencia);

    /** URL assinada, valida por {@code validade}. Nunca cacheada; gerada a cada leitura. */
    String urlAssinada(String referencia, Duration validade);

    /** Remove o objeto do storage, essencial para compensacao de transacoes de banco de dados falhas. */
    void remover(String referencia);
}
