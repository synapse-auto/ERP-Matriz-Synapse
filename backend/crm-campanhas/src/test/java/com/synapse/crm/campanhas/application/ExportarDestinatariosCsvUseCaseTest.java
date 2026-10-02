package com.synapse.crm.campanhas.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios.Linha;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;

class ExportarDestinatariosCsvUseCaseTest {

    @Test
    @DisplayName("celula com virgula, aspas ou quebra de linha vai entre aspas, com as aspas duplicadas")
    void escapaParaCsv() {
        assertThat(ExportarDestinatariosCsvUseCase.celula("Maria")).isEqualTo("Maria");
        assertThat(ExportarDestinatariosCsvUseCase.celula("Silva, Maria")).isEqualTo("\"Silva, Maria\"");
        assertThat(ExportarDestinatariosCsvUseCase.celula("Ana \"Nana\"")).isEqualTo("\"Ana \"\"Nana\"\"\"");
        assertThat(ExportarDestinatariosCsvUseCase.celula("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(ExportarDestinatariosCsvUseCase.celula(null)).isEmpty();
    }

    @Test
    @DisplayName("nome que comeca com =, +, - ou @ vira texto: nao executa como formula no Excel")
    void neutralizaInjecaoDeFormula() {
        assertThat(ExportarDestinatariosCsvUseCase.celula("=HYPERLINK(\"http://x\")")).startsWith("\"'=");
        assertThat(ExportarDestinatariosCsvUseCase.celula("+55 61")).isEqualTo("'+55 61");
        assertThat(ExportarDestinatariosCsvUseCase.celula("-1")).isEqualTo("'-1");
        assertThat(ExportarDestinatariosCsvUseCase.celula("@cmd")).isEqualTo("'@cmd");
    }

    @Test
    @DisplayName("a linha segue a ordem do cabecalho, com campos vazios para o que nao aconteceu")
    void linhaNaOrdemDoCabecalho() {
        Linha linha = new Linha(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Maria",
                "5561912345678",
                StatusDoDestinatario.FALHA,
                MotivoDoDestinatario.ENVIO_NAO_CONFIRMADO,
                131048,
                Instant.parse("2026-10-05T12:00:00Z"),
                null,
                null,
                null,
                Instant.parse("2026-10-05T13:00:00Z"));

        assertThat(ExportarDestinatariosCsvUseCase.linha(linha))
                .isEqualTo(
                        "Maria,5561912345678,FALHA,ENVIO_NAO_CONFIRMADO,131048,2026-10-05T12:00:00Z,,,,2026-10-05T13:00:00Z");
        assertThat(ExportarDestinatariosCsvUseCase.CABECALHO.split(",")).hasSize(10);
    }
}
