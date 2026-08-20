package com.tf.reader.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record CollectionItemsWrite(@NotNull List<String> itemIds) {}
