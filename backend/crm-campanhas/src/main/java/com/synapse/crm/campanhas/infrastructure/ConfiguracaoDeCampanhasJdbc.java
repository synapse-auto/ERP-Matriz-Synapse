package com.synapse.crm.campanhas.infrastructure;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhas;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.PoliticaDePausa;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Parametros de campanhas em {@code configuracao_automacao}. Leitura numa consulta so, a cada chamada (sem cache:
 * o administrador altera e o proximo ciclo ja usa). Valor ausente ou invalido cai no padrao MAIS CONSERVADOR:
 * envio global desligado e limites do seed. Erro de digitacao nunca liberar mais envio.
 */
@Repository
class ConfiguracaoDeCampanhasJdbc implements ConfiguracaoDeCampanhas {

    static final String ENVIO_HABILITADO = "campanhas.envio_habilitado";
    static final String TETO_DIARIO = "campanhas.teto_diario_instancia";
    static final String LIMITE_PADRAO = "campanhas.limite_diario_padrao";
    static final String LIMITE_META = "campanhas.limite_meta_informado";
    static final String LIMIAR_FALHA = "campanhas.pausa.limiar_falha_pct";
    static final String JANELA_ENVIOS = "campanhas.pausa.janela_envios";
    static final String MINIMO_AMOSTRA = "campanhas.pausa.minimo_amostra";
    static final String CONFERENCIA_MINUTOS = "campanhas.conferencia_apos_minutos";
    static final String RESPONDEU_DIAS = "campanhas.respondeu_janela_dias";
    static final String COOLDOWN_PROATIVO = "automacao_proativa.cooldown_horas";

    private static final String ALERTA = "[ALERTA_CONFIG_CAMPANHAS]";
    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoDeCampanhasJdbc.class);

    private final JdbcTemplate chat;

    ConfiguracaoDeCampanhasJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Parametros atuais() {
        TransacaoObrigatoria.exigir("ler configuracao de campanhas");
        Map<String, String> valores = new HashMap<>();
        chat.query(
                "SELECT chave, valor FROM configuracao_automacao WHERE chave LIKE 'campanhas.%' OR chave = ?",
                rs -> {
                    valores.put(rs.getString("chave"), rs.getString("valor"));
                },
                COOLDOWN_PROATIVO);
        return new Parametros(
                booleano(valores, ENVIO_HABILITADO),
                inteiro(valores, TETO_DIARIO, 1),
                inteiro(valores, LIMITE_PADRAO, 1),
                inteiro(valores, LIMITE_META, 0),
                new PoliticaDePausa(
                        inteiro(valores, LIMIAR_FALHA, 20),
                        inteiro(valores, JANELA_ENVIOS, 50),
                        inteiro(valores, MINIMO_AMOSTRA, 20)),
                inteiro(valores, CONFERENCIA_MINUTOS, 30),
                inteiro(valores, RESPONDEU_DIAS, 7),
                inteiro(valores, COOLDOWN_PROATIVO, 0));
    }

    @Override
    public void atualizar(Atualizacao a, UUID usuarioId) {
        TransacaoObrigatoria.exigir("atualizar configuracao de campanhas");
        if (a.envioHabilitado() != null) {
            gravar(ENVIO_HABILITADO, Boolean.toString(a.envioHabilitado()), usuarioId);
        }
        if (a.tetoDiarioDaInstancia() != null) {
            gravarInteiro(TETO_DIARIO, a.tetoDiarioDaInstancia(), usuarioId);
        }
        if (a.limiteDiarioPadrao() != null) {
            gravarInteiro(LIMITE_PADRAO, a.limiteDiarioPadrao(), usuarioId);
        }
        if (a.limiteMetaInformado() != null) {
            gravarInteiro(LIMITE_META, a.limiteMetaInformado(), usuarioId);
        }
        if (a.limiarDeFalhaPorCento() != null) {
            gravarInteiro(LIMIAR_FALHA, a.limiarDeFalhaPorCento(), usuarioId);
        }
        if (a.janelaDeEnvios() != null) {
            gravarInteiro(JANELA_ENVIOS, a.janelaDeEnvios(), usuarioId);
        }
        if (a.minimoDeAmostra() != null) {
            gravarInteiro(MINIMO_AMOSTRA, a.minimoDeAmostra(), usuarioId);
        }
        Parametros resultante = atuais();
        if (resultante.limiteDiarioPadrao() > resultante.tetoDiarioDaInstancia()) {
            // Lanca dentro da transacao do caso de uso: a alteracao inteira e desfeita.
            throw new CampanhaInvalidaException(
                    "LIMITE_PADRAO_ACIMA_DO_TETO", "o limite diario padrao nao pode passar do teto da instancia");
        }
    }

    private void gravarInteiro(String chave, int valor, UUID usuarioId) {
        gravar(chave, Integer.toString(valor), usuarioId);
    }

    /** So grava dentro da faixa da propria linha (valor_min/valor_max semeados na migration). */
    private void gravar(String chave, String valor, UUID usuarioId) {
        List<BigDecimal[]> faixas = chat.query(
                "SELECT valor_min, valor_max FROM configuracao_automacao WHERE chave = ?",
                (rs, linha) -> new BigDecimal[] {rs.getBigDecimal("valor_min"), rs.getBigDecimal("valor_max")},
                chave);
        if (faixas.isEmpty()) {
            throw new CampanhaInvalidaException("CONFIGURACAO_INVALIDA", "parametro desconhecido: " + chave);
        }
        BigDecimal[] faixa = faixas.get(0);
        if (!valor.equals("true") && !valor.equals("false")) {
            BigDecimal numero = new BigDecimal(valor);
            if ((faixa[0] != null && numero.compareTo(faixa[0]) < 0) || (faixa[1] != null && numero.compareTo(faixa[1]) > 0)) {
                throw new CampanhaInvalidaException(
                        "CONFIGURACAO_FORA_DA_FAIXA",
                        chave + " precisa estar entre " + faixa[0] + " e " + faixa[1]);
            }
        }
        chat.update(
                "UPDATE configuracao_automacao SET valor = ?, atualizado_por_id = ?, atualizado_em = now() WHERE chave = ?",
                valor,
                usuarioId,
                chave);
    }

    private static boolean booleano(Map<String, String> valores, String chave) {
        String valor = valores.get(chave);
        if ("true".equalsIgnoreCase(valor == null ? null : valor.trim())) {
            return true;
        }
        if (!"false".equalsIgnoreCase(valor == null ? null : valor.trim())) {
            log.error("{} parametro {} ausente ou invalido; vale o padrao conservador (desligado)", ALERTA, chave);
        }
        return false;
    }

    private static int inteiro(Map<String, String> valores, String chave, int padrao) {
        String valor = valores.get(chave);
        if (valor == null) {
            log.warn("{} parametro {} ausente; vale o padrao {}", ALERTA, chave, padrao);
            return padrao;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException invalido) {
            log.error("{} parametro {} invalido; vale o padrao {}", ALERTA, chave, padrao);
            return padrao;
        }
    }
}
