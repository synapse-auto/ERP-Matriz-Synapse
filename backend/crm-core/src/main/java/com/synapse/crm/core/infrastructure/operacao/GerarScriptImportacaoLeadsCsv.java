package com.synapse.crm.core.infrastructure.operacao;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.synapse.crm.core.application.lead.importacao.PrepararImportacaoLeadsCsv;
import com.synapse.crm.core.application.lead.importacao.PrepararImportacaoLeadsCsv.LeadImportavel;
import com.synapse.crm.core.application.lead.importacao.PrepararImportacaoLeadsCsv.Resultado;
import com.synapse.crm.core.infrastructure.persistencia.lead.SqlDaImportacaoOperacionalDeLeads;

/** Gera a entrada atomica consumida pelo psql do script operacional. */
public final class GerarScriptImportacaoLeadsCsv {

    private GerarScriptImportacaoLeadsCsv() {}

    public static void main(String[] argumentos) throws IOException {
        if (argumentos.length != 3 || !("SIMULAR".equals(argumentos[2]) || "APLICAR".equals(argumentos[2]))) {
            System.err.println("Uso: GerarScriptImportacaoLeadsCsv <arquivo.csv> <ddi> <SIMULAR|APLICAR>");
            System.exit(2);
        }
        Path arquivo = Path.of(argumentos[0]);
        Resultado resultado;
        try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            resultado = new PrepararImportacaoLeadsCsv(argumentos[1]).executar(leitor);
        }
        for (var recusado : resultado.recusados()) {
            System.err.printf("RECUSADA linha=%d motivo=%s%n", recusado.linha(), recusado.motivo());
        }
        escreverPsql(System.out, resultado, "APLICAR".equals(argumentos[2]));
    }

    static void escreverPsql(PrintStream saida, Resultado resultado, boolean aplicar) {
        saida.println("\\set ON_ERROR_STOP on");
        saida.println("BEGIN;");
        saida.println(SqlDaImportacaoOperacionalDeLeads.criarTabelaTemporaria() + ";");
        saida.println("\\copy " + SqlDaImportacaoOperacionalDeLeads.TABELA_TEMPORARIA
                + " (nome, telefone) FROM STDIN WITH (FORMAT csv, HEADER true)");
        saida.println("nome,telefone");
        for (LeadImportavel lead : resultado.aceitos()) {
            saida.println(csv(lead.nome()) + "," + csv(lead.telefone()));
        }
        saida.println("\\.");
        saida.println("SELECT 'IMPORTACAO_LINHAS=' || " + resultado.totalDeLinhas() + ";");
        saida.println("SELECT 'IMPORTACAO_VALIDAS=' || count(*) FROM importacao_lead_csv;");
        saida.println("SELECT 'IMPORTACAO_JA_EXISTIAM=' || count(*) FROM importacao_lead_csv i JOIN lead l USING (telefone);");
        saida.println("WITH inseridos AS (" + SqlDaImportacaoOperacionalDeLeads.inserirLeads()
                + " RETURNING 1) SELECT 'IMPORTACAO_INSERIDAS=' || count(*) FROM inseridos;");
        saida.println("SELECT 'IMPORTACAO_RECUSADAS=' || " + resultado.recusados().size() + ";");
        saida.println(aplicar ? "COMMIT;" : "ROLLBACK;");
    }

    private static String csv(String valor) {
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
