package com.synapse.crm.campanhas.domain;

/**
 * Campo do lead que pode preencher uma variavel {{n}} do template. Lista fechada de proposito: variavel
 * livre ("qualquer coluna") abriria caminho para vazar dado que o template nao devia carregar (notas,
 * resumo da IA, CPF).
 */
public enum CampoDoLead {
    PRIMEIRO_NOME,
    NOME_COMPLETO,
    EMPRESA,
    LOCALIZACAO;

    /** O recorte do lead que as variaveis enxergam. */
    public record Dados(String nome, String empresa, String localizacao) {}

    /** Valor cru do campo; vazio ou nulo quando o lead nao tem. Quem chama aplica o valor de reserva. */
    public String valor(Dados dados) {
        return switch (this) {
            case PRIMEIRO_NOME -> NomeDoContato.primeiroNome(dados.nome());
            case NOME_COMPLETO -> dados.nome();
            case EMPRESA -> dados.empresa();
            case LOCALIZACAO -> dados.localizacao();
        };
    }
}
