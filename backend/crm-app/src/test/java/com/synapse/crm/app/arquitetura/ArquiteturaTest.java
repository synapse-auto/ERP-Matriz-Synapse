package com.synapse.crm.app.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Regra de dependencia do monorepo, verificada no build.
 *
 * <p>A proibicao de framework no dominio nao da para expressar num POM: infrastructure e domain
 * moram no mesmo modulo e, portanto, no mesmo classpath. O POM cobre o crm-shared-kernel (via
 * maven-enforcer-plugin); o que cobre os pacotes {@code domain} e este teste.
 *
 * <p>A suite vive em crm-app porque este e o unico modulo cujo classpath contem todos os outros —
 * uma suite so cobre o monorepo inteiro, em vez de uma copia por modulo que sairia de sincronia.
 *
 * <p>A E02 trouxe as primeiras classes de dominio e {@code archunit.properties} voltou a
 * {@code failOnEmptyShould=true}: daqui em diante, uma regra que deixe de casar com qualquer classe
 * e falha de build. Isso protege contra o modo de falha mais traicoeiro deste arquivo — a regra
 * continuar verde porque parou de enxergar as classes que deveria vigiar.
 */
// Sem DoNotIncludeJars: os modulos chegam ate crm-app empacotados como JAR, entao
// exclui-los deixava a suite analisando so as classes da propria aplicacao. O
// filtro por pacote ja mantem dependencia de terceiro fora da analise.
@AnalyzeClasses(
        packages = "com.synapse.crm",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ArquiteturaTest {

    /**
     * O dominio e Java puro. Se foi preciso importar {@code jakarta.persistence} aqui, o mapeamento
     * pertence a um adaptador em infrastructure, nao a entidade.
     */
    @ArchTest
    static final ArchRule dominio_nao_conhece_framework = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.validation..",
                    "org.hibernate..",
                    "com.fasterxml.jackson..")
            .because("CLAUDE.md: domain sem Spring, sem JPA, sem serializacao");

    /** A regra de dependencia aponta para dentro: domain e o centro e nao olha para fora. */
    @ArchTest
    static final ArchRule dominio_nao_conhece_as_outras_camadas = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..interfaces..")
            .because("domain nao importa nada de application, infrastructure ou interface");

    /**
     * Application declara portas; quem as implementa e infrastructure. Se um caso de uso importa um
     * adaptador, trocar de canal ou de fila deixa de ser possivel sem mexer em regra de negocio.
     */
    @ArchTest
    static final ArchRule application_nao_conhece_adaptadores = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..interfaces..")
            .because("casos de uso dependem de portas, nunca de adaptadores concretos");

    /**
     * O shared kernel e importavel pelo dominio de todos os modulos: um acoplamento a framework
     * introduzido aqui vazaria para todos os dominios de uma vez.
     */
    @ArchTest
    static final ArchRule shared_kernel_e_java_puro = noClasses()
            .that()
            .resideInAPackage("com.synapse.crm.sharedkernel..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml.jackson..")
            .because("crm-shared-kernel e Java puro — ver o maven-enforcer-plugin no POM do modulo");

    /**
     * A composicao acontece em crm-app e so ali. Um modulo que importa crm-app deixa de ser
     * extraivel e transforma o monolito modular em monolito.
     */
    @ArchTest
    static final ArchRule modulos_nao_dependem_da_aplicacao = noClasses()
            .that()
            .resideOutsideOfPackage("com.synapse.crm.app..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.synapse.crm.app..")
            .because("crm-app conhece os modulos; nenhum modulo conhece crm-app");

    /**
     * O repositorio Spring Data de lead consegue ler sem filtro de visibilidade. Essa capacidade
     * existe, entao precisa ficar trancada num lugar so: o adaptador que sempre aplica a
     * VisibilidadeLeadSpecification antes de delegar.
     *
     * <p>Com 60+ casos de uso e prazo curto, "lembrar de aplicar a Specification" nao e um plano.
     * Esta regra transforma o esquecimento em build vermelho.
     */
    @ArchTest
    static final ArchRule so_o_adaptador_conversa_com_o_jpa_de_lead = noClasses()
            .that()
            .resideOutsideOfPackage("com.synapse.crm.core.infrastructure.persistencia.lead..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("LeadJpaRepository")
            .because("toda leitura de lead passa pelo adaptador que aplica a RN-CRM-01");

    /**
     * O contexto de seguranca do Spring so pode ser lido pelo adaptador que o traduz em
     * UsuarioAutenticado. Caso de uso que consulte SecurityContextHolder direto vira codigo
     * impossivel de testar sem subir Spring — e o primeiro passo para a regra de visibilidade
     * escapar da Specification.
     */
    @ArchTest
    static final ArchRule contexto_de_seguranca_so_no_adaptador = noClasses()
            .that()
            .resideOutsideOfPackage("com.synapse.crm.equipe.infrastructure.seguranca..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
            .because("os casos de uso recebem UsuarioContext, nao o SecurityContextHolder");
}
