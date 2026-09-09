package com.synapse.crm.atendimento.infrastructure.midia;

import java.util.Set;

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

    private static final Set<String> TIPOS_DE_CONTAINER_AMBIGUOS =
            Set.of("application/x-tika-ooxml", "application/zip");

    private final Tika tika = new Tika();

    @Override
    public String detectar(byte[] conteudo) {
        String tipoDetectado = tika.detect(conteudo);
        if (!TIPOS_DE_CONTAINER_AMBIGUOS.contains(tipoDetectado)) {
            return tipoDetectado;
        }
        // O tika-core nao inclui os parsers OOXML. Quando o detector so reconhece o ZIP,
        // inspecionamos o [Content_Types].xml do pacote para recuperar o tipo especifico sem
        // confiar na extensao ou no Content-Type informado pelo navegador.
        return DetectorDePacoteOoxml.detectar(conteudo).orElse(tipoDetectado);
    }
}
