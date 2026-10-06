package com.synapse.crm.app.config.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.equipe.application.chat.FotoDeGrupoInvalidaException;

class ValidadorDeFotoDeGrupoImagemTest {

    private final ValidadorDeFotoDeGrupoImagem validador =
            new ValidadorDeFotoDeGrupoImagem(new FotoDeGrupoProperties(64, 8000, 24_000_000));

    @Test
    @DisplayName("JPEG, PNG e WebP verdadeiros, com o tipo declarado certo, sao aceitos")
    void formatosValidos() throws IOException {
        assertThatCode(() -> validador.validarConteudo(imagem("jpg", 320, 180), "image/jpeg")).doesNotThrowAnyException();
        assertThatCode(() -> validador.validarConteudo(imagem("png", 320, 180), "image/png")).doesNotThrowAnyException();
        assertThatCode(() -> validador.validarConteudo(webpValido(), "image/webp")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tipo declarado diferente do real (PNG enviado como JPEG) e recusado")
    void tipoDeclaradoIncompativel() throws IOException {
        assertThatThrownBy(() -> validador.validarConteudo(imagem("png", 320, 180), "image/jpeg"))
                .isInstanceOf(FotoDeGrupoInvalidaException.class)
                .hasMessageContaining("nao corresponde");
        assertThatThrownBy(() -> validador.validarConteudo(webpValido(), "image/png"))
                .isInstanceOf(FotoDeGrupoInvalidaException.class);
    }

    @Test
    @DisplayName("executavel, texto, HTML e SVG renomeados como imagem sao recusados pelos bytes")
    void extensaoFalsa() {
        byte[] exe = new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};
        assertThatThrownBy(() -> validador.validarConteudo(exe, "image/png")).isInstanceOf(FotoDeGrupoInvalidaException.class);
        for (String texto : new String[] {"<html><script>alert(1)</script></html>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>", "so um texto"}) {
            assertThatThrownBy(() -> validador.validarConteudo(texto.getBytes(StandardCharsets.UTF_8), "image/png"))
                    .isInstanceOf(FotoDeGrupoInvalidaException.class);
        }
    }

    @Test
    @DisplayName("assinatura de PNG seguida de lixo (arquivo corrompido) e recusada")
    void corrompido() throws IOException {
        byte[] corrompido = new byte[200];
        Arrays.fill(corrompido, (byte) 0x41);
        System.arraycopy(imagem("png", 100, 100), 0, corrompido, 0, 12);

        assertThatThrownBy(() -> validador.validarConteudo(corrompido, "image/png"))
                .isInstanceOf(FotoDeGrupoInvalidaException.class);
    }

    @Test
    @DisplayName("vazio ou nulo e recusado")
    void vazio() {
        assertThatThrownBy(() -> validador.validarConteudo(new byte[0], "image/png")).isInstanceOf(FotoDeGrupoInvalidaException.class);
        assertThatThrownBy(() -> validador.validarConteudo(null, "image/png")).isInstanceOf(FotoDeGrupoInvalidaException.class);
    }

    @Test
    @DisplayName("dimensao minima: 63 px e recusada, 64 px passa")
    void dimensaoMinima() throws IOException {
        assertThatThrownBy(() -> validador.validarConteudo(imagem("png", 63, 400), "image/png"))
                .isInstanceOf(FotoDeGrupoInvalidaException.class).hasMessageContaining("pequena demais");
        assertThatCode(() -> validador.validarConteudo(imagem("png", 64, 400), "image/png")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dimensao maxima e total de pixels sao configuraveis")
    void dimensaoMaxima() throws IOException {
        var limitado = new ValidadorDeFotoDeGrupoImagem(new FotoDeGrupoProperties(64, 300, 60_000));

        assertThatThrownBy(() -> limitado.validarConteudo(imagem("png", 301, 100), "image/png"))
                .isInstanceOf(FotoDeGrupoInvalidaException.class).hasMessageContaining("grande demais");
        assertThatThrownBy(() -> limitado.validarConteudo(imagem("png", 300, 300), "image/png"))
                .as("90000 px > 60000 px, mesmo com cada lado dentro do limite")
                .isInstanceOf(FotoDeGrupoInvalidaException.class);
        assertThatCode(() -> limitado.validarConteudo(imagem("png", 300, 200), "image/png")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PNG de poucos bytes que declara 30000x30000 e recusado pelo cabecalho, sem decodificar")
    void bombaDeDescompressao() throws IOException {
        byte[] pequeno = imagem("png", 100, 100);
        byte[] bomba = comDimensoesNoCabecalho(pequeno, 30_000, 30_000);

        assertThat(bomba.length).as("o arquivo continua pequeno").isLessThan(5_000);
        assertThatThrownBy(() -> validador.validarConteudo(bomba, "image/png"))
                .isInstanceOf(FotoDeGrupoInvalidaException.class).hasMessageContaining("grande demais");
    }

    @Test
    @DisplayName("limites padrao sao aplicados quando a configuracao vem zerada")
    void padroes() {
        var padrao = new FotoDeGrupoProperties(0, 0, 0);

        assertThat(padrao.ladoMinimoPx()).isEqualTo(64);
        assertThat(padrao.ladoMaximoPx()).isEqualTo(8000);
        assertThat(padrao.pixelsMaximos()).isEqualTo(24_000_000L);
        assertThatThrownBy(() -> new FotoDeGrupoProperties(500, 100, 1_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] imagem(String formato, int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        imagem.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, bytes);
        return bytes.toByteArray();
    }

    private static byte[] webpValido() throws IOException {
        try (InputStream fixture = ValidadorDeFotoDeGrupoImagemTest.class
                .getResourceAsStream("/avatar/foto-grupo-128x96.webp")) {
            return fixture.readAllBytes();
        }
    }

    /** Reescreve largura/altura do IHDR e recalcula o CRC: o arquivo segue um PNG bem formado. */
    private static byte[] comDimensoesNoCabecalho(byte[] png, int largura, int altura) {
        byte[] copia = png.clone();
        ByteBuffer.wrap(copia).putInt(16, largura).putInt(20, altura);
        CRC32 crc = new CRC32();
        crc.update(copia, 12, 17);
        ByteBuffer.wrap(copia).putInt(29, (int) crc.getValue());
        return copia;
    }
}
