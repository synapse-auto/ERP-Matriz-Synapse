package com.synapse.crm.atendimento.domain.midia;

import java.time.Duration;

/**
 * A porta para o object storage (S3/MinIO).
 *
 * <p>{@code salvar} devolve uma referencia <b>opaca</b>, nunca uma URL. Isso nao e detalhe: e o que
 * faz a autorizacao de leitura funcionar. Se a referencia gravada em {@code mensagem.midia_url} ja
 * fosse uma URL utilizavel, qualquer um com o link acessaria o anexo para sempre, RLS ou nao. Ao
 * guardar so a chave do objeto, a URL so nasce — assinada e com prazo curto — no momento em que
 * alguem le a mensagem pelo caminho que ja aplica a regra de visibilidade (ver
 * {@code AtendimentoMensagensController} e {@code RelayDeTempoRealListener}).
 *
 * <p>Upload e download sempre passam pelo backend: o browser nunca fala direto com o storage
 * (exigencia do prompt E11b) — o backend precisa validar o conteudo antes de gravar.
 */
public interface ArmazenamentoDeMidia {

    /** Grava os bytes e devolve a referencia opaca do objeto. */
    String salvar(byte[] conteudo, String nomeArquivoSanitizado, String mimetype);

    /** Os bytes do objeto — usado para reenviar ao provedor (Meta exige o arquivo, nao uma URL). */
    byte[] baixar(String referencia);

    /** URL assinada, valida por {@code validade}. Nunca cacheada; gerada a cada leitura. */
    String urlAssinada(String referencia, Duration validade);
}
