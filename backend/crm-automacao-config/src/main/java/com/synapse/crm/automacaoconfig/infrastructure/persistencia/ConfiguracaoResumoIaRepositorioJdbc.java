package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.ConfiguracaoResumoIaRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoResumoIa;
import com.synapse.crm.automacaoconfig.domain.GatilhoResumo;

@Repository
class ConfiguracaoResumoIaRepositorioJdbc implements ConfiguracaoResumoIaRepositorio {
    private final JdbcTemplate jdbc;

    ConfiguracaoResumoIaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConfiguracaoResumoIa obter() {
        return jdbc.queryForObject(
                "SELECT ativo, gatilho::text, quantidade_mensagens FROM configuracao_resumo_ia WHERE id = 1",
                (rs, rowNum) -> new ConfiguracaoResumoIa(
                        rs.getBoolean("ativo"),
                        GatilhoResumo.valueOf(rs.getString("gatilho")),
                        (Integer) rs.getObject("quantidade_mensagens")));
    }

    @Override
    public ConfiguracaoResumoIa salvar(ConfiguracaoResumoIa configuracao) {
        jdbc.update(
                "UPDATE configuracao_resumo_ia SET ativo = ?, gatilho = ?::gatilho_resumo, quantidade_mensagens = ? WHERE id = 1",
                configuracao.ativo(), configuracao.gatilho().name(), configuracao.quantidadeMensagens());
        return obter();
    }
}
