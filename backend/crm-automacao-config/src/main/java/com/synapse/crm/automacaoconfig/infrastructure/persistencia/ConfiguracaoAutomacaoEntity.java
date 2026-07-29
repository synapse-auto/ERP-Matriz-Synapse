package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;

/** Mapeamento JPA de {@code configuracao_automacao} (V7). Pacote-privada: so o adaptador a toca. */
@Entity
@Table(name = "configuracao_automacao")
class ConfiguracaoAutomacaoEntity {

    @Id
    @Column(name = "chave")
    private String chave;

    @Column(name = "valor", nullable = false)
    private String valor;

    @Column(name = "unidade")
    private String unidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoConfiguracaoAutomacao tipo;

    @Column(name = "valor_min")
    private BigDecimal valorMin;

    @Column(name = "valor_max")
    private BigDecimal valorMax;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "atualizado_por_id")
    private UUID atualizadoPorId;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ConfiguracaoAutomacaoEntity() {
        // exigido pelo JPA
    }

    ConfiguracaoAutomacao paraDominio() {
        return new ConfiguracaoAutomacao(
                chave, valor, unidade, tipo, valorMin, valorMax, descricao, atualizadoPorId, atualizadoEm);
    }

    void aplicar(ConfiguracaoAutomacao configuracao) {
        this.chave = configuracao.chave();
        this.valor = configuracao.valor();
        this.unidade = configuracao.unidade();
        this.tipo = configuracao.tipo();
        this.valorMin = configuracao.valorMin();
        this.valorMax = configuracao.valorMax();
        this.descricao = configuracao.descricao();
        this.atualizadoPorId = configuracao.atualizadoPorId();
        this.atualizadoEm = configuracao.atualizadoEm();
    }
}
