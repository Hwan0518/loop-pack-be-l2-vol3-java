package com.loopers.support.common.error;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;


@Getter
@RequiredArgsConstructor
public enum ErrorType {

	/** 범용 에러 */
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
	CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),

	/** User 도메인 에러 */
	USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 가입된 로그인 ID입니다."),
	INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_FORMAT", "비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다."),
	PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "PASSWORD_CONTAINS_BIRTH_DATE", "비밀번호에 생년월일을 포함할 수 없습니다."),
	INVALID_LOGIN_ID_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_LOGIN_ID_FORMAT", "로그인 ID는 영문과 숫자만 사용 가능하며, 4~20자여야 합니다."),
	INVALID_NAME_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_NAME_FORMAT", "이름은 한글, 영문, 공백만 사용 가능하며, 최대 50자입니다."),
	INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "올바른 이메일 형식이 아닙니다."),
	INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE", "올바른 생년월일이 아닙니다."),
	PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "PASSWORD_SAME_AS_CURRENT", "새 비밀번호는 현재 비밀번호와 같을 수 없습니다."),

	/** 인증 에러 */
	AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "아이디와 비밀번호를 다시 확인해주세요."),

	/** Catalog - Brand 에러 */
	BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드가 존재하지 않습니다."),
	BRAND_HAS_ACTIVE_PRODUCTS(HttpStatus.CONFLICT, "BRAND_HAS_ACTIVE_PRODUCTS", "해당 브랜드에 활성 상품이 존재하여 삭제할 수 없습니다."),
	INVALID_BRAND_NAME(HttpStatus.BAD_REQUEST, "INVALID_BRAND_NAME", "올바른 브랜드명을 입력해주세요."),
	INVALID_BRAND_DESCRIPTION(HttpStatus.BAD_REQUEST, "INVALID_BRAND_DESCRIPTION", "올바른 브랜드 설명을 입력해주세요."),
	INVALID_BRAND_VISIBLE_STATUS(HttpStatus.BAD_REQUEST, "INVALID_BRAND_VISIBLE_STATUS", "올바른 노출 상태를 입력해주세요."),

	/** Catalog - Product 에러 */
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품이 존재하지 않습니다."),
	INVALID_PRODUCT_NAME(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_NAME", "올바른 상품명을 입력해주세요."),
	INVALID_PRODUCT_PRICE(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_PRICE", "올바른 가격을 입력해주세요."),
	INVALID_PRODUCT_STOCK(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_STOCK", "올바른 재고 수량을 입력해주세요."),
	PRODUCT_OUT_OF_STOCK(HttpStatus.CONFLICT, "PRODUCT_OUT_OF_STOCK", "재고가 부족합니다."),

	/** Like 에러 */
	LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_NOT_FOUND", "좋아요가 존재하지 않습니다."),
	LIKE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_TARGET_NOT_FOUND", "좋아요 대상이 존재하지 않습니다."),

	/** Cart 에러 */
	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목이 존재하지 않습니다."),
	INVALID_CART_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_CART_QUANTITY", "수량은 1 이상이어야 합니다."),

	/** Order 에러 */
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문이 존재하지 않습니다."),
	ORDER_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "ORDER_EMPTY_ITEMS", "주문 항목이 비어있습니다."),
	ORDER_OUT_OF_STOCK(HttpStatus.CONFLICT, "ORDER_OUT_OF_STOCK", "재고가 부족하여 주문할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
