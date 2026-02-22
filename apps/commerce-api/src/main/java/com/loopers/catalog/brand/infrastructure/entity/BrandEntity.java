package com.loopers.catalog.brand.infrastructure.entity;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.domain.SoftDeleteBaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 브랜드 엔티티
 * - name: 브랜드명
 * - description: 브랜드 설명
 * - visibleStatus: 노출 상태
 */
@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandEntity extends SoftDeleteBaseEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "visible_status", nullable = false, length = 10)
	private VisibleStatus visibleStatus;


	private BrandEntity(Long id, String name, String description, VisibleStatus visibleStatus) {
		super(id);
		this.name = name;
		this.description = description;
		this.visibleStatus = visibleStatus;
	}


	public static BrandEntity of(Long id, String name, String description, VisibleStatus visibleStatus) {
		return new BrandEntity(id, name, description, visibleStatus);
	}

	public static BrandEntity of(String name, String description, VisibleStatus visibleStatus) {
		return of(null, name, description, visibleStatus);
	}

}
