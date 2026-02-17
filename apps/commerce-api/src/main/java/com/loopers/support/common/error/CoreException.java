package com.loopers.support.common.error;


import lombok.Getter;


@Getter
public class CoreException extends RuntimeException {

	private final ErrorType errorType;
	private final String customMessage;


	public CoreException(ErrorType errorType) {
		this(errorType, null);
	}


	public CoreException(ErrorType errorType, String customMessage) {
		super(customMessage != null ? customMessage : errorType.getMessage());
		this.errorType = errorType;
		this.customMessage = customMessage;
	}


	public CoreException addMessage(String additionalMessage) {
		String newMessage = this.getMessage() + " " + additionalMessage;
		return new CoreException(this.errorType, newMessage);
	}

}
