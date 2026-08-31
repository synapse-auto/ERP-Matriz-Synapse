package com.synapse.crm.core.application.lead.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

class PrepararImportacaoLeadsCsvTest {

    private final PrepararImportacaoLeadsCsv importacao = new PrepararImportacaoLeadsCsv("55");

    @Test
    void formatosComESemDdiEMascaraConvergemParaUmLead() throws Exception {
        var resultado = importacao.executar(new StringReader("""
                nome;telefone
                Maria;+55 61 99999-9999
                Maria repetida;5561999999999
                Maria local;(61) 99999-9999
                """));

        assertThat(resultado.totalDeLinhas()).isEqualTo(3);
        assertThat(resultado.aceitos())
                .extracting(PrepararImportacaoLeadsCsv.LeadImportavel::telefone)
                .containsExactly("5561999999999");
        assertThat(resultado.recusados())
                .hasSize(2)
                .allMatch(linha -> linha.motivo().equals("telefone duplicado no arquivo"));
    }

    @Test
    void telefoneSemNonoDigitoEhRecusadoEmVezDeCriarSegundaIdentidade() throws Exception {
        var resultado = importacao.executar(new StringReader("""
                nome,telefone
                Sem DDI,6199999999
                Com DDI,556199999999
                Com nono,61999999999
                """));

        assertThat(resultado.aceitos())
                .extracting(PrepararImportacaoLeadsCsv.LeadImportavel::telefone)
                .containsExactly("5561999999999");
        assertThat(resultado.recusados())
                .extracting(PrepararImportacaoLeadsCsv.LinhaRecusada::motivo)
                .containsExactly(
                        "telefone nacional com dez digitos e ambiguo; confirme o nono digito",
                        "telefone nacional com dez digitos e ambiguo; confirme o nono digito");
    }

    @Test
    void linhasInvalidasSaoRelatadasESeguemParaAProxima() throws Exception {
        var resultado = importacao.executar(new StringReader("""
                nome;telefone
                ;5561999999999
                Curto;123
                Letras;55abc61999999999
                "Aspas sem fim;5561888888888
                Valida;5561777777777
                """));

        assertThat(resultado.aceitos())
                .extracting(PrepararImportacaoLeadsCsv.LeadImportavel::telefone)
                .containsExactly("5561777777777");
        assertThat(resultado.recusados())
                .extracting(PrepararImportacaoLeadsCsv.LinhaRecusada::motivo)
                .containsExactly(
                        "nome vazio",
                        "telefone curto ou ilegivel",
                        "telefone contem letras",
                        "aspas nao fechadas");
    }

    @Test
    void aceitaBomCabecalhoCaseInsensitiveECampoComDelimitadorEntreAspas() throws Exception {
        var resultado = importacao.executar(new StringReader("\uFEFFNome;Telefone;Observacao\n"
                + "\"Cliente; Matriz\";5561666666666;\"uma; observacao\"\n"));

        assertThat(resultado.aceitos())
                .containsExactly(new PrepararImportacaoLeadsCsv.LeadImportavel(
                        "Cliente; Matriz", "5561666666666"));
        assertThat(resultado.recusados()).isEmpty();
    }
}
