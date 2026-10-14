package com.synapse.crm.atendimento.application.painel.operacional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ObterPainelOperacionalUseCase {
    private final PainelOperacionalRepositorio repositorio;
    private final UsuarioContext usuarios;
    private final Clock relogio;
    private final ZoneId fuso;

    public ObterPainelOperacionalUseCase(PainelOperacionalRepositorio repositorio, UsuarioContext usuarios,
            Clock relogio, @Value("${synapse.tenant.timezone}") String fuso) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.relogio = relogio;
        this.fuso = ZoneId.of(fuso);
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.painel_operacional')")
    @Transactional(readOnly = true)
    public PainelOperacional executar(FiltroOperacional filtro) {
        var usuario = usuarios.atual();
        var visibilidade = VisibilidadeLead.de(usuario);
        Instant agora = relogio.instant();
        var dia = agora.atZone(fuso).toLocalDate();
        Instant inicio = dia.atStartOfDay(fuso).toInstant();
        Instant fim = dia.plusDays(1).atStartOfDay(fuso).toInstant();
        int limite = repositorio.limiteMinutos();
        var equipe = repositorio.agregar(visibilidade, usuario.id(), agora, inicio, fim, limite, filtro);
        // Próxima passagem do limite deriva do mesmo snapshot, sem segunda leitura da equipe.
        Instant proxima = equipe.stream().map(PainelOperacional.Linha::proximaTransicaoEm)
                .filter(java.util.Objects::nonNull)
                .filter(i -> i.isAfter(agora)).min(Instant::compareTo).orElse(fim);
        return new PainelOperacional(agora, inicio, fim, fuso.getId(), limite,
                proxima.isBefore(fim) ? proxima : fim, equipe);
    }
}
