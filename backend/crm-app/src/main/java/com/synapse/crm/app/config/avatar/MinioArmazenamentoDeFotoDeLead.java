package com.synapse.crm.app.config.avatar;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.lead.foto.ArmazenamentoDeFotoDeLead;

/**
 * Mesmo bucket dos avatares, prefixo proprio {@code lead/} (E97).
 *
 * <p>Prefixo separado, e nao bucket separado, porque o que precisa ficar isolado e o alcance de uma
 * referencia: {@code buscar}/{@code remover} do avatar de usuario filtram por {@code avatar/} e nao
 * conseguem tocar objeto de lead, e vice-versa.
 */
@Component
class MinioArmazenamentoDeFotoDeLead implements ArmazenamentoDeFotoDeLead {

    static final String PREFIXO = "lead/";

    private final BucketDeAvatares bucket;

    MinioArmazenamentoDeFotoDeLead(BucketDeAvatares bucket) {
        this.bucket = bucket;
    }

    @Override
    public String salvar(byte[] conteudo, String mimetype) {
        return bucket.salvar(PREFIXO, conteudo, mimetype);
    }

    @Override
    public Optional<Arquivo> buscar(String referencia) {
        return bucket.buscar(PREFIXO, referencia).map(conteudo -> new Arquivo(conteudo, "image/png"));
    }

    @Override
    public void remover(String referencia) {
        bucket.remover(PREFIXO, referencia);
    }
}
