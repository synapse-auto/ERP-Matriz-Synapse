package com.synapse.crm.equipe.interfaces;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.equipe.application.usuario.AtualizarMinhaPresencaUseCase;
import com.synapse.crm.equipe.application.usuario.AtualizarUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.CriarUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.DesativarUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.EmailDeUsuarioEmUsoException;
import com.synapse.crm.equipe.application.usuario.ListarUsuariosUseCase;
import com.synapse.crm.equipe.application.usuario.ObterMinhaPresencaUseCase;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Consulta da equipe. A restricao por papel esta no caso de uso, nao aqui. */
@RestController
@RequestMapping("/api/v1/usuarios")
class UsuarioController {

    private final ListarUsuariosUseCase listar;
    private final CriarUsuarioUseCase criar;
    private final AtualizarUsuarioUseCase atualizar;
    private final DesativarUsuarioUseCase desativar;
    private final ObterMinhaPresencaUseCase obterPresenca;
    private final AtualizarMinhaPresencaUseCase atualizarPresenca;

    UsuarioController(ListarUsuariosUseCase listar, CriarUsuarioUseCase criar,
            AtualizarUsuarioUseCase atualizar, DesativarUsuarioUseCase desativar,
            ObterMinhaPresencaUseCase obterPresenca, AtualizarMinhaPresencaUseCase atualizarPresenca) {
        this.listar = listar;
        this.criar = criar;
        this.atualizar = atualizar;
        this.desativar = desativar;
        this.obterPresenca = obterPresenca;
        this.atualizarPresenca = atualizarPresenca;
    }

    @GetMapping
    List<UsuarioResposta> listar() {
        return listar.executar().stream().map(UsuarioResposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioResposta criar(@Valid @RequestBody Criacao requisicao) {
        return UsuarioResposta.de(criar.executar(requisicao.nome(), requisicao.email(),
                requisicao.senha(), requisicao.papel()));
    }

    @PutMapping("/{id}")
    UsuarioResposta atualizar(@PathVariable UUID id, @Valid @RequestBody Alteracao requisicao) {
        return atualizar.executar(id, requisicao.nome(), requisicao.email(), requisicao.papel())
                .map(UsuarioResposta::de).orElseThrow(UsuarioController::naoEncontrado);
    }

    @PatchMapping("/{id}/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desativar(@PathVariable UUID id) {
        if (!desativar.executar(id)) throw naoEncontrado();
    }

    @GetMapping("/me/presenca")
    PresencaResposta presenca() {
        return obterPresenca.executar().map(PresencaResposta::new).orElseThrow(UsuarioController::naoEncontrado);
    }

    @PatchMapping("/me/presenca")
    PresencaResposta presenca(@Valid @RequestBody PresencaRequisicao requisicao) {
        return atualizarPresenca.executar(requisicao.status()).map(PresencaResposta::new)
                .orElseThrow(UsuarioController::naoEncontrado);
    }

    @ExceptionHandler(EmailDeUsuarioEmUsoException.class)
    ProblemDetail emailEmUso(EmailDeUsuarioEmUsoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("E-mail em uso");
        return problema;
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
    }

    /** Sem senha_hash: nunca sai da camada de persistencia. */
    record UsuarioResposta(UUID id, String nome, String email, PapelUsuario papel,
            StatusPresenca statusPresenca, boolean ativo) {
        static UsuarioResposta de(Usuario usuario) {
            return new UsuarioResposta(
                    usuario.id(), usuario.nome(), usuario.email(), usuario.papel(),
                    usuario.statusPresenca(), usuario.ativo());
        }
    }

    record Criacao(@NotBlank @Size(max = 150) String nome, @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String senha, @NotNull PapelGerenciavel papel) {}
    record Alteracao(@NotBlank @Size(max = 150) String nome, @NotBlank @Email String email,
            @NotNull PapelGerenciavel papel) {}
    record PresencaRequisicao(@NotNull StatusPresenca status) {}
    record PresencaResposta(StatusPresenca status) {}
}
