package com.loopers.support.common;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;


/**
 * Admin 인증 헤더 검증 유틸리티
 * 1. X-Loopers-Ldap 헤더 검증
 */
public final class AdminHeaderValidator {

	private static final String ADMIN_LDAP_VALUE = "loopers.admin";

	private AdminHeaderValidator() {
	}


	// 1. X-Loopers-Ldap 헤더 검증
	public static void validate(String ldapHeader) {

		if (ldapHeader == null || !ldapHeader.equals(ADMIN_LDAP_VALUE)) {
			throw new CoreException(ErrorType.AUTHENTICATION_FAILED);
		}
	}

}
