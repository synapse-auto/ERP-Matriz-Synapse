package com.synapse.crm.relatorios.application.cancelamentos;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.synapse.crm.relatorios.domain.cancelamentos.FiltroCancelamentos;

/** Limites operacionais configuraveis da consulta local. */
@Component
public class PoliticaConsultaCancelamentos {
    private final Clock relogio;
    private final ZoneId fuso;
    private final int tamanhoPadrao, maximoPagina, maximoDias, maximoPaginas, maximoBusca;

    public PoliticaConsultaCancelamentos(Clock relogio,
            @Value("${synapse.tenant.timezone}") String fuso,
            @Value("${synapse.cancelamentos.tamanho-pagina}") int tamanhoPadrao,
            @Value("${synapse.cancelamentos.maximo-pagina}") int maximoPagina,
            @Value("${synapse.cancelamentos.maximo-dias}") int maximoDias,
            @Value("${synapse.cancelamentos.maximo-paginas}") int maximoPaginas,
            @Value("${synapse.cancelamentos.maximo-busca}") int maximoBusca) {
        this.relogio = relogio; this.fuso = ZoneId.of(fuso);
        if (tamanhoPadrao < 1 || tamanhoPadrao > maximoPagina || maximoDias < 31 || maximoPaginas < 1 || maximoBusca < 1)
            throw new IllegalArgumentException("Limites de cancelamentos invalidos");
        this.tamanhoPadrao = tamanhoPadrao; this.maximoPagina = maximoPagina;
        this.maximoDias = maximoDias; this.maximoPaginas = maximoPaginas; this.maximoBusca = maximoBusca;
    }
    public record Configuracao(String fusoHorario, LocalDate inicioPadrao, LocalDate fimPadrao,
            int tamanhoPagina, int maximoPagina, int maximoDias, int maximoPaginas, int maximoBusca) {}

    public Configuracao configuracao() {
        LocalDate hoje = LocalDate.now(relogio.withZone(fuso));
        return new Configuracao(fuso.getId(), hoje.withDayOfMonth(1), hoje, tamanhoPadrao,
                maximoPagina, maximoDias, maximoPaginas, maximoBusca);
    }
    public FiltroCancelamentos filtro(FiltroCancelamentos.BaseTemporal base, LocalDate inicio,
            LocalDate fim, String profissional, String tipo, FiltroCancelamentos.Descricao descricao,
            String nome, int pagina, Integer tamanho) {
        return FiltroCancelamentos.criar(base, inicio, fim, profissional, tipo, descricao, nome,
                pagina, tamanho == null ? tamanhoPadrao : tamanho, fuso,
                maximoPagina, maximoDias, maximoPaginas, maximoBusca);
    }
}
