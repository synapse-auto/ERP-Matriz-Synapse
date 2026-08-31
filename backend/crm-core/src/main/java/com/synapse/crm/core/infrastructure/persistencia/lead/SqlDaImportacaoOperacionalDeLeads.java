package com.synapse.crm.core.infrastructure.persistencia.lead;

/** SQL compartilhado pelo comando operacional e pelo teste integrado da importacao. */
public final class SqlDaImportacaoOperacionalDeLeads {

    public static final String TABELA_TEMPORARIA = "importacao_lead_csv";

    private SqlDaImportacaoOperacionalDeLeads() {}

    public static String criarTabelaTemporaria() {
        return "CREATE TEMP TABLE " + TABELA_TEMPORARIA
                + " (nome TEXT NOT NULL, telefone TEXT NOT NULL) ON COMMIT DROP";
    }

    public static String inserirLeads() {
        return """
                INSERT INTO lead (nome, telefone, status_basico, atendente_responsavel_id)
                SELECT nome, telefone, 'IA'::status_basico_lead, NULL
                  FROM importacao_lead_csv
                ON CONFLICT (telefone) WHERE telefone IS NOT NULL DO NOTHING
                """;
    }
}
