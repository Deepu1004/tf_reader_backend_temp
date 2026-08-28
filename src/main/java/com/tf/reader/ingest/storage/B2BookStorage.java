package com.tf.reader.ingest.storage;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.api.PresignedObject;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Component
@RequiredArgsConstructor
class B2BookStorage implements BookStorage {

	private final S3Client s3;
	private final S3Presigner presigner;
	private final BookStorageProperties properties;

	@Override
	public void store(String key, byte[] data, String contentType) {
		s3.putObject(
				PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build(),
				RequestBody.fromBytes(data));
	}

	@Override
	public byte[] load(String key) {
		ResponseBytes<GetObjectResponse> response = s3
				.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
		return response.asByteArray();
	}

	@Override
	public String contentType(String key) {
		return s3.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build()).contentType();
	}

	@Override
	public void delete(String key) {
		s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
	}

	@Override
	public PresignedObject presign(String key, Duration ttl) {
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.getObjectRequest(b -> b.bucket(properties.bucket()).key(key))
				.build();
		PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
		return new PresignedObject(presigned.url().toString(), presigned.expiration());
	}

}
