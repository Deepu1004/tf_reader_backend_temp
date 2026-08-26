package com.tf.reader.ingest.storage;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Builds the S3-compatible client and presigner pointed at Backblaze B2. Building either makes no
 * network call - they only resolve the endpoint/credentials into a client object - so a context
 * can start with placeholder values (as every {@code @SpringBootTest} does today) without ever
 * reaching Backblaze. Presigning itself is a local, offline computation (a SigV4 signature over
 * the request), not a call to B2 either - only the device that later fetches the URL talks to B2.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BookStorageProperties.class)
public class BookStorageConfig {

	@Bean
	S3Client s3Client(BookStorageProperties properties) {
		return S3Client.builder()
				.endpointOverride(URI.create(properties.endpoint()))
				.region(Region.of(properties.region()))
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
				.serviceConfiguration(
						S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build())
				.build();
	}

	@Bean
	S3Presigner s3Presigner(BookStorageProperties properties) {
		return S3Presigner.builder()
				.endpointOverride(URI.create(properties.endpoint()))
				.region(Region.of(properties.region()))
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
				.serviceConfiguration(
						S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build())
				.build();
	}

}
