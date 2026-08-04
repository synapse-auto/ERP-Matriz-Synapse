package com.synapse.crm.core.interfaces.lembrete;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lembrete.AtualizarLembreteUseCase;
import com.synapse.crm.core.application.lembrete.CriarLembreteUseCase;
import com.synapse.crm.core.application.lembrete.FiltroLembretes;
import com.synapse.crm.core.application.lembrete.ListarLembretesUseCase;
import com.synapse.crm.core.application.lembrete.PaginaLembretes;
import com.synapse.crm.core.application.lembrete.RemoverLembreteUseCase;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.core.domain.lembrete.StatusLembrete;

@RestController
@RequestMapping("/api/v1/lembretes")
class LembreteController {
    private final CriarLembreteUseCase criar;
    private final ListarLembretesUseCase listar;
    private final AtualizarLembreteUseCase atualizar;
    private final RemoverLembreteUseCase remover;
    private final int tamanhoPagina;

    LembreteController(CriarLembreteUseCase criar, ListarLembretesUseCase listar,
            AtualizarLembreteUseCase atualizar, RemoverLembreteUseCase remover,
            @Value("${synapse.suporte.tamanho-pagina}") int tamanhoPagina) {
        this.criar = criar;
        this.listar = listar;
        this.atualizar = atualizar;
        this.remover = remover;
        this.tamanhoPagina = tamanhoPagina;
    }

    @GetMapping
    PaginaResposta listar(@RequestParam(required = false) Instant inicio,
            @RequestParam(required = false) Instant fim,
            @RequestParam(required = false) StatusLembrete status,
            @RequestParam(defaultValue = "0") int pagina) {
        return PaginaResposta.de(listar.executar(new FiltroLembretes(inicio, fim, status, pagina, tamanhoPagina)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Resposta criar(@Valid @RequestBody Criacao requisicao) {
        return criar.executar(requisicao.leadId(), requisicao.texto(), requisicao.dataHora())
                .map(Resposta::de).orElseThrow(LembreteController::naoEncontrado);
    }

    @PutMapping("/{id}")
    Resposta atualizar(@PathVariable UUID id, @Valid @RequestBody Alteracao requisicao) {
        return atualizar.executar(id, requisicao.texto(), requisicao.dataHora(), requisicao.status())
                .map(Resposta::de).orElseThrow(LembreteController::naoEncontrado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable UUID id) {
        if (!remover.executar(id)) throw naoEncontrado();
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lembrete ou lead nao encontrado");
    }

    record Criacao(@NotNull UUID leadId, @NotBlank String texto, @NotNull Instant dataHora) {}
    record Alteracao(@NotBlank String texto, @NotNull Instant dataHora, @NotNull StatusLembrete status) {}
    record Resposta(UUID id, UUID leadId, String leadNome, UUID atendenteId, String atendenteNome,
            String texto, Instant dataHora, boolean origemAutomatica, StatusLembrete status) {
        static Resposta de(Lembrete l) {
            return new Resposta(l.id(), l.leadId(), l.leadNome(), l.atendenteId(), l.atendenteNome(),
                    l.texto(), l.dataHora(), l.origemAutomatica(), l.status());
        }
    }
    record PaginaResposta(List<Resposta> lembretes, int pagina, boolean temMais) {
        static PaginaResposta de(PaginaLembretes p) {
            return new PaginaResposta(p.lembretes().stream().map(Resposta::de).toList(), p.pagina(), p.temMais());
        }
    }
}
