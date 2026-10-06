package com.synapse.crm.equipe.application.chat;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Foto enviada pelo usuario, ainda nao lida: o corpo so e carregado em memoria depois da
 * autorizacao e da checagem de tamanho, porque o upload multipart pode chegar a dezenas de MB.
 *
 * @param tipoDeclarado Content-Type da parte multipart, informado pelo cliente (nao confiavel)
 * @param tamanho bytes do arquivo, conhecidos sem le-lo
 */
public record ArquivoDeFoto(String nomeOriginal, String tipoDeclarado, long tamanho, Leitor leitor) {

    @FunctionalInterface
    public interface Leitor {
        byte[] ler() throws IOException;
    }

    static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> EXTENSOES_ACEITAS = Set.of("jpg", "jpeg", "png", "webp");
    private static final int TAMANHO_MAXIMO_DO_NOME = 255;

    /** Nome sem caminho e com extensao de imagem; o nome nunca chega ao storage, mas nao entra sujo. */
    void validarDescricao() {
        String nome = nomeOriginal;
        if (nome == null || nome.isBlank() || nome.length() > TAMANHO_MAXIMO_DO_NOME) {
            throw new FotoDeGrupoInvalidaException("nome de arquivo ausente ou longo demais");
        }
        if (nome.contains("/") || nome.contains("\\") || nome.contains("..") || temCaractereDeControle(nome)) {
            throw new FotoDeGrupoInvalidaException("nome de arquivo invalido");
        }
        int ponto = nome.lastIndexOf('.');
        String extensao = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSOES_ACEITAS.contains(extensao)) {
            throw new FotoDeGrupoInvalidaException("aceitos somente arquivos JPEG, PNG ou WebP");
        }
        if (!TIPOS_ACEITOS.contains(tipoNormalizado())) {
            throw new FotoDeGrupoInvalidaException("tipo de arquivo declarado nao e uma imagem aceita");
        }
    }

    /** Content-Type sem parametros e em minusculas; {@code image/jpg} (nao oficial) vira jpeg. */
    String tipoNormalizado() {
        if (tipoDeclarado == null) {
            return "";
        }
        String tipo = tipoDeclarado.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return tipo.equals("image/jpg") ? "image/jpeg" : tipo;
    }

    private static boolean temCaractereDeControle(String texto) {
        return texto.chars().anyMatch(c -> c < 0x20 || c == 0x7f);
    }
}
