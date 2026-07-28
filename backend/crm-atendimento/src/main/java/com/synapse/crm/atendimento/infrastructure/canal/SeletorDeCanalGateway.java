package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

/**
 * Escolhe o adaptador de canal pela configuracao da instancia.
 *
 * <p>Este e o unico ponto do sistema que sabe que existe mais de um provedor. Os casos de uso injetam
 * {@link CanalGateway} e recebem o ativo; nenhum deles pergunta qual e. Um {@code if (provedor ==
 * "meta")} espalhado pelo codigo seria o comeco do fork que a Base PAI existe para evitar — o proximo
 * filho usa Z-API ou um BSP, e a diferenca tem de caber numa variavel de ambiente.
 *
 * <p>Falhar na inicializacao quando o nome nao casa e deliberado. A alternativa — cair num adaptador
 * padrao — significaria uma instancia configurada para Z-API enviando pela Meta, ou pior, subindo com
 * um gateway de teste em producao.
 */
@Configuration
public class SeletorDeCanalGateway {

    private static final Logger log = LoggerFactory.getLogger(SeletorDeCanalGateway.class);

    /**
     * O gateway ativo.
     *
     * <p>{@code @Primary} porque os adaptadores continuam sendo beans do mesmo tipo: quem pede um
     * {@link CanalGateway} sem qualificar recebe este.
     */
    @Bean
    @Primary
    public CanalGateway canalAtivo(List<CanalGateway> disponiveis, CanalProperties propriedades) {
        Map<String, CanalGateway> porProvedor = disponiveis.stream()
                .collect(Collectors.toMap(CanalGateway::provedor, Function.identity()));

        CanalGateway escolhido = porProvedor.get(propriedades.provedor());
        if (escolhido == null) {
            throw new IllegalStateException("synapse.canal.whatsapp.provedor=" + propriedades.provedor()
                    + " nao corresponde a nenhum adaptador. Disponiveis: "
                    + porProvedor.keySet().stream().sorted().toList());
        }

        log.info("Canal de saida ativo: {}.", escolhido.provedor());
        return escolhido;
    }

    /**
     * O tradutor de entrada do mesmo provedor.
     *
     * <p>Selecionado pela mesma chave, e nao de forma independente: saida por um provedor e entrada
     * por outro seria uma instancia enviando pela Meta e lendo webhook de Z-API — inconsistencia que
     * so apareceria quando o primeiro cliente respondesse.
     */
    @Bean
    @Primary
    public TradutorDeCanal tradutorAtivo(
            List<TradutorDeCanal> disponiveis, CanalProperties propriedades) {
        Map<String, TradutorDeCanal> porProvedor = disponiveis.stream()
                .collect(Collectors.toMap(TradutorDeCanal::provedor, Function.identity()));

        TradutorDeCanal escolhido = porProvedor.get(propriedades.provedor());
        if (escolhido == null) {
            throw new IllegalStateException("nenhum tradutor de webhook para o provedor "
                    + propriedades.provedor() + ". Disponiveis: "
                    + porProvedor.keySet().stream().sorted().toList());
        }
        return escolhido;
    }
}
