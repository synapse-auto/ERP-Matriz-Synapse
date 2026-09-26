package com.synapse.crm.app.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import com.synapse.crm.equipe.domain.permissao.Capacidade;

/**
 * Catalogo e {@code @PreAuthorize} precisam concordar nos dois sentidos (docs/47):
 *
 * <ul>
 *   <li>todo {@code @capacidades.permite('x')} cita um id do catalogo — SpEL e texto, e um erro de
 *       digitacao so apareceria em runtime;
 *   <li>toda capacidade configuravel tem ao menos um ponto de aplicacao — permissao sem efeito e
 *       controle fantasma na tela.
 * </ul>
 */
class CapacidadesReferenciadasTest {

    private static final Pattern PERMITE = Pattern.compile("@capacidades\\.permite\\('([^']+)'\\)");

    /** Aplicadas por codigo (VerificadorDeCapacidades), nao por anotacao. */
    private static final Set<String> APLICADAS_PROGRAMATICAMENTE = Set.of(
            "resumo_ia.ver", // LeadController corta resumoIa da ficha (e tambem anotada em SolicitarResumoIaUseCase.estado)
            "equipe.alterar_papel"); // AtualizarUsuarioUseCase, so quando o papel muda

    private static Set<String> referenciadas;
    private static JavaClasses classes;

    @BeforeAll
    static void importar() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.synapse.crm");
        referenciadas = classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(PreAuthorize.class))
                .map(CapacidadesReferenciadasTest::expressao)
                .flatMap(e -> ids(e).stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    @DisplayName("nenhum @PreAuthorize cita capacidade fora do catalogo")
    void semIdDesconhecido() {
        assertThat(referenciadas).isNotEmpty();
        Set<String> desconhecidas = referenciadas.stream()
                .filter(id -> Capacidade.porId(id).isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(desconhecidas).as("ids citados em @PreAuthorize que nao existem no catalogo").isEmpty();
    }

    @Test
    @DisplayName("toda capacidade configuravel tem ponto de aplicacao no backend")
    void semPermissaoSemEfeito() {
        Set<String> semEfeito = Arrays.stream(Capacidade.values())
                .filter(c -> !c.estrutural())
                .map(Capacidade::id)
                .filter(id -> !referenciadas.contains(id) && !APLICADAS_PROGRAMATICAMENTE.contains(id))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(semEfeito).as("capacidades do catalogo sem nenhum ponto de aplicacao").isEmpty();
    }

    @Test
    @DisplayName("processamento tecnico (SERVICO) nunca passa por capacidade: comando ja aceito nao e revalidado")
    void servicoSemCapacidade() {
        Set<String> misturados = classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(PreAuthorize.class))
                .filter(m -> expressao(m).contains("SERVICO") && expressao(m).contains("@capacidades"))
                .map(JavaMethod::getFullName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(misturados).isEmpty();
    }

    @Test
    @DisplayName("a regra reprova de proposito: um id inventado e detectado")
    void regraReprovaIdInventado() {
        Set<String> ids = ids("hasRole('GESTOR') and @capacidades.permite('tags.criarr')");
        assertThat(ids).containsExactly("tags.criarr");
        assertThat(Capacidade.porId("tags.criarr")).isEmpty();
    }

    private static String expressao(JavaMethod metodo) {
        return metodo.getAnnotationOfType(PreAuthorize.class).value();
    }

    private static Set<String> ids(String expressao) {
        Set<String> ids = new TreeSet<>();
        Matcher m = PERMITE.matcher(expressao);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }
}
