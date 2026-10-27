package com.synapse.crm.atendimento.infrastructure.midia;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/** Identificação estrutural, não antivírus, renderizador ou validador de todo o conteúdo Word. */
final class DetectorDeDocumentoWord {
    static final String DOC = "application/msword";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    // Mesmo orçamento de metadados já usado pelo fallback OOXML; nunca extrair o ZIP inteiro.
    private static final int METADADOS = 1024 * 1024;
    private static final int PREFIXO_XML = 64 * 1024;
    private static final String OPC = "http://schemas.openxmlformats.org/package/2006/";
    private static final String OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String OFFICE_ESTRITO = "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";
    private static final Set<String> WORD = Set.of("http://schemas.openxmlformats.org/wordprocessingml/2006/main",
            "http://purl.oclc.org/ooxml/wordprocessingml/main");

    private DetectorDeDocumentoWord() {}

    static String detectar(Path arquivo, String detectado) throws IOException {
        byte[] cabecalho;
        try (var entrada = Files.newInputStream(arquivo)) { cabecalho = entrada.readNBytes(8); }
        if (ole(cabecalho)) return docValido(arquivo) ? DOC : "application/x-tika-msoffice";
        if (cabecalho.length < 4 || cabecalho[0] != 'P' || cabecalho[1] != 'K')
            return Set.of(DOC, DOCX).contains(detectado) ? "application/octet-stream" : detectado;
        if (!diretorioZipLimitado(arquivo)) return "application/zip";
        if (docxValido(arquivo)) return DOCX;
        String outro = DetectorDePacoteOoxml.detectar(arquivo).orElse(detectado);
        return DOCX.equals(outro) ? "application/zip" : outro;
    }

    static boolean recipiente(String detectado) {
        return Set.of(DOC, DOCX, "application/x-tika-msoffice", "application/x-ole-storage",
                "application/x-tika-ooxml", "application/zip").contains(detectado);
    }

    private static boolean ole(byte[] cabecalho) {
        return java.util.Arrays.equals(cabecalho, new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,
                (byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1});
    }

    private static boolean docValido(Path arquivo) {
        try (var fs = new POIFSFileSystem(arquivo.toFile(), true)) {
            var raiz = fs.getRoot();
            if (!(raiz.getEntry("WordDocument") instanceof DocumentEntry documento) || documento.getSize() < 32)
                return false;
            try (var entrada = raiz.createDocumentInputStream("WordDocument")) {
                byte[] fib = entrada.readNBytes(32);
                var dados = ByteBuffer.wrap(fib).order(ByteOrder.LITTLE_ENDIAN);
                if (fib.length != 32 || Short.toUnsignedInt(dados.getShort(0)) != 0xa5ec) return false;
                int versao = Short.toUnsignedInt(dados.getShort(2));
                int flags = Short.toUnsignedInt(dados.getShort(10));
                // Sem tentar decriptar conteúdo, templates ou pacotes Office de outro tipo.
                if ((flags & 0x0101) != 0 || versao < 0x65) return false;
                if (documento.getSize() > 32) {
                    entrada.skipNBytes(documento.getSize() - 33L);
                    if (entrada.read() < 0) return false;
                }
                if (versao >= 0xc1) {
                    String tabela = (flags & 0x0200) == 0 ? "0Table" : "1Table";
                    if (!(raiz.getEntry(tabela) instanceof DocumentEntry parte) || parte.getSize() == 0) return false;
                    try (var trecho = raiz.createDocumentInputStream(tabela)) {
                        trecho.skipNBytes(parte.getSize() - 1L);
                        if (trecho.read() < 0) return false;
                    }
                }
                return true;
            }
        } catch (IOException | RuntimeException invalido) { return false; }
    }

    /** Limita o diretório ANTES de ZipFile alocar seu índice; rejeita ZIP truncado/ZIP64 ambíguo. */
    private static boolean diretorioZipLimitado(Path arquivo) {
        try (var dados = new RandomAccessFile(arquivo.toFile(), "r")) {
            long tamanho = dados.length();
            if (tamanho < 22) return false;
            int quantidade = (int)Math.min(tamanho, 65535 + 22);
            byte[] fim = new byte[quantidade]; dados.seek(tamanho - quantidade); dados.readFully(fim);
            var b = ByteBuffer.wrap(fim).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = fim.length - 22; i >= 0; i--) {
                if (b.getInt(i) != 0x06054b50 || i + 22 + Short.toUnsignedInt(b.getShort(i + 20)) != fim.length) continue;
                long central = Integer.toUnsignedLong(b.getInt(i + 12));
                long inicio = Integer.toUnsignedLong(b.getInt(i + 16));
                return b.getShort(i + 4) == 0 && b.getShort(i + 6) == 0
                        && b.getShort(i + 8) == b.getShort(i + 10)
                        && Short.toUnsignedInt(b.getShort(i + 10)) != 65535
                        && central <= METADADOS && inicio + central == tamanho - quantidade + i;
            }
            return false;
        } catch (IOException invalido) { return false; }
    }

    private static boolean docxValido(Path arquivo) {
        try (var zip = new ZipFile(arquivo.toFile())) {
            Set<String> nomes = new HashSet<>();
            var entradas = zip.entries();
            while (entradas.hasMoreElements()) {
                String nome = entradas.nextElement().getName();
                if (!nomes.add(nome) || nome.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) return false;
            }
            String principal = null;
            var tipos = xml(ler(zip, "[Content_Types].xml"));
            try {
                while (tipos.hasNext()) {
                    int evento = tipos.next();
                    if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) return false;
                    if (evento != XMLStreamConstants.START_ELEMENT) continue;
                    String tipo = tipos.getAttributeValue(null, "ContentType");
                    if (tipo != null && (tipo.toLowerCase(Locale.ROOT).contains("macroenabled")
                            || tipo.toLowerCase(Locale.ROOT).contains("vbaproject"))) return false;
                    if ("Override".equals(tipos.getLocalName()) && (DOCX + ".main+xml").equals(tipo)
                            && (OPC + "content-types").equals(tipos.getNamespaceURI())) {
                        if (principal != null) return false;
                        principal = tipos.getAttributeValue(null, "PartName");
                    }
                }
            } finally { tipos.close(); }
            if (principal == null || !principal.startsWith("/") || principal.contains("..")) return false;
            String destino = null;
            var relacoes = xml(ler(zip, "_rels/.rels"));
            try {
                while (relacoes.hasNext()) {
                    int evento = relacoes.next();
                    if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) return false;
                    if (evento != XMLStreamConstants.START_ELEMENT || !"Relationship".equals(relacoes.getLocalName())
                            || !(OPC + "relationships").equals(relacoes.getNamespaceURI())) continue;
                    String tipo = relacoes.getAttributeValue(null, "Type");
                    if (!OFFICE.equals(tipo) && !OFFICE_ESTRITO.equals(tipo)) continue;
                    if (destino != null || "External".equalsIgnoreCase(relacoes.getAttributeValue(null, "TargetMode"))) return false;
                    destino = relacoes.getAttributeValue(null, "Target");
                }
            } finally { relacoes.close(); }
            if (destino == null || !principal.equals(destino.startsWith("/") ? destino : "/" + destino)) return false;
            var parte = zip.getEntry(principal.substring(1));
            if (parte == null || parte.isDirectory() || parte.getSize() <= 0) return false;
            // Apenas o início estrutural. Não extrai texto/imagens nem interpreta campos/macros.
            try (var entrada = zip.getInputStream(parte)) {
                var documento = xml(entrada.readNBytes(PREFIXO_XML));
                try {
                    boolean raiz = false, corpo = false;
                    while (documento.hasNext()) {
                        int evento = documento.next();
                        if (evento == XMLStreamConstants.DTD || evento == XMLStreamConstants.ENTITY_REFERENCE) return false;
                        if (evento != XMLStreamConstants.START_ELEMENT) continue;
                        if (!raiz) {
                            if (!"document".equals(documento.getLocalName()) || !WORD.contains(documento.getNamespaceURI())) return false;
                            raiz = true;
                        } else if ("body".equals(documento.getLocalName()) && WORD.contains(documento.getNamespaceURI())) {
                            corpo = true;
                            if (parte.getSize() > PREFIXO_XML) return true;
                        }
                    }
                    return raiz && corpo;
                } finally { documento.close(); }
            }
        } catch (IOException | XMLStreamException | RuntimeException invalido) { return false; }
    }

    private static byte[] ler(ZipFile zip, String nome) throws IOException {
        var entrada = zip.getEntry(nome);
        if (entrada == null || entrada.getSize() < 0 || entrada.getSize() > METADADOS) throw new IOException("metadados Office invalidos");
        try (InputStream dados = zip.getInputStream(entrada)) {
            byte[] bytes = dados.readNBytes(METADADOS + 1);
            if (bytes.length > METADADOS || bytes.length != entrada.getSize()) throw new IOException("metadados Office invalidos");
            return bytes;
        }
    }

    private static XMLStreamReader xml(byte[] bytes) throws XMLStreamException {
        var fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        fabrica.setXMLResolver((a,b,c,d) -> { throw new XMLStreamException("referencia externa proibida"); });
        return fabrica.createXMLStreamReader(new ByteArrayInputStream(bytes));
    }
}
