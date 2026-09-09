package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.midia.EnviarMidiaUseCase;
import com.synapse.crm.atendimento.application.midia.TipoDeMidiaNaoPermitidoException;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

/** Exercita o detector Tika real com pacotes OOXML válidos, sem mockar a detecção por MIME. */
class TikaDetectorDeTipoRealIT {

    private static final String XLSX_MIMETYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX_MIMETYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PPTX_MIMETYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @ParameterizedTest(name = "detecta pacote {0} pelo conteúdo real")
    @MethodSource("pacotesOoxml")
    void detectaPacoteOoxmlPeloContentTypes(String extensao, String mimetype, byte[] pacote) {
        assertThat(new TikaDetectorDeTipoReal().detectar(pacote)).isEqualTo(mimetype);
    }

    @ParameterizedTest(name = "aceita pacote {0} no caso de uso real")
    @MethodSource("pacotesOoxml")
    void casoDeUsoAceitaPacoteOoxmlComDetectorReal(String extensao, String mimetype, byte[] pacote) {
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        when(limites.limiteEmBytes(CategoriaDeMidia.DOCUMENTO)).thenReturn(Optional.of(1024L * 1024L));
        when(armazenamento.salvar(any(), any(), eq(mimetype))).thenReturn("midias/anexo" + extensao);

        var useCase = new EnviarMidiaUseCase(
                new TikaDetectorDeTipoReal(),
                armazenamento,
                limites,
                enviarMensagem,
                new ObjectMapper(),
                mock(ConversorDeAudio.class));
        UUID leadId = UUID.randomUUID();

        useCase.executar(leadId, pacote, "anexo" + extensao, null);

        ArgumentCaptor<ConteudoDeEnvio> envio = ArgumentCaptor.forClass(ConteudoDeEnvio.class);
        verify(enviarMensagem).executar(eq(leadId), envio.capture());
        assertThat(envio.getValue()).isInstanceOf(ConteudoDeEnvio.MensagemMidia.class);
        assertThat(((ConteudoDeEnvio.MensagemMidia) envio.getValue()).tipo()).isEqualTo(TipoMensagem.DOCUMENTO);
    }

    @ParameterizedTest
    @MethodSource("pacotesNaoOoxml")
    void zipArbitrarioEArquivoInvalidoNaoViraramAnexoPermitido(byte[] conteudo) {
        String tipo = new TikaDetectorDeTipoReal().detectar(conteudo);

        assertThat(tipo).isNotEqualTo(XLSX_MIMETYPE);
        assertThat(tipo).isNotEqualTo(DOCX_MIMETYPE);
        assertThat(tipo).isNotEqualTo(PPTX_MIMETYPE);
    }

    @Test
    void executavelDisfarcadoDePdfContinuaRejeitadoPeloCasoDeUso() {
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        var useCase = new EnviarMidiaUseCase(
                new TikaDetectorDeTipoReal(),
                armazenamento,
                limites,
                enviarMensagem,
                new ObjectMapper(),
                mock(ConversorDeAudio.class));

        assertThatThrownBy(() -> useCase.executar(
                        UUID.randomUUID(), "MZ\u0000\u0001".getBytes(StandardCharsets.US_ASCII), "nota.pdf", null))
                .isInstanceOf(TipoDeMidiaNaoPermitidoException.class);
        verifyNoInteractions(armazenamento, enviarMensagem);
    }

    static Stream<Arguments> pacotesOoxml() {
        return Stream.of(
                Arguments.of(".xlsx", XLSX_MIMETYPE, pacoteOoxml("xl/workbook.xml", "spreadsheetml")),
                Arguments.of(".docx", DOCX_MIMETYPE, pacoteOoxml("word/document.xml", "wordprocessingml")),
                Arguments.of(".pptx", PPTX_MIMETYPE, pacoteOoxml("ppt/presentation.xml", "presentationml")));
    }

    static Stream<Arguments> pacotesNaoOoxml() {
        return Stream.of(Arguments.of(zipArbitrario()), Arguments.of("MZ\u0000\u0001".getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] pacoteOoxml(String partePrincipal, String namespace) {
        String mimetype = switch (namespace) {
            case "spreadsheetml" -> XLSX_MIMETYPE + ".main+xml";
            case "wordprocessingml" -> DOCX_MIMETYPE + ".main+xml";
            case "presentationml" -> PPTX_MIMETYPE + ".main+xml";
            default -> throw new IllegalArgumentException("namespace OOXML desconhecido");
        };
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Override PartName=\"/" + partePrincipal + "\" ContentType=\"" + mimetype + "\"/>"
                + "</Types>";
        String xmlPrincipal = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<root xmlns=\"http://schemas.openxmlformats.org/" + namespace + "/2006/main\"/>";
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                adicionar(zip, "[Content_Types].xml", contentTypes);
                adicionar(zip, partePrincipal, xmlPrincipal);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new AssertionError("falha ao gerar pacote OOXML de teste", e);
        }
    }

    private static byte[] zipArbitrario() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                adicionar(zip, "arquivo.txt", "conteudo");
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new AssertionError("falha ao gerar ZIP de teste", e);
        }
    }

    private static void adicionar(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
