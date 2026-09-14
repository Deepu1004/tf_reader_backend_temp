package com.tf.reader;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// Test code is deliberately excluded (DoNotIncludeTests): at least one existing *IT.java imports
// another team's entity/repository classes directly for its own seeding, and fixing that is a
// separate, coordinated change - not this rule's job. Production code has no such violation today.
@AnalyzeClasses(packages = "com.tf.reader", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // wokay and flambeau, exactly as CLAUDE.md's module map and shared.md's "two Java interfaces,
    // same process" describe them. common/ and sync/ sit outside this seam on purpose - common is
    // shared infrastructure both teams depend on freely, and sync/ answers to neither team.
    private static final String[] WOKAY_MODULES = {"catalogue", "admin", "content", "crypto", "ingest"};
    private static final String[] FLAMBEAU_MODULES = {"auth", "loan", "hold", "reading", "library"};

    // The literal, explicit part of CLAUDE.md's rule: "Never another module's entity/,
    // repository/ or service/". A resolved request principal like auth.model.CurrentUser is
    // shared vocabulary both teams already pass across the seam (hold, loan and reading depend
    // on it too, not only catalogue) - it is not one of these three, so it is untouched by
    // either rule below.
    private static final String[] INTERNAL_SUBPACKAGES = {"entity", "repository", "service"};

    @ArchTest
    static final ArchRule flambeauNeverReachesWokaysInternals =
            neverReachesInternalsOf(FLAMBEAU_MODULES, WOKAY_MODULES);

    @ArchTest
    static final ArchRule wokayNeverReachesFlambeausInternals =
            neverReachesInternalsOf(WOKAY_MODULES, FLAMBEAU_MODULES);

    private static ArchRule neverReachesInternalsOf(String[] from, String[] to) {
        return noClasses().that().resideInAnyPackage(packagesOf(from))
                .should().dependOnClassesThat(resideInAnyPackage(internalPackagesOf(to)))
                .because("a module may only reach another team's module through its api/ package, "
                        + "never its entity/, repository/ or service/ package");
    }

    private static String[] packagesOf(String[] modules) {
        String[] packages = new String[modules.length];
        for (int i = 0; i < modules.length; i++) {
            packages[i] = "com.tf.reader." + modules[i] + "..";
        }
        return packages;
    }

    private static String[] internalPackagesOf(String[] modules) {
        String[] packages = new String[modules.length * INTERNAL_SUBPACKAGES.length];
        int i = 0;
        for (String module : modules) {
            for (String subpackage : INTERNAL_SUBPACKAGES) {
                packages[i++] = "com.tf.reader." + module + "." + subpackage + "..";
            }
        }
        return packages;
    }
}
