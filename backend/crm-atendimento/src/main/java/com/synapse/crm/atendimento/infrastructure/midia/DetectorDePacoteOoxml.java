package com.synapse.crm.atendimento.infrastructure.midia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Recupera a subcategoria de pacotes Office Open XML quando o detector de contêiner é ambíguo.
 *
 * <p>O fallback é deliberadamente restrito aos três tipos OOXML que têm caminho de documento no
 * CRM. Um ZIP arbitrário continua sendo rejeitado pela allowlist. O conteúdo lido é limitado para
 * impedir que um arquivo malformado transforme a detecção em uma leitura sem limite.
 */
final class DetectorDePacoteOoxml {

    private static final int LIMITE_CONTENT_TYPES = 1024 * 1024;
    private static final int LIMITE_XML_RELEVANTE = 64 * 1024;

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";

    private DetectorDePacoteOoxml() {}

    static Optional<String> detectar(byte[] conteudo) {
        if (conteudo == null || conteudo.length < 4) {
            return Optional.empty();
        }

        Optional<String> namespaceFallback = Optional.empty();
        boolean encontrouContentTypes = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(conteudo))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (entrada.isDirectory()) {
                    continue;
                }
                if ("[Content_Types].xml".equals(entrada.getName())) {
                    encontrouContentTypes = true;
                    String contentTypes = lerLimitado(zip, LIMITE_CONTENT_TYPES);
                    if (contentTypes == null) {
                        return Optional.empty();
                    }
                    Optional<String> tipo = tipoDoContentTypes(contentTypes);
                    if (tipo.isPresent()) {
                        return tipo;
                    }
                } else if (namespaceFallback.isEmpty() && ehXmlPrincipal(entrada.getName())) {
                    String xml = lerLimitado(zip, LIMITE_XML_RELEVANTE);
                    if (xml != null) {
                        namespaceFallback = tipoDoNamespace(xml);
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ignorada) {
            // Conteúdo inválido não pode derrubar o envio nem fazer o arquivo parecer permitido.
            return Optional.empty();
        }
        // Se o pacote declara tipos, mas nenhum dos três tipos suportados, o namespace da parte
        // principal nao pode rebaixar um arquivo macro-enabled ou outro formato para OOXML aceito.
        return encontrouContentTypes ? Optional.empty() : namespaceFallback;
    }

    private static Optional<String> tipoDoContentTypes(String xml) {
        String normalizado = xml.toLowerCase(Locale.ROOT);
        if (normalizado.contains(XLSX)) {
            return Optional.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        if (normalizado.contains(DOCX)) {
            return Optional.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (normalizado.contains(PPTX)) {
            return Optional.of("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
        return Optional.empty();
    }

    private static Optional<String> tipoDoNamespace(String xml) {
        String normalizado = xml.toLowerCase(Locale.ROOT);
        if (normalizado.contains("spreadsheetml")) {
            return Optional.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        if (normalizado.contains("wordprocessingml")) {
            return Optional.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (normalizado.contains("presentationml")) {
            return Optional.of("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
        return Optional.empty();
    }

    private static boolean ehXmlPrincipal(String nome) {
        return nome.endsWith("/workbook.xml")
                || nome.endsWith("/document.xml")
                || nome.endsWith("/presentation.xml");
    }

    private static String lerLimitado(ZipInputStream zip, int limite) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int lidos;
        while ((lidos = zip.read(buffer)) != -1) {
            total += lidos;
            if (total > limite) {
                return null;
            }
            bytes.write(buffer, 0, lidos);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
