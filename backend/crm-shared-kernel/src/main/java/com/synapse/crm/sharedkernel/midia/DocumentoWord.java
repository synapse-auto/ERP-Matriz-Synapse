package com.synapse.crm.sharedkernel.midia;

import java.util.Locale;
import java.util.Set;

/** Regras de apresentação/coerência após a identificação dos bytes pela porta de detecção. */
public final class DocumentoWord {
    public static final String DOC = "application/msword";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> GENERICOS = Set.of("application/octet-stream", "application/zip",
            "application/x-tika-ooxml", "application/x-tika-msoffice", "application/x-ole-storage");
    private DocumentoWord() {}

    public static boolean ehWord(String mimetype) { return DOC.equals(mimetype) || DOCX.equals(mimetype); }

    public static boolean inspecionar(String nome, String mimetype) {
        return ehWord(mimetype) || GENERICOS.contains(mimetype == null ? "" : mimetype)
                || extensao(nome).matches("doc|docx|docm");
    }

    public static boolean nomeCompativel(String nome, String mimetype) {
        String extensao = extensao(nome);
        if ("docm".equals(extensao)) return false;
        if ("doc".equals(extensao)) return DOC.equals(mimetype);
        if ("docx".equals(extensao)) return DOCX.equals(mimetype);
        return !ehWord(mimetype) || extensao.isEmpty();
    }

    public static String nomeSeguro(String nome, String mimetype) {
        String base = nome == null ? "" : nome.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
        if (base.isBlank() || base.equals(".") || base.equals("..")) base = "documento";
        if (extensao(base).isEmpty()) base += DOC.equals(mimetype) ? ".doc" : ".docx";
        return base;
    }

    private static String extensao(String nome) {
        if (nome == null) return "";
        String base = nome.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int ponto = base.lastIndexOf('.');
        return ponto >= 0 ? base.substring(ponto + 1).toLowerCase(Locale.ROOT) : "";
    }
}
