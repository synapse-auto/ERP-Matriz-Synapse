package com.synapse.crm.atendimento.infrastructure.midia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;

/**
 * Detecção por bytes/estrutura — não lê extensão nem {@code Content-Type} declarado.
 *
 * <p>Tika é thread-safe. Recipientes Office passam por spool e inspeção limitada de metadados,
 * com recursos locais por chamada. Não extrai texto nem executa conteúdo do documento.
 */
@Component
class TikaDetectorDeTipoReal implements DetectorDeTipoReal {

    private final Tika tika = new Tika();

    @Override
    public String detectar(Path arquivo) {
        try (var entrada = Files.newInputStream(arquivo)) {
            String tipo = tika.detect(entrada);
            return DetectorDeDocumentoWord.recipiente(tipo) ? DetectorDeDocumentoWord.detectar(arquivo, tipo) : tipo;
        } catch (IOException e) { throw new UncheckedIOException("falha ao detectar midia", e); }
    }

    @Override
    public String detectar(byte[] conteudo) {
        String tipoDetectado = tika.detect(conteudo);
        if (!DetectorDeDocumentoWord.recipiente(tipoDetectado)) {
            return tipoDetectado;
        }
        // Spool para inspecao aleatoria limitada: nao duplica documento inteiro em um segundo
        // byte[] nem descomprime imagens/objetos para procurar metadados Office.
        Path temporario = null;
        try {
            temporario = Files.createTempFile("office-tipo-", ".tmp");
            Files.write(temporario, conteudo);
            return DetectorDeDocumentoWord.detectar(temporario, tipoDetectado);
        } catch (IOException e) { throw new UncheckedIOException("falha ao detectar documento", e); }
        finally {
            if (temporario != null) try { Files.deleteIfExists(temporario); }
            catch (IOException e) { temporario.toFile().deleteOnExit(); }
        }
    }
}
