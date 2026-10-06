package com.synapse.crm.app.config.avatar;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.chat.ArmazenamentoDeFotoDeGrupo;

/**
 * Mesmo bucket dos avatares, prefixo proprio {@code grupo/}. O prefixo e o que isola o alcance de uma
 * referencia: {@code buscar}/{@code remover} daqui nao tocam objeto de usuario ({@code avatar/}) nem
 * de lead ({@code lead/}), e os deles nao tocam o de grupo.
 */
@Component
class MinioArmazenamentoDeFotoDeGrupo implements ArmazenamentoDeFotoDeGrupo {

    static final String PREFIXO = "grupo/";

    private final BucketDeAvatares bucket;

    MinioArmazenamentoDeFotoDeGrupo(BucketDeAvatares bucket) {
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
