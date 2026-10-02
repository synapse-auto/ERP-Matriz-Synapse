package com.synapse.crm.campanhas.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Quando o sistema para uma campanha sozinho. Os limiares vem da configuracao; os codigos de erro da Meta
 * vem da documentacao oficial de codigos de erro da Cloud API (consultada em 01/10/2026).
 */
public record PoliticaDePausa(int limiarDeFalhaPorCento, int janelaDeEnvios, int minimoDeAmostra) {

    public static final String MOTIVO_TAXA_DE_FALHA = "TAXA_DE_FALHA";
    public static final String MOTIVO_ERRO_DA_META = "ERRO_DA_META";
    public static final String MOTIVO_TEMPLATE_INDISPONIVEL = "TEMPLATE_INDISPONIVEL";
    public static final String MOTIVO_CANAL_SEM_CAMPANHA = "CANAL_SEM_CAMPANHA";

    /**
     * Codigos que dizem que continuar piora a situacao do numero ou que o template nao serve mais:
     * 130429 limite de vazao, 131048 limite por spam, 131049 mensagem nao entregue para manter a saude do
     * ecossistema, 80007 limite da conta, 132015/132016 template pausado ou desativado por baixa qualidade e
     * 132000/132012 parametros que nao batem com o template (todo envio seguinte falharia igual).
     * 131056 (limite por par de contatos) e 131026 (nao entregavel) sao por destinatario: so entram na taxa.
     */
    public static final Set<Integer> CODIGOS_DE_PARADA_IMEDIATA =
            Set.of(130429, 131048, 131049, 80007, 132015, 132016, 132000, 132012);

    /** Desfechos recentes da campanha: envios resolvidos (aceitos ou falhos) e quantos falharam. */
    public record Desfechos(int total, int falhas) {}

    public static boolean paraImediatamente(Integer codigoDeErro) {
        return codigoDeErro != null && CODIGOS_DE_PARADA_IMEDIATA.contains(codigoDeErro);
    }

    public static String motivoPorCodigo(int codigoDeErro) {
        return MOTIVO_ERRO_DA_META + ":" + codigoDeErro;
    }

    /** Motivo de pausa pela taxa de falha, ou vazio enquanto a amostra e curta ou a taxa e aceitavel. */
    public Optional<String> avaliarTaxa(Desfechos desfechos) {
        if (desfechos.total() < minimoDeAmostra || desfechos.total() == 0) {
            return Optional.empty();
        }
        long porCento = Math.round(100.0 * desfechos.falhas() / desfechos.total());
        return porCento >= limiarDeFalhaPorCento
                ? Optional.of(MOTIVO_TAXA_DE_FALHA + ":" + porCento)
                : Optional.empty();
    }
}
