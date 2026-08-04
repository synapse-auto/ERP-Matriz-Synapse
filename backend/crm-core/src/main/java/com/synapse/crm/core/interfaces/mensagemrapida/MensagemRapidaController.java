package com.synapse.crm.core.interfaces.mensagemrapida;
import java.util.List;import java.util.UUID;import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Pattern;import jakarta.validation.constraints.Size;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
import com.synapse.crm.core.application.mensagemrapida.*;import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;
@RestController @RequestMapping("/api/v1/mensagens-rapidas") class MensagemRapidaController{private final ListarMensagensRapidasUseCase listar;private final CriarMensagemRapidaUseCase criar;private final AtualizarMensagemRapidaUseCase atualizar;private final RemoverMensagemRapidaUseCase remover;
 MensagemRapidaController(ListarMensagensRapidasUseCase l,CriarMensagemRapidaUseCase c,AtualizarMensagemRapidaUseCase a,RemoverMensagemRapidaUseCase r){listar=l;criar=c;atualizar=a;remover=r;}
 @GetMapping List<Resposta> listar(@RequestParam(defaultValue="false")boolean minhas){return listar.executar(minhas).stream().map(Resposta::de).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Resposta criar(@Valid @RequestBody Requisicao r){return Resposta.de(criar.executar(r.palavraChave(),r.conteudo()));}
 @PutMapping("/{id}") Resposta atualizar(@PathVariable UUID id,@Valid @RequestBody Requisicao r){return atualizar.executar(id,r.palavraChave(),r.conteudo()).map(Resposta::de).orElseThrow(MensagemRapidaController::naoEncontrada);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void remover(@PathVariable UUID id){if(!remover.executar(id))throw naoEncontrada();}
 @ExceptionHandler(PalavraChaveEmUsoException.class) ProblemDetail conflito(PalavraChaveEmUsoException e){ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());p.setTitle("Palavra-chave em uso");return p;}
 private static ResponseStatusException naoEncontrada(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Mensagem rapida nao encontrada");}
 record Requisicao(@NotBlank @Size(max=60) @Pattern(regexp="[\\p{L}\\p{N}_-]+")String palavraChave,@NotBlank String conteudo){}
 record Resposta(UUID id,UUID atendenteId,String atendenteNome,String palavraChave,String conteudo,String tipoMidia){static Resposta de(MensagemRapida m){return new Resposta(m.id(),m.atendenteId(),m.atendenteNome(),m.palavraChave(),m.conteudo(),m.tipoMidia());}}
}
