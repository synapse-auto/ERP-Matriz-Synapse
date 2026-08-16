package com.synapse.crm.atendimento.application;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.ConfiguracaoCanalAtivo;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.DestinosDoWebhook;

/** Decide se todos os eventos do POST pertencem aos canais ativos desta instancia. */
@Service
public class ValidarDestinoWebhookUseCase {

    private final CanalCredencialAtivaRepositorio credenciais;

    public ValidarDestinoWebhookUseCase(CanalCredencialAtivaRepositorio credenciais) {
        this.credenciais = credenciais;
    }

    public Decisao executar(DestinosDoWebhook destinos) {
        ConfiguracaoCanalAtivo configuracao = credenciais.carregarConfiguracao();
        Set<String> recebidos = Set.copyOf(destinos.identificadores());
        if (!configuracao.completa()) {
            return new Decisao(
                    Resultado.SEM_CONFIGURACAO,
                    destinos.quantidadeEventos(),
                    recebidos,
                    configuracao.quantidadeCanaisAtivos(),
                    configuracao.quantidadeSemIdentificadorExterno());
        }

        long correspondentes = destinos.identificadores().stream()
                .filter(configuracao.identificadoresExternos()::contains)
                .count();
        Resultado resultado;
        if (destinos.quantidadeEventos() > 0 && correspondentes == destinos.quantidadeEventos()) {
            resultado = Resultado.ACEITO;
        } else if (correspondentes == 0) {
            resultado = Resultado.OUTRO_CANAL;
        } else {
            resultado = Resultado.MISTO;
        }
        return new Decisao(
                resultado,
                destinos.quantidadeEventos(),
                recebidos,
                configuracao.quantidadeCanaisAtivos(),
                configuracao.quantidadeSemIdentificadorExterno());
    }

    public enum Resultado {
        ACEITO,
        OUTRO_CANAL,
        MISTO,
        SEM_CONFIGURACAO
    }

    public record Decisao(
            Resultado resultado,
            int quantidadeEventos,
            Set<String> identificadoresRecebidos,
            int quantidadeCanaisAtivos,
            int quantidadeCanaisSemIdentificador) {}
}
