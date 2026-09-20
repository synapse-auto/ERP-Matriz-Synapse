package com.synapse.crm.automacaoconfig.application.festivas;

import java.time.Clock;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;

/**
 * As datas festivas de hoje — {@code GET /internal/v1/mensagens-festivas/hoje}.
 *
 * <p>Leitura da Automacao, separada de {@link GerenciarMensagensFestivasUseCase} de proposito: o
 * CRUD e da gestao, autorizado por papel; isto aqui e consumo do contrato interno, e misturar os
 * dois faria uma mudanca de autorizacao administrativa mexer no que a Automacao enxerga.
 *
 * <p>O ano do cadastro e ignorado: uma data festiva cadastrada uma vez vale todo ano seguinte, sem
 * ninguem reeditar em dezembro. Cadastro duplicado no mesmo dia devolve os dois — escolher um
 * arbitrariamente esconderia o erro de cadastro de quem pode corrigi-lo.
 *
 * <p>A tabela tem poucas linhas por instancia, entao o filtro e em memoria: nao compensa um SQL novo
 * com extracao de mes e dia para percorrer uma lista que cabe num punhado de datas.
 */
@Service
public class MensagensFestivasDoDiaUseCase {

    private final MensagemFestivaRepositorio mensagens;
    private final Clock relogio;
    private final ZoneId fusoDoTenant;

    public MensagensFestivasDoDiaUseCase(
            MensagemFestivaRepositorio mensagens, Clock relogio, ZoneId fusoDoTenant) {
        this.mensagens = mensagens;
        this.relogio = relogio;
        this.fusoDoTenant = fusoDoTenant;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<MensagemFestiva> executar() {
        MonthDay hoje = MonthDay.from(relogio.instant().atZone(fusoDoTenant));
        return mensagens.listarTodas().stream()
                .filter(MensagemFestiva::ativo)
                .filter(mensagem -> mensagem.data() != null && MonthDay.from(mensagem.data()).equals(hoje))
                .sorted(Comparator.comparing(MensagemFestiva::titulo, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}
