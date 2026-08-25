package com.synapse.crm.app.config.avatar;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioInvalidaException;
import com.synapse.crm.equipe.application.usuario.ProcessadorDeAvatar;

/** Valida magic bytes e reencoda em PNG quadrado de 256px, removendo metadados. */
@Component
class ProcessadorDeAvatarImagem implements ProcessadorDeAvatar {

    private static final int LADO = 256;
    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");
    private final Tika tika = new Tika();

    @Override
    public Resultado processar(byte[] original) {
        if (original == null || original.length == 0) {
            throw new FotoDeUsuarioInvalidaException("a foto esta vazia");
        }
        String tipo = tika.detect(original);
        if (!TIPOS_PERMITIDOS.contains(tipo)) {
            throw new FotoDeUsuarioInvalidaException("aceitos somente JPEG, PNG ou WebP");
        }
        BufferedImage entrada;
        try {
            entrada = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException e) {
            throw new FotoDeUsuarioInvalidaException("conteudo de imagem invalido");
        }
        if (entrada == null) {
            throw new FotoDeUsuarioInvalidaException("conteudo de imagem invalido");
        }

        int lado = Math.min(entrada.getWidth(), entrada.getHeight());
        int x = (entrada.getWidth() - lado) / 2;
        int y = (entrada.getHeight() - lado) / 2;
        BufferedImage saida = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D desenho = saida.createGraphics();
        try {
            desenho.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            desenho.drawImage(entrada, 0, 0, LADO, LADO, x, y, x + lado, y + lado, null);
        } finally {
            desenho.dispose();
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(saida, "png", bytes)) {
                throw new FotoDeUsuarioInvalidaException("nao foi possivel reprocessar a foto");
            }
            return new Resultado(bytes.toByteArray(), "image/png");
        } catch (IOException e) {
            throw new FotoDeUsuarioInvalidaException("nao foi possivel reprocessar a foto");
        }
    }
}
