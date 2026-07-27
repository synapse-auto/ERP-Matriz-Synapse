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
 * <p>Na etapa E00 os pacotes de dominio ainda estao vazios, entao as regras nao tem o que reprovar
 * (ver {@code archunit.properties}). Elas passam a valer sozinhas assim que a primeira classe de
 * dominio entrar, na E01 — que e exatamente o momento em que precisam existir.
 */
@AnalyzeClasses(
        packages = "com.synapse.crm",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
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
}
