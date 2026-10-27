package com.synapse.crm.sharedkernel.midia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentoWordTest {
    @Test void preservaEspacosAcentosEMaiusculasSemAceitarExtensaoFalsa() {
        assertThat(DocumentoWord.nomeCompativel("Relatório final.DOCX", DocumentoWord.DOCX)).isTrue();
        assertThat(DocumentoWord.nomeSeguro("Relatório final.DOCX", DocumentoWord.DOCX)).isEqualTo("Relatório final.DOCX");
        assertThat(DocumentoWord.nomeCompativel("renomeado.docx", "application/pdf")).isFalse();
        assertThat(DocumentoWord.nomeCompativel("renomeado.doc", "text/plain")).isFalse();
        assertThat(DocumentoWord.nomeCompativel("renomeado.docx", DocumentoWord.DOC)).isFalse();
        assertThat(DocumentoWord.nomeCompativel("renomeado.pdf", DocumentoWord.DOCX)).isFalse();
        assertThat(DocumentoWord.nomeCompativel("macro.docm", DocumentoWord.DOCX)).isFalse();
        assertThat(DocumentoWord.nomeCompativel("outro.pdf", "application/pdf")).isTrue();
    }

    @Test void fallbackEControlesNaoGeramNomePerigoso() {
        assertThat(DocumentoWord.nomeSeguro(null, DocumentoWord.DOC)).isEqualTo("documento.doc");
        assertThat(DocumentoWord.nomeSeguro("../Pasta/Relatório\r\n final.docx", DocumentoWord.DOCX)).isEqualTo("Relatório__ final.docx");
        assertThat(DocumentoWord.inspecionar("novo.DOC", "application/octet-stream")).isTrue();
    }
}
