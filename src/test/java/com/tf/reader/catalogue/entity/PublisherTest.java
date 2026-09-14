package com.tf.reader.catalogue.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublisherTest {

	@Test
	void defaultsToNotConfiguredWhenNewPublisherIsCreated() {
		Publisher publisher = new Publisher();

		assertThat(publisher.getConnectionHealth()).isEqualTo(VaultConnectionHealth.NOT_CONFIGURED);
		assertThat(publisher.getVaultRef()).isNull();
	}

}
