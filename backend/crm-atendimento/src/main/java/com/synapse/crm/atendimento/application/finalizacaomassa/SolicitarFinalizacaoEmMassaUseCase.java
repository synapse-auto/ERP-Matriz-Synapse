package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.FinalizacaoEmMassaException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Ponto de entrada do pedido. Resolve a idempotencia antes de qualquer efeito: o mesmo usuario com a mesma
 * chave e os mesmos filtros recebe a operacao ja criada (duplo clique, retry de rede); a mesma chave com
 * filtros diferentes e conflito, nunca uma segunda operacao silenciosa.
 */
@Service
public class SolicitarFinalizacaoEmMassaUseCase {

    private static final Pattern CHAVE_VALIDA = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private final ValidadorDeFiltroDeFinalizacao validador;
    private final ConsultarFinalizacaoEmMassaUseCase consultar;
    private final CriarFinalizacaoEmMassaUseCase criar;
    private final UsuarioContext usuarioContext;

    public SolicitarFinalizacaoEmMassaUseCase(
            ValidadorDeFiltroDeFinalizacao validador,
            ConsultarFinalizacaoEmMassaUseCase consultar,
            CriarFinalizacaoEmMassaUseCase criar,
            UsuarioContext usuarioContext) {
        this.validador = validador;
        this.consultar = consultar;
        this.criar = criar;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.finalizar_lote')")
    public Solicitacao executar(String chaveDeIdempotencia, PedidoDeFinalizacao pedido) {
        String chave = chaveDeIdempotencia == null ? "" : chaveDeIdempotencia.trim();
        if (!CHAVE_VALIDA.matcher(chave).matches()) {
            throw FinalizacaoEmMassaException.invalida(
                    "CHAVE_DE_IDEMPOTENCIA_INVALIDA",
                    "envie Idempotency-Key com 8 a 80 caracteres (letras, numeros, . _ : -)");
        }
        FiltroDeFinalizacao filtro = validador.montar(pedido);
        UUID solicitante = usuarioContext.atual().id();

        var existente = consultar.porChave(solicitante, chave);
        if (existente.isPresent()) {
            return repetir(existente.get(), filtro);
        }
        try {
            return new Solicitacao(criar.executar(chave, pedido), false);
        } catch (FinalizacaoEmMassaException conflito) {
            // Duas chamadas simultaneas com a mesma chave: a perdedora encontra a vencedora aqui.
            var corrida = "OPERACAO_EM_ANDAMENTO".equals(conflito.codigo())
                    ? consultar.porChave(solicitante, chave)
                    : java.util.Optional.<OperacaoDeFinalizacao>empty();
            if (corrida.isPresent()) {
                return repetir(corrida.get(), filtro);
            }
            throw conflito;
        }
    }

    private static Solicitacao repetir(OperacaoDeFinalizacao existente, FiltroDeFinalizacao filtro) {
        if (!existente.impressaoDosFiltros().equals(filtro.impressao())) {
            throw FinalizacaoEmMassaException.conflito(
                    "CHAVE_DE_IDEMPOTENCIA_REUTILIZADA", "esta chave ja foi usada com outros filtros");
        }
        return new Solicitacao(existente, true);
    }

    /** {@code repetida}: o pedido ja existia e nada novo foi criado. */
    public record Solicitacao(OperacaoDeFinalizacao operacao, boolean repetida) {}
}
