package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.midia.EnviarMidiaUseCase;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

class MinioArmazenamentoDeMidiaIT {

    private static final String ACCESS_KEY = "e172-access-key";
    private static final String SECRET_KEY = "e172-secret-key-com-tamanho-suficiente";
    private static final DockerImageName IMAGEM_MINIO =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final GenericContainer<?> MINIO = new GenericContainer<>(IMAGEM_MINIO)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @BeforeAll
    static void iniciarMinio() {
        MINIO.start();
    }

    @AfterAll
    static void pararMinio() {
        MINIO.stop();
    }

    @ParameterizedTest(name = "armazena {0} no MinIO real")
    @MethodSource("anexosNaoAudio")
    void enviarMidiaUseCaseArmazenaAnexosNaoAudioNoMinioReal(
            String nome, String mimetype, byte[] conteudo) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        String bucket = "e172-" + UUID.randomUUID();
        var armazenamento = new MinioArmazenamentoDeMidia(
                new MidiaProperties(endpoint, endpoint, bucket, ACCESS_KEY, SECRET_KEY, Duration.ofMinutes(1)));
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        ConversorDeAudio conversor = mock(ConversorDeAudio.class);
        UUID leadId = UUID.randomUUID();

        when(detector.detectar(conteudo)).thenReturn(mimetype);
        when(limites.limiteEmBytes(CategoriaDeMidia.DOCUMENTO)).thenReturn(Optional.of(1024L * 1024L));
        when(limites.limiteEmBytes(CategoriaDeMidia.IMAGEM)).thenReturn(Optional.of(1024L * 1024L));

        var useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        useCase.executar(leadId, conteudo, nome, null);

        ArgumentCaptor<ConteudoDeEnvio> envio = ArgumentCaptor.forClass(ConteudoDeEnvio.class);
        verify(enviarMensagem).executar(eq(leadId), envio.capture());
        ConteudoDeEnvio.MensagemMidia midia = (ConteudoDeEnvio.MensagemMidia) envio.getValue();
        assertThat(armazenamento.baixar(midia.referenciaStorage())).isEqualTo(conteudo);
        assertThat(midia.metadados()).contains("\"mimetype\":\"" + mimetype + "\"");
        verify(conversor, never()).converterParaOggOpus(any(), any());
    }

    private static Stream<Arguments> anexosNaoAudio() {
        return Stream.of(
                Arguments.of(
                        "documento.pdf",
                        "application/pdf",
                        "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)),
                Arguments.of(
                        "imagem.png",
                        "image/png",
                        new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0}),
                Arguments.of(
                        "planilha.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0}));
    }
}
