package com.synapse.crm.atendimento.domain.canal;

import java.util.List;
import java.util.Objects;

/**
 * O que se manda para o cliente — e a distincao existe por causa de uma regra de negocio, nao de
 * formato.
 *
 * <p>A API oficial da Meta so aceita texto livre dentro de 24h desde a ultima mensagem <em>do
 * cliente</em>. Fora dessa janela, so template pre-aprovado. Isso nao e detalhe de infraestrutura:
 * muda o que o produto pode fazer. Follow-up, campanha e reativacao de "90 dias sem contato" caem
 * sempre fora da janela, logo sao sempre template.
 *
 * <p>Modelar como tipo selado, e nao como {@code String} com uma flag, e o que impede alguem montar
 * uma campanha de reativacao com texto livre e so descobrir em producao, quando a Meta devolver 400
 * para cada lead da lista.
 */
public sealed interface ConteudoDeEnvio {

    /**
     * Texto que o atendente escreveu na hora.
     *
     * <p>So sai dentro da janela de 24h em provedor oficial. Em provedor nao oficial, sempre.
     */
    record MensagemLivre(String texto) implements ConteudoDeEnvio {

        public MensagemLivre {
            if (texto == null || texto.isBlank()) {
                throw new IllegalArgumentException("mensagem livre exige texto");
            }
        }
    }

    /**
     * Template aprovado previamente pela Meta.
     *
     * <p>Nao e string arbitraria: tem nome, idioma e parametros <b>posicionais</b>. Os parametros
     * entram na ordem em que o template os declara — trocar a ordem troca o significado da mensagem
     * que o cliente le, sem erro nenhum em lugar nenhum.
     */
    record MensagemTemplate(String nome, String idioma, List<String> parametros)
            implements ConteudoDeEnvio {

        public MensagemTemplate {
            Objects.requireNonNull(nome, "template exige nome aprovado");
            Objects.requireNonNull(idioma, "template exige idioma");
            parametros = parametros == null ? List.of() : List.copyOf(parametros);
        }

        public static MensagemTemplate de(String nome, String idioma, String... parametros) {
            return new MensagemTemplate(nome, idioma, List.of(parametros));
        }
    }

    /** Como a mensagem aparece na conversa e no historico do CRM. */
    default String paraHistorico() {
        return switch (this) {
            case MensagemLivre livre -> livre.texto();
            // O texto renderizado quem tem e a Meta; o CRM guarda o que foi pedido.
            case MensagemTemplate template ->
                "[template " + template.nome() + "] " + String.join(" | ", template.parametros());
        };
    }
}
