package com.synapse.crm.equipe.domain.avaliacao;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record ResumoAvaliacoes(BigDecimal mediaGeral,long total,List<PorAtendente> porAtendente){public record PorAtendente(UUID atendenteId,String atendenteNome,BigDecimal media,long total){}}
