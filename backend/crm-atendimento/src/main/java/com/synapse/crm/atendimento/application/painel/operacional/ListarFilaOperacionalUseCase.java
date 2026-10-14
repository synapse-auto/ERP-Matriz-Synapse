package com.synapse.crm.atendimento.application.painel.operacional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ListarFilaOperacionalUseCase {
    private final PainelOperacionalRepositorio repositorio;
    private final UsuarioContext usuarios;
    private final Clock relogio;
    private final ZoneId fuso;

    public ListarFilaOperacionalUseCase(PainelOperacionalRepositorio repositorio, UsuarioContext usuarios,
            Clock relogio, @Value("${synapse.tenant.timezone}") String fuso) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.relogio = relogio;
        this.fuso = ZoneId.of(fuso);
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.painel_operacional')")
    @Transactional(readOnly = true)
    public PainelOperacional.Pagina executar(FiltroOperacional filtro, int tamanho, String cursor) {
        if (tamanho < 1 || tamanho > 100) throw new IllegalArgumentException("tamanho fora de 1..100");
        var usuario = usuarios.atual();
        Instant agora = relogio.instant();
        var dia = agora.atZone(fuso).toLocalDate();
        Instant depois = null;
        UUID id = null;
        if (cursor != null) {
            try {
                String[] partes = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
                if (partes.length != 2) throw new IllegalArgumentException("cursor inválido");
                depois = Instant.parse(partes[0]);
                id = UUID.fromString(partes[1]);
            } catch (RuntimeException erro) {
                throw new IllegalArgumentException("cursor inválido");
            }
        }
        var linhas = repositorio.listar(VisibilidadeLead.de(usuario), usuario.id(), agora,
                dia.atStartOfDay(fuso).toInstant(), dia.plusDays(1).atStartOfDay(fuso).toInstant(),
                repositorio.limiteMinutos(), filtro, depois, id, tamanho + 1);
        boolean mais = linhas.size() > tamanho;
        var pagina = java.util.List.copyOf(linhas.subList(0, Math.min(tamanho, linhas.size())));
        String proximo = null;
        if (mais) {
            var ultima = pagina.getLast();
            proximo = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (ultima.atividadeEm() + "|" + ultima.atendimentoId()).getBytes(StandardCharsets.UTF_8));
        }
        return new PainelOperacional.Pagina(pagina, proximo);
    }
}
