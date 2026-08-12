package com.synapse.crm.relatorios.application.equipe;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.relatorios.application.vendas.AgregacaoDeVendasRepositorio;
import com.synapse.crm.relatorios.domain.equipe.DesempenhoDaEquipe;
import com.synapse.crm.relatorios.domain.equipe.DesempenhoDaEquipe.DesempenhoPorAtendente;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas.VendasPorAtendente;

/** Combina atendimentos com a agregacao canonica de vendas usada pelo Dashboard. */
@Service
public class ObterDesempenhoDaEquipeUseCase {

    private final DesempenhoDaEquipeRepositorio atendimentos;
    private final AgregacaoDeVendasRepositorio vendas;

    public ObterDesempenhoDaEquipeUseCase(
            DesempenhoDaEquipeRepositorio atendimentos,
            AgregacaoDeVendasRepositorio vendas) {
        this.atendimentos = atendimentos;
        this.vendas = vendas;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public DesempenhoDaEquipe executar() {
        Map<java.util.UUID, VendasPorAtendente> vendasPorId = vendas.agregar(List.of(), null)
                .porAtendente()
                .stream()
                .collect(Collectors.toMap(VendasPorAtendente::atendenteId, Function.identity()));
        return new DesempenhoDaEquipe(atendimentos.contarAtendimentos().stream()
                .map(item -> new DesempenhoPorAtendente(
                        item.atendenteId(),
                        item.atendenteNome(),
                        item.atendimentos(),
                        vendasPorId.getOrDefault(
                                        item.atendenteId(),
                                        new VendasPorAtendente(
                                                item.atendenteId(), item.atendenteNome(), 0))
                                .vendas()))
                .toList());
    }
}
