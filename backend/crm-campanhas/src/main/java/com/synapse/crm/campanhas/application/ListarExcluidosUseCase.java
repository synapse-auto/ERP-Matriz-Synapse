package com.synapse.crm.campanhas.application;

import java.time.ZoneId;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.application.PublicoRepositorio.Excluido;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;

/** "Ver a lista de excluidos" do passo Publico: ate 200 contatos de um motivo, para conferir antes de iniciar. */
@Service
public class ListarExcluidosUseCase {

    public static final int LIMITE = 200;

    private final PublicoRepositorio publico;
    private final ConfiguracaoDeCampanhas configuracao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;
    private final ZoneId fuso;

    public ListarExcluidosUseCase(
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
    public List<Excluido> executar(FiltroDePublico filtro, MotivoDoDestinatario motivo) {
        disponibilidade.exigir();
        if (motivo == null || !motivo.exclusaoDoPublico()) {
            throw new CampanhaInvalidaException("MOTIVO_INVALIDO", "informe um motivo de exclusao do publico");
        }
        int cooldown = transacoes.noChatSomenteLeitura(() -> configuracao.atuais().cooldownProativoHoras());
        return transacoes.noGeralSomenteLeitura(() -> publico.excluidos(filtro, cooldown, fuso, motivo, LIMITE));
    }
}
