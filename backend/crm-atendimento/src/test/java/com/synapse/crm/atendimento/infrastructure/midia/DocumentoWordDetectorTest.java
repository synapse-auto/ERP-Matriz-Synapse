package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.synapse.crm.sharedkernel.midia.DocumentoWord;

class DocumentoWordDetectorTest {
    @TempDir Path pasta;
    private final TikaDetectorDeTipoReal detector = new TikaDetectorDeTipoReal();
    private static final String TIPOS = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"" + DocumentoWord.DOCX + ".main+xml\"/></Types>";
    private static final String RELACOES = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>";
    private static final String DOCUMENTO = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Teste sintético</w:t></w:r></w:p></w:body></w:document>";

    @Test void docRealVazioEDocxMinimoNaoDependemDoNomeOuMimeDeclarado() throws Exception {
        byte[] doc;
        try (var recurso = getClass().getResourceAsStream("/word/vazio.doc")) { doc = recurso.readAllBytes(); }
        conferir(doc, DocumentoWord.DOC);
        conferir(zip(Map.of("[Content_Types].xml", TIPOS, "_rels/.rels", RELACOES, "word/document.xml", DOCUMENTO)), DocumentoWord.DOCX);
    }

    @ParameterizedTest @MethodSource("pacotesInvalidos")
    void zipOuPacoteDisfarcadoNaoViraWord(Map<String,String> entradas) throws Exception {
        byte[] bytes = zip(entradas);
        assertThat(detector.detectar(bytes)).isNotEqualTo(DocumentoWord.DOCX);
        assertThat(detector.detectar(Files.write(pasta.resolve("forjado.docx"), bytes))).isNotEqualTo(DocumentoWord.DOCX);
    }

    static Stream<Map<String,String>> pacotesInvalidos() {
        return Stream.of(
            Map.of("arquivo.txt", "zip comum"),
            Map.of("[Content_Types].xml", TIPOS),
            Map.of("word/document.xml", DOCUMENTO),
            Map.of("[Content_Types].xml", "<!--" + TIPOS + "-->", "_rels/.rels", RELACOES, "word/document.xml", DOCUMENTO),
            Map.of("[Content_Types].xml", TIPOS, "_rels/.rels", RELACOES.replace("word/document.xml", "outro.xml"), "word/document.xml", DOCUMENTO),
            Map.of("[Content_Types].xml", TIPOS, "_rels/.rels", RELACOES.replace("Target=", "TargetMode=\"External\" Target="), "word/document.xml", DOCUMENTO),
            Map.of("[Content_Types].xml", TIPOS, "_rels/.rels", RELACOES, "word/document.xml", "<raiz/>"),
            Map.of("[Content_Types].xml", TIPOS, "_rels/.rels", RELACOES, "word/document.xml", "<w:document"),
            Map.of("[Content_Types].xml", TIPOS.replace(DocumentoWord.DOCX + ".main+xml", "application/vnd.ms-word.document.macroEnabled.main+xml"), "_rels/.rels", RELACOES, "word/document.xml", DOCUMENTO),
            Map.of("[Content_Types].xml", TIPOS, "_rels/.rels", RELACOES, "word/document.xml", DOCUMENTO, "word/vbaProject.bin", "nao executar"),
            Map.of("[Content_Types].xml", "<!DOCTYPE Types [<!ENTITY x SYSTEM 'file:///nao-ler'>]>" + TIPOS.replace("</Types>", "&x;</Types>"), "_rels/.rels", RELACOES, "word/document.xml", DOCUMENTO));
    }

    @Test void vazioTruncadoEZipSemDiretorioNaoSaoWord() throws Exception {
        byte[] doc;
        try (var recurso = getClass().getResourceAsStream("/word/vazio.doc")) { doc = recurso.readAllBytes(); }
        for (byte[] bytes : new byte[][]{new byte[0], Arrays.copyOf(doc, 128),
                Arrays.copyOf(zip(Map.of("[Content_Types].xml", TIPOS)), 100)}) {
            assertThat(detector.detectar(bytes)).isNotIn(DocumentoWord.DOC, DocumentoWord.DOCX);
        }
    }

    @Test void oleGenericoExcelOuWordDocumentSemFibNaoSaoDoc() throws Exception {
        for (String nome : new String[]{"Workbook", "WordDocument"}) {
            try (var ole = new POIFSFileSystem(); var bytes = new ByteArrayOutputStream()) {
                ole.createDocument(new ByteArrayInputStream(new byte[4096]), nome);
                ole.writeFilesystem(bytes);
                assertThat(detector.detectar(bytes.toByteArray())).isNotEqualTo(DocumentoWord.DOC);
            }
        }
    }

    @Test void naoDescomprimeImagemGrandeAntesDosMetadados() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            adicionar(zip, "word/media/imagem.png", "a".repeat(2 * 1024 * 1024));
            adicionar(zip, "[Content_Types].xml", TIPOS);
            adicionar(zip, "_rels/.rels", RELACOES);
            adicionar(zip, "word/document.xml", DOCUMENTO);
        }
        conferir(bytes.toByteArray(), DocumentoWord.DOCX);
    }

    @Test void metadadosComMaisDoOrcamentoNaoSaoInspecionados() throws Exception {
        assertThat(detector.detectar(zip(Map.of("[Content_Types].xml", TIPOS + " ".repeat(1024 * 1024),
                "_rels/.rels", RELACOES, "word/document.xml", DOCUMENTO)))).isNotEqualTo(DocumentoWord.DOCX);
    }

    private void conferir(byte[] bytes, String mime) throws Exception {
        assertThat(detector.detectar(bytes)).isEqualTo(mime);
        assertThat(detector.detectar(Files.write(pasta.resolve("sem-extensao"), bytes))).isEqualTo(mime);
    }
    private static byte[] zip(Map<String,String> entradas) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) { for (var entrada : entradas.entrySet()) adicionar(zip, entrada.getKey(), entrada.getValue()); }
        return bytes.toByteArray();
    }
    private static void adicionar(ZipOutputStream zip, String nome, String texto) throws Exception {
        zip.putNextEntry(new ZipEntry(nome)); zip.write(texto.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
}
