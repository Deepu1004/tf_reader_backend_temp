package com.tf.reader.admin.dto;

import java.time.LocalDate;
import java.util.List;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record CatalogueItemWrite(

		@NotBlank String publisherId,

		@Size(max = 50) List<String> collectionIds,

		@NotBlank @Size(max = 300) String title,

		@Size(max = 300) String subtitle,

		@Size(max = 20) List<@Size(max = 120) String> authors,

		@Size(max = 20) List<@Size(max = 120) String> editors,

		@Size(max = 10) List<@Size(max = 120) String> narrators,

		@Pattern(regexp = "^(97[89])?[0-9]{9}[0-9X]$", message = "isbn must be a valid ISBN-10 or ISBN-13") String isbn,

		@NotNull ContentType contentType,

		@NotNull AccessTier accessTier,

		@Size(max = 20) List<@Size(max = 80) String> subjects,

		@Size(max = 20) String language,

		@Size(max = 4000) String description,

		LocalDate publishedAt,

		@Min(1) Integer duration,

		String coverUrl,

		ItemStatus status) {
}
