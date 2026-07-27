package com.synapse.crm.equipe.interfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.autenticacao.AutenticarUsuarioUseCase;
import com.synapse.crm.equipe.application.autenticacao.EncerrarSessaoUseCase;
import com.synapse.crm.equipe.application.autenticacao.RenovarSessaoUseCase;
import com.synapse.crm.equipe.application.autenticacao.Sessao;
import com.synapse.crm.equipe.domain.usuario.AutenticacaoInvalidaException;

/** Login, renovacao e logout. Unicas rotas abertas da API. */
@RestController
@RequestMapping("/api/v1/auth")
class AutenticacaoController {

    private final AutenticarUsuarioUseCase autenticar;
    private final RenovarSessaoUseCase renovar;
    private final EncerrarSessaoUseCase encerrar;

    AutenticacaoController(
            AutenticarUsuarioUseCase autenticar,
            RenovarSessaoUseCase renovar,
            EncerrarSessaoUseCase encerrar) {
        this.autenticar = autenticar;
        this.renovar = renovar;
        this.encerrar = encerrar;
    }

    @PostMapping("/login")
    SessaoResposta login(@Valid @RequestBody LoginRequisicao requisicao) {
        return SessaoResposta.de(autenticar.executar(requisicao.email(), requisicao.senha()));
    }

    @PostMapping("/refresh")
    SessaoResposta refresh(@Valid @RequestBody RefreshRequisicao requisicao) {
        return SessaoResposta.de(renovar.executar(requisicao.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequisicao requisicao) {
        encerrar.executar(requisicao.refreshToken());
    }

    /**
     * Toda falha de autenticacao vira 401 com a mesma mensagem (RFC 7807). O motivo real fica no log
     * do servidor: distinguir "senha errada" de "usuario inativo" na resposta entrega informacao a
     * quem esta tentando adivinhar contas.
     */
    @ExceptionHandler(AutenticacaoInvalidaException.class)
    ProblemDetail aoFalharAutenticacao(AutenticacaoInvalidaException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problema.setTitle("Falha de autenticacao");
        return problema;
    }

    record LoginRequisicao(@NotBlank String email, @NotBlank String senha) {}

    record RefreshRequisicao(@NotBlank String refreshToken) {}

    record SessaoResposta(String accessToken, String refreshToken, long expiraEmSegundos) {
        static SessaoResposta de(Sessao sessao) {
            return new SessaoResposta(
                    sessao.accessToken(), sessao.refreshToken(), sessao.expiraEmSegundos());
        }
    }
}
