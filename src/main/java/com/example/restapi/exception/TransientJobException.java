package com.example.restapi.exception;

public class TransientJobException extends RuntimeException {

	public TransientJobException(String message) {
		super(message);
	}

}
