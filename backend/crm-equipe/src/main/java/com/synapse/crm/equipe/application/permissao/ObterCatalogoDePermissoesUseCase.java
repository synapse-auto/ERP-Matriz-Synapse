package com.synapse.crm.equipe.application.permissao;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.Modulo;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;

/** Catalogo disponivel nesta instancia: modulos e capacidades cuja flag esta ligada. */
@Service
public class ObterCatalogoDePermissoesUseCase {

    private final ResolvedorDePermissoesEfetivas resolvedor;

    public ObterCatalogoDePermissoesUseCase(ResolvedorDePermissoesEfetivas resolvedor) {
        this.resolvedor = resolvedor;
    }

    @PreAuthorize(AutorizacaoDeGestao.LER)
    public Catalogo executar() {
        Set<String> flags = resolvedor.flagsHabilitadas();
        List<Modulo> modulos = Arrays.stream(Modulo.values())
                .filter(m -> PoliticaDePermissoes.disponivel(m, flags))
                .toList();
        List<Capacidade> capacidades = Arrays.stream(Capacidade.values())
                .filter(c -> PoliticaDePermissoes.disponivel(c.modulo(), flags))
                .toList();
        return new Catalogo(modulos, capacidades);
    }

    public record Catalogo(List<Modulo> modulos, List<Capacidade> capacidades) {}
}
