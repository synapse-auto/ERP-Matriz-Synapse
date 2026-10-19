package com.synapse.crm.app.cancelamentos;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.synapse.crm.relatorios.application.cancelamentos.*;
import com.synapse.crm.relatorios.domain.cancelamentos.*;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/** Apenas fixture: nao e adaptador de producao, nao comprova SQL ou correlacao externos. */
public class FonteCancelamentosDeTeste implements LeituraDeCancelamentos {
    public FonteCancelamentosException.Codigo falha;
    public FiltroCancelamentos ultimoFiltro;
    public UsuarioAutenticado ultimoUsuario;
    private static final UUID ANA = UUID.fromString("11000000-0000-4000-8000-000000000004");
    private static final UUID BRUNO = UUID.fromString("11000000-0000-4000-8000-000000000005");
    private record Linha(CancelamentoAgendamento item, UUID dono) {}
    private final List<Linha> linhas = List.of(
        new Linha(new CancelamentoAgendamento("teste-3", Instant.parse("2026-10-25T14:30:00Z"), Instant.parse("2026-10-18T12:00:00Z"),
            "Contato sintetico Ana", "Profissional A", "Consulta", "Solicitou outro horario.\nAguardando contato."), ANA),
        new Linha(new CancelamentoAgendamento("teste-2", Instant.parse("2026-10-22T13:00:00Z"), Instant.parse("2026-10-18T12:00:00Z"),
            "Contato sintetico Bruno", "Profissional B", "Retorno", "   "), BRUNO),
        new Linha(new CancelamentoAgendamento("teste-1", null, Instant.parse("2026-10-01T03:00:00Z"),
            "Contato sem correlacao", null, null, null), null));

    public void limpar() { falha = null; ultimoFiltro = null; ultimoUsuario = null; }
    private Stream<Linha> visiveis(UsuarioAutenticado usuario) {
        ultimoUsuario = usuario;
        if (falha != null) throw new FonteCancelamentosException(falha);
        return linhas.stream().filter(l -> usuario.enxergaTodosOsLeads() || usuario.id().equals(l.dono()));
    }
    private static boolean contem(String valor, String busca) {
        return busca == null || valor != null && valor.toLowerCase(Locale.ROOT).contains(busca.toLowerCase(Locale.ROOT));
    }
    @Override public Pagina listar(FiltroCancelamentos f, UsuarioAutenticado usuario) {
        ultimoFiltro = f;
        var data = (java.util.function.Function<Linha, Instant>) l ->
                f.base() == FiltroCancelamentos.BaseTemporal.REGISTRO ? l.item().registradoEm() : l.item().agendadoPara();
        var encontrados = visiveis(usuario).filter(l -> {
            Instant instante = data.apply(l);
            return instante != null && !instante.isBefore(f.inicio()) && instante.isBefore(f.fimExclusivo())
                    && contem(l.item().contatoNome(), f.nome()) && contem(l.item().profissionalNome(), f.profissional())
                    && contem(l.item().tipoAgendamento(), f.tipo())
                    && (f.descricao() == FiltroCancelamentos.Descricao.TODAS || l.item().temDescricao()
                        == (f.descricao() == FiltroCancelamentos.Descricao.COM_DESCRICAO));
        }).sorted(Comparator.comparing(data, Comparator.reverseOrder()).thenComparing(l -> l.item().id(), Comparator.reverseOrder()))
                .skip((long) f.pagina() * f.tamanho()).limit(f.tamanho() + 1L).map(Linha::item).toList();
        return new Pagina(encontrados.stream().limit(f.tamanho()).toList(), encontrados.size() > f.tamanho());
    }
    @Override public Optional<CancelamentoAgendamento> detalhe(String id, UsuarioAutenticado usuario) {
        return visiveis(usuario).map(Linha::item).filter(l -> l.id().equals(id)).findFirst();
    }
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean @Primary public FonteCancelamentosDeTeste cancelamentosDeTeste() { return new FonteCancelamentosDeTeste(); }
    }
}
