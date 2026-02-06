package com.loopers.user.support.common;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

public final class HeaderValidator {

	private HeaderValidator() {
	}

	public static void validate(String loginId, String password) {
		if (loginId == null || loginId.isBlank()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
		if (password == null || password.isBlank()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
	}
}
