package com.synapse.crm.atendimento.infrastructure.midia;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;

/**
 * Deteccao por magic bytes com Apache Tika — nao le extensao nem {@code Content-Type} declarado.
 *
 * <p>{@link Tika#detect(byte[])} e thread-safe e nao faz I/O (o Tika mantem a base de assinaturas
 * de mimetype em memoria), entao um unico bean singleton serve toda a instancia sem contencao.
 */
@Component
class TikaDetectorDeTipoReal implements DetectorDeTipoReal {

    private final Tika tika = new Tika();

    @Override
    public String detectar(byte[] conteudo) {
        return tika.detect(conteudo);
    }
}
