package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.port.out.client.engagement.BrandLikeCleanupManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandCleanupCommandService 테스트")
class BrandCleanupCommandServiceTest {

	@Mock
	private BrandLikeCleanupManager brandLikeCleanupManager;

	private BrandCleanupCommandService brandCleanupCommandService;


	@BeforeEach
	void setUp() {
		brandCleanupCommandService = new BrandCleanupCommandService(brandLikeCleanupManager);
	}


	@Nested
	@DisplayName("deleteAllBrandLikes() - 브랜드 좋아요 전체 삭제")
	class DeleteAllBrandLikesTest {

		@Test
		@DisplayName("[deleteAllBrandLikes()] 유효한 브랜드 ID -> BrandLikeCleanupManager에 위임")
		void deleteAllBrandLikesSuccess() {
			// Arrange
			willDoNothing().given(brandLikeCleanupManager).deleteAllByBrandId(1L);

			// Act
			brandCleanupCommandService.deleteAllBrandLikes(1L);

			// Assert
			verify(brandLikeCleanupManager).deleteAllByBrandId(1L);
		}

	}

}
