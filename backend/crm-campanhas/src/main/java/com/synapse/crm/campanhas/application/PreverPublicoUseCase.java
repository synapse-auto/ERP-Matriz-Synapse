package com.synapse.crm.campanhas.application;

import java.time.ZoneId;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.application.PublicoRepositorio.ContagemDoPublico;
import com.synapse.crm.campanhas.domain.FiltroDePublico;

/** Previa do publico: so contagens (total, elegiveis, excluidos por motivo), nunca a lista materializada. */
@Service
public class PreverPublicoUseCase {

    private final PublicoRepositorio publico;
    private final ConfiguracaoDeCampanhas configuracao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;
    private final ZoneId fuso;

    public PreverPublicoUseCase(
            PublicoRepositorio publico,
            ConfiguracaoDeCampanhas configuracao,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes,
            ZoneId fuso) {
        this.publico = publico;
        this.configuracao = configuracao;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
        this.fuso = fuso;
    }

    @PreAuthorize(PermissoesDeCampanha.LEITURA_DE_DESTINATARIOS)
    public ContagemDoPublico executar(FiltroDePublico filtro) {
        disponibilidade.exigir();
        int cooldown = transacoes.noChatSomenteLeitura(() -> configuracao.atuais().cooldownProativoHoras());
        return transacoes.noGeralSomenteLeitura(() -> publico.prever(filtro, cooldown, fuso));
    }
}
