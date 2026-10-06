package com.synapse.crm.equipe.domain.disponibilidade;

/**
 * Funil do rodizio da IA: quantos usuarios sobram depois de cada filtro, na ordem em que a consulta os aplica
 * (ativo, papel que recebe atendimento, marcado como disponivel para a IA, presenca ONLINE). So contagens, para
 * explicar por que o rodizio ficou vazio sem expor nome, e-mail ou qualquer dado pessoal.
 *
 * <p>{@code semRegistroDeDisponibilidade} separa dois casos que a contagem de "disponiveis" mistura: quem desligou
 * a opcao e quem nunca teve linha em {@code disponibilidade_atendente_ia} (a consulta usa JOIN, entao quem nao tem
 * linha nunca entra no rodizio).
 */
public record DiagnosticoDoRodizio(
        String estrategia,
        long ativos,
        long comPapelPermitido,
        long disponiveisParaIa,
        long online,
        long semRegistroDeDisponibilidade) {

    public static final String ESTRATEGIA_SEQUENCIAL = "SEQUENCIAL";
    public static final String ESTRATEGIA_MENOR_CARGA = "MENOR_CARGA";
}
