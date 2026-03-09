package com.demo.clinic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = -5272115996825274036L;

	public BusinessRuleException(String message) {
        super(message);
    }
}
