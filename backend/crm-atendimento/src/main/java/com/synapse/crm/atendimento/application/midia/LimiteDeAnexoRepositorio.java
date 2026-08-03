package com.synapse.crm.atendimento.application.midia;

import java.util.Optional;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/**
 * Le o limite de tamanho de anexo configurado para cada tipo de midia.
 *
 * <p>Le direto a tabela {@code configuracao_automacao} por SQL, e nao via
 * {@code crm-automacao-config} (o modulo dono dela): {@code crm-automacao-config} ja depende de
 * {@code crm-atendimento} (injeta {@code CanalGateway} direto), entao importar o tipo Java de la
 * criaria um ciclo de modulo no Maven. E acoplamento pelo schema compartilhado, nao pela classe.
 *
 * <p>Vazio quando ninguem configurou a chave ainda — quem chama aplica um teto de fallback (ver
 * {@code EnviarMidiaUseCase}), entao o recurso funciona antes de qualquer admin abrir a tela de
 * Configuracoes.
 */
public interface LimiteDeAnexoRepositorio {

    Optional<Long> limiteEmBytes(TipoMensagem tipo);
}
