package com.synapse.crm.equipe.interfaces;
import java.math.BigDecimal;
@RestController @RequestMapping("/api/v1/equipe/avaliacoes") class AvaliacaoController{private final ResumirAvaliacoesUseCase resumir;AvaliacaoController(ResumirAvaliacoesUseCase r){resumir=r;}@GetMapping Resposta resumo(){return Resposta.de(resumir.executar());}
 record PorAtendente(UUID atendenteId,String atendenteNome,BigDecimal media,long total){static PorAtendente de(ResumoAvaliacoes.PorAtendente p){return new PorAtendente(p.atendenteId(),p.atendenteNome(),p.media(),p.total());}}
 record Resposta(BigDecimal mediaGeral,long total,List<PorAtendente> porAtendente){static Resposta de(ResumoAvaliacoes r){return new Resposta(r.mediaGeral(),r.total(),r.porAtendente().stream().map(PorAtendente::de).toList());}}}
