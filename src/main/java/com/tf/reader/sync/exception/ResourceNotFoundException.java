package com.tf.reader.sync.exception;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String entity, String id) {
        super(ErrorCode.NOT_FOUND, entity + " '" + id + "' was not found");
    }
}
