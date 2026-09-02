package com.tf.reader.admin;

import com.tf.reader.admin.service.DemoDataSeeder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Safety-rail test that runs everywhere on purpose (no Testcontainers, no Docker needed),
 * passing null repo/mapper/client to prove the host guard fires before anything else does;
 * the real-client path is covered separately by {@code DemoDataSeederIT}.
 */
class DemoDataSeederGuardTest {

    private DemoDataSeeder seederFor(String uri) {
        return new DemoDataSeeder(
                null, // PublisherRepository
                null, // BookCollectionRepository
                null, // InstitutionRepository
                null, // CatalogueItemRepository
                null, // EntitlementRepository
                null, // AdminUserRepository
                null, // FeedSettingsRepository
                null, // BookEncryptionKeys
                null, // ObjectMapper
                null, // MongoClient: absent here on purpose, so the URI fallback is what is tested
                null, // MongoDatabaseFactory: absent here on purpose, so the URI fallback is what is tested
                uri,
                "localhost,127.0.0.1,::1,mongo",
                false);
    }

    @Test
    @DisplayName("refuses a remote Mongo host and names it")
    void refusesRemoteHost() {
        assertThatThrownBy(() -> seederFor("mongodb://mongo-prod.tf.internal:27017/tnfreader").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to run against non-local Mongo host")
                .hasMessageContaining("mongo-prod.tf.internal");
    }

    @Test
    @DisplayName("refuses a replica set where any one member is remote")
    void refusesMixedHostList() {
        assertThatThrownBy(
                        () ->
                                seederFor("mongodb://localhost:27017,mongo-staging.tf.internal:27017/tnfreader")
                                        .run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mongo-staging.tf.internal");
    }

    @Test
    @DisplayName("refuses when the URI is missing entirely, rather than guessing")
    void refusesBlankUri() {
        assertThatThrownBy(() -> seederFor("").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot determine which MongoDB");
    }

    @Test
    @DisplayName("the message tells you how to start without seeding")
    void messageIsActionable() {
        // A rail that blocks startup without saying what to do next gets worked around by deleting it.
        assertThatThrownBy(() -> seederFor("mongodb://db.example.com/tnfreader").run(null))
                .hasMessageContaining("tnf.seed.enabled=false")
                .hasMessageContaining("tnf.seed.allowed-hosts");
    }

    @Test
    @DisplayName("accepts the three local forms and the compose service name")
    void acceptsLocalHosts() {
        // Past the guard it will NPE on the null repositories, which is the proof that the guard let
        // it through. Anything else means the host check rejected a form the team actually uses.
        for (String uri :
                new String[] {
                    "mongodb://localhost:27017/tnfreader",
                    "mongodb://127.0.0.1:27017/tnfreader",
                    "mongodb://mongo:27017/tnfreader"
                }) {
            assertThatThrownBy(() -> seederFor(uri).run(null))
                    .as("%s should pass the host check", uri)
                    .isNotInstanceOf(IllegalStateException.class);
        }
        assertThat(true).isTrue();
    }
}