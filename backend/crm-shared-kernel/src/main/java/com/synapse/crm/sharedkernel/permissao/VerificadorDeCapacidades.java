package com.synapse.crm.sharedkernel.permissao;

/**
 * Pergunta se o usuario da requisicao corrente pode executar uma capacidade do catalogo de Gestao.
 *
 * <p>Mora no shared kernel porque os casos de uso de todos os modulos consultam a mesma decisao,
 * e nenhum deles pode depender de crm-equipe, onde o catalogo e o calculo vivem. A implementacao e
 * registrada como bean {@value #NOME_DO_BEAN}; os casos de uso a usam em SpEL, sempre em conjunto
 * com a checagem de papel que ja existia:
 *
 * <pre>{@code @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('tags.criar')")}</pre>
 *
 * <p>O papel continua sendo o teto estrutural; a capacidade so restringe dentro dele. Uma
 * capacidade desconhecida lanca {@link IllegalArgumentException} — erro de digitacao em SpEL precisa
 * falhar alto, nunca virar "permitido" nem "negado" silencioso. {@code CapacidadesReferenciadasTest}
 * reprova o build quando uma anotacao cita um identificador fora do catalogo.
 *
 * <p>Execucao tecnica (jobs, outbox, contrato da Automacao) roda em {@code ContextoDeServico}, sem
 * usuario: ali a resposta e sempre {@code true}. O comando humano ja foi autorizado quando foi
 * aceito; revalidar no processamento descartaria ou duplicaria o que ja entrou na fila.
 */
public interface VerificadorDeCapacidades {

    String NOME_DO_BEAN = "capacidades";

    boolean permite(String capacidade);
}
