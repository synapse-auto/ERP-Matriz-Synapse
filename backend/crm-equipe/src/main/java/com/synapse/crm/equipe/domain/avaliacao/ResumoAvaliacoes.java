package com.synapse.crm.equipe.domain.avaliacao;
import java.math.BigDecimal;
public record ResumoAvaliacoes(BigDecimal mediaGeral,long total,List<PorAtendente> porAtendente){public record PorAtendente(UUID atendenteId,String atendenteNome,BigDecimal media,long total){}}
