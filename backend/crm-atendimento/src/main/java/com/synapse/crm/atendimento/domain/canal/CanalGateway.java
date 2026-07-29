package com.synapse.crm.atendimento.domain.canal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A porta de saida para o canal externo. O unico lugar por onde mensagem deixa o CRM.
 *
 * <p>Java puro: nenhum tipo do provedor aparece nesta assinatura, nem em {@link ConteudoDeEnvio}, nem
 * em {@link ResultadoDeEnvio}. Quando o formato da Meta mudar — e vai —, a mudanca fica confinada ao
 * adaptador. Esse e o ponto inteiro do ACL.
 *
 * <p>Nenhum caso de uso sabe qual implementacao esta ativa. A escolha e por configuracao da instancia
 * ({@code synapse.canal.whatsapp.provedor}), resolvida num mapa. Se um caso de uso precisar perguntar
 * "sou Meta ou Z-API?", a abstracao vazou e o proximo filho vira um fork.
 */
public interface CanalGateway {

    /** Chave de configuracao que seleciona este adaptador. Ex.: {@code meta-cloud}, {@code z-api}. */
    String provedor();

    /**
     * Da para mandar texto livre para este lead agora?
     *
     * <p>A pergunta e do dominio; a resposta e do adaptador. A Meta oficial responde "so dentro de
     * 24h desde a ultima mensagem do cliente"; um provedor nao oficial responde sempre sim. Se a
     * regra morasse no dominio, todo filho que usa Z-API carregaria uma restricao que nao existe para
     * ele — e alguem acabaria escrevendo {@code if (provedor == "meta")} para contornar.
     *
     * @param ultimaInteracaoDoLead vazio quando o lead nunca interagiu; a janela esta fechada
     */
    boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora);

    /**
     * O canal ativo exige template pre-aprovado fora da janela de texto livre?
     *
     * <p>A Automacao (E07 §5) precisa saber disto <b>antes</b> de tentar enviar — senao descobre por
     * um 400 traduzido da Meta, que e diagnostico ruim, na primeira execucao de uma campanha de
     * reativacao de 90 dias sem contato. Um provedor nao oficial responde que nao exige, porque nunca
     * teve janela para comecar.
     */
    boolean exigeTemplateForaDaJanela();

    /**
     * Entrega a mensagem ao provedor.
     *
     * <p>Nao lanca por falha do provedor: devolve {@link ResultadoDeEnvio.Recusado}. Excecao aqui
     * subiria pelo publisher da outbox e a linha ficaria sem diagnostico; o resultado tipado carrega
     * o motivo e diz se vale retentar.
     */
    ResultadoDeEnvio enviar(Envio envio);

    /**
     * Tudo o que o adaptador precisa para montar a chamada.
     *
     * @param credencialId qual credencial usar; o historico aponta para a vigente na epoca, entao
     *     troca de numero nao corrompe conversa antiga
     */
    record Envio(UUID mensagemId, String telefoneDestino, ConteudoDeEnvio conteudo, UUID credencialId) {}
}
