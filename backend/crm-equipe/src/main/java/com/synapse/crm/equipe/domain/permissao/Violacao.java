package com.synapse.crm.equipe.domain.permissao;

/** Uma chave do payload que nao pode ser persistida, com o motivo estavel (vai para o Problem Details). */
public record Violacao(String chave, Codigo codigo) {

    public enum Codigo {
        /** Identificador fora do catalogo. */
        DESCONHECIDA,
        /** Valor que nao e nivel nem booleano valido. */
        VALOR_INVALIDO,
        /** Recorte estrutural (ex.: atendimentos.ver); nao e configuravel. */
        ESTRUTURAL,
        /** O papel alvo nunca teve esta acao; conceder seria ampliar alem do teto. */
        FORA_DO_TETO,
        /** O modulo esta desligado por feature flag. */
        FLAG_DESLIGADA,
        /** Nivel abaixo do minimo do modulo ou acima do maximo do papel. */
        NIVEL_FORA_DO_LIMITE,
        /** Acao ligada com o nivel do modulo abaixo do minimo dela: ficaria "ligada" mas bloqueada. */
        NIVEL_INSUFICIENTE,
        /** Perfil GESTOR/ADMINISTRADOR: acesso fixo, nao configuravel. */
        PERFIL_FIXO,
        /** Origem de copia invalida (propria pessoa, acesso fixo, papel inexistente). */
        ORIGEM_INVALIDA
    }
}
