package com.tf.reader.sync.exception;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String entity, String id) {
        super(ErrorCode.CODE_TAKEN, entity + " '" + id + "' already exists");
    }

    public DuplicateResourceException(String message) {
        super(ErrorCode.CODE_TAKEN, message);
    }
}
