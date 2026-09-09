package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import com.synapse.crm.sharedkernel.midia.ResumoSeguroDeMidia;
import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

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
        verify(conversor, never()).converterParaAacAdts(any(), any());
    }

    @Test
    void gravaERecuperaExatamenteOArtefatoOggDoComposerNoMinioReal() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        String bucket = "e179-audio-" + UUID.randomUUID();
        var armazenamento = new MinioArmazenamentoDeMidia(
                new MidiaProperties(endpoint, endpoint, bucket, ACCESS_KEY, SECRET_KEY, Duration.ofMinutes(1)));
        byte[] ogg = oggOpusValido();

        assertThat(ValidadorDeOggOpus.ehValido(ogg)).isTrue();
        String referencia = armazenamento.salvar(ogg, "gravacao.ogg", "audio/ogg");
        byte[] recuperado = armazenamento.baixar(referencia);

        assertThat(recuperado).isEqualTo(ogg);
        assertThat(ResumoSeguroDeMidia.de(recuperado))
                .isEqualTo(ResumoSeguroDeMidia.de(ogg));
    }

    @Test
    void gravaRecuperaEValidaComFfprobeAmesmaNotaDeVozDoComposer() throws Exception {
        Assumptions.assumeTrue(ffmpegDisponivel(), "FFmpeg não instalado neste ambiente");
        byte[] entrada = gerarMp4Aac();
        byte[] ogg = new FfmpegConversorDeAudio("ffmpeg")
                .converterParaOggOpus(entrada, "audio/mp4")
                .conteudo();
        String antes = inspecionarComFfprobe(ogg);

        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        String bucket = "e179-audio-ffprobe-" + UUID.randomUUID();
        var armazenamento = new MinioArmazenamentoDeMidia(
                new MidiaProperties(endpoint, endpoint, bucket, ACCESS_KEY, SECRET_KEY, Duration.ofMinutes(1)));
        String referencia = armazenamento.salvar(ogg, "gravacao.ogg", "audio/ogg");
        byte[] recuperado = armazenamento.baixar(referencia);
        String depois = inspecionarComFfprobe(recuperado);

        assertThat(recuperado).isEqualTo(ogg);
        assertThat(ResumoSeguroDeMidia.de(recuperado)).isEqualTo(ResumoSeguroDeMidia.de(ogg));
        assertThat(antes).contains("codec_name=opus", "channels=1", "sample_rate=48000");
        assertThat(depois).contains("codec_name=opus", "channels=1", "sample_rate=48000");
        assertThat(duracao(depois)).isPositive();
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

    private static boolean ffmpegDisponivel() {
        try {
            Process processo = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            processo.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return processo.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static byte[] gerarMp4Aac() throws IOException, InterruptedException {
        Process processo = new ProcessBuilder(List.of(
                        "ffmpeg",
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-f",
                        "lavfi",
                        "-i",
                        "sine=frequency=1000:duration=0.2",
                        "-ac",
                        "2",
                        "-ar",
                        "44100",
                        "-c:a",
                        "aac",
                        "-movflags",
                        "frag_keyframe+empty_moov",
                        "-f",
                        "mp4",
                        "pipe:1"))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] saida = processo.getInputStream().readAllBytes();
        assertThat(processo.waitFor()).isZero();
        return saida;
    }

    private static String inspecionarComFfprobe(byte[] audio) throws IOException, InterruptedException {
        Path arquivo = Files.createTempFile("ffprobe-storage-audio-", ".ogg");
        try {
            Files.write(arquivo, audio);
            Process processo = new ProcessBuilder(List.of(
                            "ffprobe",
                            "-v",
                            "error",
                            "-select_streams",
                            "a:0",
                            "-show_entries",
                            "stream=codec_name,channels,sample_rate,duration",
                            "-of",
                            "default=noprint_wrappers=1",
                            "-i",
                            arquivo.toString()))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(processo.waitFor()).isZero();
            return saida;
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }

    private static double duracao(String detalhes) {
        return java.util.Arrays.stream(detalhes.split("\\R"))
                .filter(linha -> linha.startsWith("duration="))
                .mapToDouble(linha -> Double.parseDouble(linha.substring("duration=".length())))
                .findFirst()
                .orElse(0);
    }

    private static byte[] oggOpusValido() {
        byte[] opusHead = {
            'O', 'p', 'u', 's', 'H', 'e', 'a', 'd',
            1, 1, 0, 0, (byte) 0x80, (byte) 0xBB, 0, 0, 0, 0, 0
        };
        return concatenar(paginaOgg(0, 0, opusHead), paginaOgg(0x04, 960, new byte[] {0}));
    }

    private static byte[] paginaOgg(int flags, long granule, byte[] payload) {
        byte[] pagina = new byte[28 + payload.length];
        pagina[0] = 'O';
        pagina[1] = 'g';
        pagina[2] = 'g';
        pagina[3] = 'S';
        pagina[5] = (byte) flags;
        for (int indice = 0; indice < Long.BYTES; indice++) {
            pagina[6 + indice] = (byte) (granule >>> (8 * indice));
        }
        pagina[26] = 1;
        pagina[27] = (byte) payload.length;
        System.arraycopy(payload, 0, pagina, 28, payload.length);
        return pagina;
    }

    private static byte[] concatenar(byte[] primeiro, byte[] segundo) {
        byte[] resultado = java.util.Arrays.copyOf(primeiro, primeiro.length + segundo.length);
        System.arraycopy(segundo, 0, resultado, primeiro.length, segundo.length);
        return resultado;
    }
}
