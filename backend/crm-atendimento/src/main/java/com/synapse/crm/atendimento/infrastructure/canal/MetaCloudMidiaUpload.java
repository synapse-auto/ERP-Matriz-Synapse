package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.Locale;

import org.springframework.http.MediaType;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/**
 * Traduz o MIME e o nome que o CRM guarda para o que a Cloud API da Meta aceita no upload
 * multipart. A Meta e quem exige {@code codecs=opus} em OGG e um Content-Type no arquivo que
 * coincida com o campo {@code type} — conhecimento de provedor, nao de dominio.
 */
final class MetaCloudMidiaUpload {

    private MetaCloudMidiaUpload() {}

    static String tipoDoCampo(String mimetype, TipoMensagem tipo) {
        String principal = tipoPrincipal(mimetype);
        if (principal.equals("audio/ogg") || principal.equals("audio/opus")) {
            return "audio/ogg; codecs=opus";
        }
        if (!principal.isEmpty()) {
            return principal;
        }
        return switch (tipo) {
            case AUDIO -> "audio/mp4";
            case IMAGEM -> "image/jpeg";
            case DOCUMENTO -> "application/octet-stream";
            case TEXTO, BOTOES, LISTA -> "application/octet-stream";
        };
    }

    static String tipoDoArquivo(String tipoDoCampo) {
        return tipoPrincipal(tipoDoCampo);
    }

    static boolean ehNotaDeVoz(String mimetype) {
        String principal = tipoPrincipal(mimetype);
        return principal.equals("audio/ogg") || principal.equals("audio/opus");
    }

    static String nomeDoArquivo(String nome, String tipoDoArquivo) {
        String extensao = extensao(tipoDoArquivo);
        String base = (nome == null || nome.isBlank()) ? nomePadrao(tipoDoArquivo) : nome.strip();
        if (extensao.isEmpty()) {
            return base;
        }
        int ponto = base.lastIndexOf('.');
        String semExtensao = ponto > 0 ? base.substring(0, ponto) : base;
        return semExtensao + extensao;
    }

    static MediaType contentType(String tipoDoArquivo) {
        String principal = tipoPrincipal(tipoDoArquivo);
        int barra = principal.indexOf('/');
        if (barra <= 0 || barra == principal.length() - 1) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return new MediaType(principal.substring(0, barra), principal.substring(barra + 1));
    }

    static String tipoPrincipal(String mimetype) {
        if (mimetype == null || mimetype.isBlank()) {
            return "";
        }
        int pontoEVirgula = mimetype.indexOf(';');
        String bruto = pontoEVirgula < 0 ? mimetype : mimetype.substring(0, pontoEVirgula);
        return bruto.strip().toLowerCase(Locale.ROOT);
    }

    private static String extensao(String tipoDoArquivo) {
        return switch (tipoPrincipal(tipoDoArquivo)) {
            case "audio/ogg", "audio/opus" -> ".ogg";
            case "audio/mpeg" -> ".mp3";
            case "audio/aac" -> ".aac";
            case "audio/amr" -> ".amr";
            case "audio/mp4" -> ".m4a";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private static String nomePadrao(String tipoDoArquivo) {
        String principal = tipoPrincipal(tipoDoArquivo);
        if (principal.startsWith("audio/")) {
            return "audio";
        }
        if (principal.startsWith("image/")) {
            return "imagem";
        }
        return "arquivo";
    }
}
