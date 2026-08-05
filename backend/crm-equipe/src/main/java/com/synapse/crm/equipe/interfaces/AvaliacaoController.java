package com.synapse.crm.equipe.interfaces;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.usuario.ResumirAvaliacoesUseCase;
import com.synapse.crm.equipe.domain.avaliacao.ResumoAvaliacoes;
@RestController @RequestMapping("/api/v1/equipe/avaliacoes")
@Tag(name = "Avaliações da equipe", description = "Médias agregadas das avaliações de atendimento.")
@SecurityRequirement(name = "bearerAuth")
class AvaliacaoController{private final ResumirAvaliacoesUseCase resumir;AvaliacaoController(ResumirAvaliacoesUseCase r){resumir=r;}
 @Operation(summary = "Resumir avaliações", description = "Retorna média geral e agregação por atendente conforme a autorização de gestão.", responses = @ApiResponse(responseCode = "200", description = "Resumo das avaliações."))
 @GetMapping Resposta resumo(){return Resposta.de(resumir.executar());}
 record PorAtendente(UUID atendenteId,String atendenteNome,BigDecimal media,long total){static PorAtendente de(ResumoAvaliacoes.PorAtendente p){return new PorAtendente(p.atendenteId(),p.atendenteNome(),p.media(),p.total());}}
 record Resposta(BigDecimal mediaGeral,long total,List<PorAtendente> porAtendente){static Resposta de(ResumoAvaliacoes r){return new Resposta(r.mediaGeral(),r.total(),r.porAtendente().stream().map(PorAtendente::de).toList());}}}
