package com.synapse.crm.atendimento.application.encaminhamentodochat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.equipe.application.chat.MensagemDoChatParaCliente;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

class MontadorDeConteudoDoChatParaClienteTest {

    private static final long CINCO_MB = 5L * 1024 * 1024;

    private final ObjectMapper json = new ObjectMapper();
    private MontadorDeConteudoDoChatParaCliente montador = montadorSem(Optional.empty());

    @Test
    @DisplayName("texto vira mensagem livre com o texto da linha")
    void texto() {
        ConteudoDeEnvio conteudo = montador.montar(texto("Olá"));

        assertThat(conteudo).isEqualTo(new ConteudoDeEnvio.MensagemLivre("Olá"));
    }

    static Stream<Arguments> validas() {
        return Stream.of(
                Arguments.of("IMAGEM", "image/jpeg", TipoMensagem.IMAGEM),
                Arguments.of("IMAGEM", "image/png", TipoMensagem.IMAGEM),
                Arguments.of("IMAGEM", "image/webp", TipoMensagem.IMAGEM),
                Arguments.of("VIDEO", "video/mp4", TipoMensagem.VIDEO),
                Arguments.of("VIDEO", "video/3gpp", TipoMensagem.VIDEO),
                Arguments.of("AUDIO", "audio/ogg", TipoMensagem.AUDIO),
                Arguments.of("AUDIO", "audio/mpeg", TipoMensagem.AUDIO),
                Arguments.of("DOCUMENTO", "application/pdf", TipoMensagem.DOCUMENTO));
    }

    @ParameterizedTest(name = "{0} {1} e aceito")
    @MethodSource("validas")
    void formatosAceitos(String tipo, String mime, TipoMensagem esperado) {
        ConteudoDeEnvio conteudo = montador.montar(midia(tipo, mime, "arq", 1024L, "leg"));

        assertThat(conteudo).isInstanceOfSatisfying(ConteudoDeEnvio.MensagemMidia.class, midia -> {
            assertThat(midia.tipo()).isEqualTo(esperado);
            assertThat(midia.referenciaStorage()).isEqualTo("midia/objeto");
        });
    }

    static Stream<Arguments> invalidas() {
        return Stream.of(
                Arguments.of("DOCUMENTO", "application/vnd.ms-excel.sheet.macroEnabled.12"),
                Arguments.of("VIDEO", "video/quicktime"),
                Arguments.of("VIDEO", "video/webm"),
                Arguments.of("IMAGEM", "application/pdf"),
                Arguments.of("AUDIO", "image/png"),
                Arguments.of("DOCUMENTO", "application/x-msdownload"));
    }

    @ParameterizedTest(name = "{0} {1} e recusado")
    @MethodSource("invalidas")
    void formatosRecusados(String tipo, String mime) {
        assertThatThrownBy(() -> montador.montar(midia(tipo, mime, "arq", 1024L, null)))
                .isInstanceOfSatisfying(ConteudoDoChatNaoEnviavelAoClienteException.class,
                        e -> assertThat(e.motivo()).isEqualTo(MotivoDeBloqueio.TIPO_NAO_SUPORTADO));
    }

    @Test
    @DisplayName("sem limite configurado vale o teto da Meta por tipo; no limite passa, um byte acima nao")
    void limiteDaMeta() {
        assertThat(montador.montar(midia("IMAGEM", "image/png", "a.png", CINCO_MB, null))).isNotNull();

        assertThatThrownBy(() -> montador.montar(midia("IMAGEM", "image/png", "a.png", CINCO_MB + 1, null)))
                .isInstanceOfSatisfying(ConteudoDoChatNaoEnviavelAoClienteException.class,
                        e -> assertThat(e.motivo()).isEqualTo(MotivoDeBloqueio.ARQUIVO_ACIMA_DO_LIMITE));
    }

    @Test
    @DisplayName("limite configurado pela gestao vence o teto da Meta")
    void limiteConfigurado() {
        montador = montadorSem(Optional.of(1024L));

        assertThat(montador.montar(midia("IMAGEM", "image/png", "a.png", 1024L, null))).isNotNull();
        assertThatThrownBy(() -> montador.montar(midia("IMAGEM", "image/png", "a.png", 1025L, null)))
                .isInstanceOf(ConteudoDoChatNaoEnviavelAoClienteException.class);
    }

    @Test
    @DisplayName("sem tamanho registrado o limite nao pode ser conferido e o envio e recusado")
    void semTamanho() {
        assertThatThrownBy(() -> montador.montar(midia("IMAGEM", "image/png", "a.png", null, null)))
                .isInstanceOfSatisfying(ConteudoDoChatNaoEnviavelAoClienteException.class,
                        e -> assertThat(e.motivo()).isEqualTo(MotivoDeBloqueio.ARQUIVO_SEM_TAMANHO));
    }

    @Test
    @DisplayName("metadados na forma dos adaptadores: nome, mimetype, tamanho e legenda; audio nao leva legenda")
    void metadados() throws Exception {
        ConteudoDeEnvio.MensagemMidia documento = (ConteudoDeEnvio.MensagemMidia)
                montador.montar(midia("DOCUMENTO", "application/pdf", "orçamento 2026.pdf", 2048L, "segue"));
        JsonNode m = json.readTree(documento.metadados());
        assertThat(m.path("nome").asText()).isEqualTo("or_amento_2026.pdf");
        assertThat(m.path("mimetype").asText()).isEqualTo("application/pdf");
        assertThat(m.path("tamanho").asLong()).isEqualTo(2048L);
        assertThat(m.path("legenda").asText()).isEqualTo("segue");
        assertThat(documento.legenda()).isEqualTo("segue");
        assertThat(m.has("nome_original")).isFalse();

        ConteudoDeEnvio.MensagemMidia audio = (ConteudoDeEnvio.MensagemMidia)
                montador.montar(midia("AUDIO", "audio/ogg", "voz.ogg", 10L, "ignorada"));
        assertThat(audio.legenda()).isNull();
        assertThat(json.readTree(audio.metadados()).has("legenda")).isFalse();
    }

    @ParameterizedTest(name = "nome \"{0}\" nunca carrega caminho nem caractere perigoso")
    @ValueSource(strings = {"../../etc/passwd", "C:\\pasta\\foto.exe", "a/b/c.png", "nome\r\ncom\tcontrole.png", "<script>.png"})
    void nomeSeguro(String nome) throws Exception {
        ConteudoDeEnvio.MensagemMidia conteudo = (ConteudoDeEnvio.MensagemMidia)
                montador.montar(midia("IMAGEM", "image/png", nome, 10L, null));

        String nomeFinal = json.readTree(conteudo.metadados()).path("nome").asText();
        assertThat(nomeFinal).matches("[A-Za-z0-9._-]+");
        assertThat(nomeFinal).doesNotContain("/", "\\");
    }

    @Test
    @DisplayName("sem nome, o arquivo recebe um nome neutro")
    void semNome() throws Exception {
        ConteudoDeEnvio.MensagemMidia conteudo = (ConteudoDeEnvio.MensagemMidia)
                montador.montar(midia("IMAGEM", "image/png", null, 10L, null));

        assertThat(json.readTree(conteudo.metadados()).path("nome").asText()).isEqualTo("arquivo");
    }

    private static MensagemDoChatParaCliente texto(String texto) {
        return new MensagemDoChatParaCliente(UUID.randomUUID(), UUID.randomUUID(), "TEXTO", texto, null, null, null, null, null);
    }

    private static MensagemDoChatParaCliente midia(String tipo, String mime, String nome, Long tamanho, String legenda) {
        return new MensagemDoChatParaCliente(
                UUID.randomUUID(), UUID.randomUUID(), tipo, null, "midia/objeto", nome, mime, tamanho, legenda);
    }

    private MontadorDeConteudoDoChatParaCliente montadorSem(Optional<Long> limite) {
        return new MontadorDeConteudoDoChatParaCliente(new LimiteDeAnexoRepositorio() {
            @Override
            public Optional<Long> limiteEmBytes(CategoriaDeMidia tipo) {
                return limite;
            }

            @Override
            public Optional<Long> duracaoMaximaAudioEmSegundos() {
                return Optional.empty();
            }
        }, json);
    }
}
