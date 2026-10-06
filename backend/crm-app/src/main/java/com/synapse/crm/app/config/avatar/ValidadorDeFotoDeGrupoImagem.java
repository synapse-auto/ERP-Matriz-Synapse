package com.synapse.crm.app.config.avatar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.chat.FotoDeGrupoInvalidaException;
import com.synapse.crm.equipe.application.chat.ValidadorDeFotoDeGrupo;

/**
 * Confere o que o cliente alegou contra os bytes: tipo detectado por magic bytes (nunca pelo nome) e
 * dimensoes lidas so do cabecalho. A decodificacao completa fica para o reprocessamento, que so roda
 * depois que esta validacao aprova.
 */
@Component
class ValidadorDeFotoDeGrupoImagem implements ValidadorDeFotoDeGrupo {

    private final Tika tika = new Tika();
    private final FotoDeGrupoProperties limites;

    ValidadorDeFotoDeGrupoImagem(FotoDeGrupoProperties limites) {
        this.limites = limites;
    }

    @Override
    public void validarConteudo(byte[] conteudo, String tipoDeclarado) {
        if (conteudo == null || conteudo.length == 0) {
            throw new FotoDeGrupoInvalidaException("a foto esta vazia");
        }
        String tipoReal = tika.detect(conteudo);
        if (!tipoReal.equals(tipoDeclarado)) {
            throw new FotoDeGrupoInvalidaException(
                    "o conteudo do arquivo nao corresponde ao tipo declarado: aceitos somente JPEG, PNG ou WebP verdadeiros");
        }
        validarDimensoes(lerDimensoes(conteudo));
    }

    private void validarDimensoes(int[] dimensoes) {
        int largura = dimensoes[0];
        int altura = dimensoes[1];
        if (Math.min(largura, altura) < limites.ladoMinimoPx()) {
            throw new FotoDeGrupoInvalidaException(
                    "a imagem e pequena demais: o menor lado precisa ter ao menos " + limites.ladoMinimoPx() + " px");
        }
        if (Math.max(largura, altura) > limites.ladoMaximoPx()
                || (long) largura * altura > limites.pixelsMaximos()) {
            throw new FotoDeGrupoInvalidaException("a imagem e grande demais: reduza as dimensoes e tente de novo");
        }
    }

    private static int[] lerDimensoes(byte[] conteudo) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(conteudo))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new FotoDeGrupoInvalidaException("conteudo de imagem invalido");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                return new int[] {leitor.getWidth(0), leitor.getHeight(0)};
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof FotoDeGrupoInvalidaException invalida) {
                throw invalida;
            }
            throw new FotoDeGrupoInvalidaException("conteudo de imagem invalido");
        }
    }
}
