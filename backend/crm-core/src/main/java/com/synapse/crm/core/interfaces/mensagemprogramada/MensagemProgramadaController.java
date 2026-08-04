package com.synapse.crm.core.interfaces.mensagemprogramada;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

import com.synapse.crm.core.application.mensagemprogramada.*;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;

@RestController
@RequestMapping("/api/v1/mensagens-programadas")
class MensagemProgramadaController {
    private final CriarMensagemProgramadaUseCase criar; private final ListarMensagensProgramadasUseCase listar;
    private final AtualizarMensagemProgramadaUseCase atualizar; private final CancelarMensagemProgramadaUseCase cancelar;
    private final int tamanho;
    MensagemProgramadaController(CriarMensagemProgramadaUseCase criar,ListarMensagensProgramadasUseCase listar,
            AtualizarMensagemProgramadaUseCase atualizar,CancelarMensagemProgramadaUseCase cancelar,
            @Value("${synapse.suporte.tamanho-pagina}")int tamanho){this.criar=criar;this.listar=listar;this.atualizar=atualizar;this.cancelar=cancelar;this.tamanho=tamanho;}
    @GetMapping PaginaResposta listar(@RequestParam(required=false)Instant inicio,@RequestParam(required=false)Instant fim,
            @RequestParam(required=false)StatusMensagemProgramada status,@RequestParam(defaultValue="0")int pagina){
        return PaginaResposta.de(listar.executar(new FiltroMensagensProgramadas(inicio,fim,status,pagina,tamanho)));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Resposta criar(@Valid @RequestBody Requisicao r){return criar.executar(r.leadId(),r.conteudo(),r.dataEnvio()).map(Resposta::de).orElseThrow(MensagemProgramadaController::naoEncontrada);}
    @PutMapping("/{id}") Resposta atualizar(@PathVariable UUID id,@Valid @RequestBody Alteracao r){return atualizar.executar(id,r.conteudo(),r.dataEnvio()).map(Resposta::de).orElseThrow(MensagemProgramadaController::naoEncontrada);}
    @PostMapping("/{id}/cancelar") Resposta cancelar(@PathVariable UUID id){return cancelar.executar(id).map(Resposta::de).orElseThrow(MensagemProgramadaController::naoEncontrada);}
    @ExceptionHandler(MensagemProgramadaNaoEditavelException.class) ProblemDetail conflito(MensagemProgramadaNaoEditavelException e){ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());p.setTitle("Mensagem programada nao editavel");return p;}
    private static ResponseStatusException naoEncontrada(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Mensagem programada ou lead nao encontrado");}
    record Requisicao(@NotNull UUID leadId,@NotBlank String conteudo,@NotNull @Future Instant dataEnvio){}
    record Alteracao(@NotBlank String conteudo,@NotNull @Future Instant dataEnvio){}
    record Resposta(UUID id,UUID leadId,String leadNome,UUID atendenteId,String atendenteNome,String conteudo,Instant dataEnvio,StatusMensagemProgramada status){static Resposta de(MensagemProgramada m){return new Resposta(m.id(),m.leadId(),m.leadNome(),m.atendenteId(),m.atendenteNome(),m.conteudo(),m.dataEnvio(),m.status());}}
    record PaginaResposta(List<Resposta> mensagens,int pagina,boolean temMais){static PaginaResposta de(PaginaMensagensProgramadas p){return new PaginaResposta(p.mensagens().stream().map(Resposta::de).toList(),p.pagina(),p.temMais());}}
}
