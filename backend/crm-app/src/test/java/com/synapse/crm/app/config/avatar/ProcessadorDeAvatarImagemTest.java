package com.synapse.crm.app.config.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioInvalidaException;

class ProcessadorDeAvatarImagemTest {

    private final ProcessadorDeAvatarImagem processador = new ProcessadorDeAvatarImagem();

    @Test
    void reprocessaParaQuadradoPngSemReutilizarBytesOriginais() throws IOException {
        BufferedImage imagem = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        imagem.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", original);

        var resultado = processador.processar(original.toByteArray());

        assertThat(resultado.mimetype()).isEqualTo("image/png");
        assertThat(resultado.conteudo()).isNotEqualTo(original.toByteArray());
        BufferedImage reprocessada = ImageIO.read(new java.io.ByteArrayInputStream(resultado.conteudo()));
        assertThat(reprocessada.getWidth()).isEqualTo(256);
        assertThat(reprocessada.getHeight()).isEqualTo(256);
    }

    @Test
    void rejeitaSvgMesmoComExtensaoDeImagem() {
        assertThatThrownBy(() -> processador.processar(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes()))
                .isInstanceOf(FotoDeUsuarioInvalidaException.class);
    }

    @Test
    void rejeitaConteudoQueFingeSerPng() {
        byte[] spoof = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 'x'};

        assertThatThrownBy(() -> processador.processar(spoof))
                .isInstanceOf(FotoDeUsuarioInvalidaException.class);
    }
}
