package com.loopers.architecture;


import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


/**
 * Architecture Dependency Rules
 * 1. Layer direction
 * 2. Facade -> Service only
 * 3. Controller -> Facade only
 * 4. Domain model purity
 * 5. Repository interface purity
 * 6. Domain service purity
 * 7. Cross-BC boundary
 * 8. Facade -> EventPublisher prohibition
 * 9. Service -> infrastructure prohibition
 * 10. (reserved)
 * 11. Service -> Service prohibition
 * 12. ACL thin adapter (Facade-only, no Service/Repository/Entity direct access, no error mapping)
 */
@DisplayName("아키텍처 의존성 규칙 검증")
class LayerDependencyArchTest {

	private static JavaClasses importedClasses;


	@BeforeAll
	static void setUp() {
		importedClasses = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("com.loopers");
	}


	// 1. Layer direction
	@Nested
	@DisplayName("레이어 의존 방향 규칙")
	class LayerDirection {

		@Test
		@DisplayName("[domain] domain은 application에 의존하지 않는다")
		void domainShouldNotDependOnApplication() {
			noClasses()
				.that().resideInAnyPackage("..domain.model..", "..domain.repository..", "..domain.service..", "..domain.event..")
				.should().dependOnClassesThat().resideInAnyPackage("..application..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[domain] domain은 infrastructure에 의존하지 않는다")
		void domainShouldNotDependOnInfrastructure() {
			noClasses()
				.that().resideInAnyPackage("..domain.model..", "..domain.repository..", "..domain.service..", "..domain.event..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[domain] domain은 interfaces에 의존하지 않는다")
		void domainShouldNotDependOnInterfaces() {
			noClasses()
				.that().resideInAnyPackage("..domain.model..", "..domain.repository..", "..domain.service..", "..domain.event..")
				.should().dependOnClassesThat().resideInAnyPackage("..interfaces..")
				.check(importedClasses);
		}
	}


	// 2. Facade -> Service only
	@Nested
	@DisplayName("Facade 의존 규칙")
	class FacadeDependency {

		@Test
		@DisplayName("[facade] facade는 repository를 직접 호출하지 않는다")
		void facadeShouldNotDependOnRepository() {
			noClasses()
				.that().resideInAnyPackage("..application.facade..")
				.should().dependOnClassesThat().resideInAnyPackage("..domain.repository..", "..infrastructure.repository..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[facade] facade는 port 인터페이스를 직접 호출하지 않는다. DTO record 참조는 허용")
		void facadeShouldNotDependOnPort() {
			// Facade는 Port 인터페이스(Cross-BC 계약)를 직접 호출하지 않는다
			// Port 패키지 내 DTO record(OrderCartItemInfo 등)는 서비스 간 데이터 전달용이므로 허용
			// infrastructure.acl 의존은 facadeShouldNotDependOnInfrastructure()에서 이미 검증
			noClasses()
				.that().resideInAnyPackage("..application.facade..")
				.should().dependOnClassesThat(
					JavaClass.Predicates.INTERFACES
						.and(JavaClass.Predicates.resideInAnyPackage("..application.port.."))
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[facade] facade는 infrastructure에 직접 의존하지 않는다")
		void facadeShouldNotDependOnInfrastructure() {
			noClasses()
				.that().resideInAnyPackage("..application.facade..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
				.check(importedClasses);
		}
	}


	// 3. Controller -> Facade only
	@Nested
	@DisplayName("Controller 의존 규칙")
	class ControllerDependency {

		@Test
		@DisplayName("[controller] controller는 service를 직접 호출하지 않는다")
		void controllerShouldNotDependOnService() {
			noClasses()
				.that().resideInAnyPackage("..interfaces.web.controller..")
				.should().dependOnClassesThat().resideInAnyPackage("..application.service..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[controller] controller는 infrastructure에 직접 의존하지 않는다")
		void controllerShouldNotDependOnInfrastructure() {
			noClasses()
				.that().resideInAnyPackage("..interfaces.web.controller..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
				.check(importedClasses);
		}
	}


	// 4. Domain model purity
	@Nested
	@DisplayName("Domain Model 순수성 규칙")
	class DomainModelPurity {

		@Test
		@DisplayName("[domain.model] domain model은 Spring 프레임워크에 의존하지 않는다")
		void domainModelShouldNotDependOnSpring() {
			noClasses()
				.that().resideInAnyPackage("..domain.model..")
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[domain.model] domain model은 JPA에 의존하지 않는다")
		void domainModelShouldNotDependOnJpa() {
			noClasses()
				.that().resideInAnyPackage("..domain.model..")
				.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
				.check(importedClasses);
		}
	}


	// 5. Repository interface purity
	@Nested
	@DisplayName("Repository 인터페이스 순수성 규칙")
	class RepositoryInterfacePurity {

		@Test
		@DisplayName("[domain.repository] repository 인터페이스는 Spring Data 타입을 사용하지 않는다")
		void repositoryInterfaceShouldNotUseSpringDataTypes() {
			noClasses()
				.that().resideInAnyPackage("..domain.repository..")
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework.data..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[domain.repository] repository 인터페이스는 JPA에 의존하지 않는다")
		void repositoryInterfaceShouldNotDependOnJpa() {
			noClasses()
				.that().resideInAnyPackage("..domain.repository..")
				.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
				.check(importedClasses);
		}
	}


	// 6. Domain service purity
	@Nested
	@DisplayName("Domain Service 순수성 규칙")
	class DomainServicePurity {

		@Test
		@DisplayName("[domain.service] domain service는 repository를 직접 호출하지 않는다")
		void domainServiceShouldNotDependOnRepository() {
			noClasses()
				.that().resideInAnyPackage("..domain.service..")
				.should().dependOnClassesThat().resideInAnyPackage("..domain.repository..", "..infrastructure.repository..")
				.allowEmptyShould(true)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[domain.service] domain service는 port를 직접 호출하지 않는다")
		void domainServiceShouldNotDependOnPort() {
			noClasses()
				.that().resideInAnyPackage("..domain.service..")
				.should().dependOnClassesThat().resideInAnyPackage("..application.port..")
				.allowEmptyShould(true)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[domain.service] domain service는 Spring에 의존하지 않는다")
		void domainServiceShouldNotDependOnSpring() {
			noClasses()
				.that().resideInAnyPackage("..domain.service..")
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
				.allowEmptyShould(true)
				.check(importedClasses);
		}
	}


	// 8. Facade -> EventPublisher prohibition
	@Nested
	@DisplayName("Facade EventPublisher 규칙")
	class FacadeEventPublisherDependency {

		@Test
		@DisplayName("[facade] facade는 ApplicationEventPublisher를 직접 사용하지 않는다 (Service에서 발행)")
		void facadeShouldNotDependOnEventPublisher() {
			noClasses()
				.that().resideInAnyPackage("..application.facade..")
				.should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.context.ApplicationEventPublisher")
				.check(importedClasses);
		}
	}


	// 9. Service -> infrastructure prohibition
	@Nested
	@DisplayName("Service infrastructure 의존 규칙")
	class ServiceInfrastructureDependency {

		@Test
		@DisplayName("[service] service는 infrastructure.jpa를 직접 참조하지 않는다 (Domain Repository를 통해 접근)")
		void serviceShouldNotDependOnJpaRepository() {
			noClasses()
				.that().resideInAnyPackage("..application.service..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure.jpa..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[service] service는 infrastructure.entity를 직접 참조하지 않는다 (Domain Model을 통해 접근)")
		void serviceShouldNotDependOnEntity() {
			noClasses()
				.that().resideInAnyPackage("..application.service..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure.entity..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[service] service는 infrastructure.cache를 직접 참조하지 않는다 (ProductCachePort를 통해 접근)")
		void serviceShouldNotDependOnCache() {
			noClasses()
				.that().resideInAnyPackage("..application.service..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure.cache..")
				.check(importedClasses);
		}
	}


	// 11. Service -> Service prohibition
	@Nested
	@DisplayName("Service 의존 규칙")
	class ServiceDependency {

		@Test
		@DisplayName("[service] Service는 다른 Service를 직접 호출하지 않는다")
		void serviceShouldNotDependOnOtherServices() {
			noClasses()
				.that().resideInAnyPackage("..application.service..")
				.should().dependOnClassesThat()
				.resideInAnyPackage("..application.service..")
				.check(importedClasses);
		}
	}


	// 12. ACL thin adapter
	@Nested
	@DisplayName("ACL 의존 규칙")
	class AclDependency {

		@Test
		@DisplayName("[acl] ACL은 Provider Service를 직접 참조하지 않는다 (Provider Facade만 호출)")
		void aclShouldNotDependOnService() {
			noClasses()
				.that().resideInAnyPackage("..infrastructure.acl..")
				.should().dependOnClassesThat().resideInAnyPackage("..application.service..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[acl] ACL은 repository/jpa를 직접 참조하지 않는다")
		void aclShouldNotDependOnRepository() {
			noClasses()
				.that().resideInAnyPackage("..infrastructure.acl..")
				.should().dependOnClassesThat().resideInAnyPackage("..domain.repository..", "..infrastructure.repository..", "..infrastructure.jpa..", "..infrastructure.querydsl..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[acl] ACL은 infrastructure.entity를 직접 참조하지 않는다")
		void aclShouldNotDependOnEntity() {
			noClasses()
				.that().resideInAnyPackage("..infrastructure.acl..")
				.should().dependOnClassesThat().resideInAnyPackage("..infrastructure.entity..")
				.check(importedClasses);
		}

		@Test
		@DisplayName("[acl] ACL은 에러 매핑을 하지 않는다 (support.common.error 직접 참조 금지)")
		void aclShouldNotDependOnErrorPackage() {
			noClasses()
				.that().resideInAnyPackage("..infrastructure.acl..")
				.should().dependOnClassesThat().resideInAnyPackage("..support.common.error..")
				.check(importedClasses);
		}
	}


	// 7. Cross-BC boundary
	@Nested
	@DisplayName("Bounded Context 경계 규칙")
	class CrossBcBoundary {

		@Test
		@DisplayName("[Cross-BC] user domain은 다른 BC의 domain을 참조하지 않는다")
		void userDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.user.user.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.engagement.productlike.domain..", "com.loopers.engagement.brandlike.domain..",
					"com.loopers.cart.cart.domain..",
					"com.loopers.ordering.order.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] catalog(brand) domain은 다른 BC의 domain을 참조하지 않는다")
		void brandDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.catalog.brand.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.user.domain..",
					"com.loopers.engagement.productlike.domain..", "com.loopers.engagement.brandlike.domain..",
					"com.loopers.cart.cart.domain..",
					"com.loopers.ordering.order.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] catalog(product) domain은 다른 BC의 domain을 참조하지 않는다")
		void productDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.catalog.product.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.user.domain..",
					"com.loopers.engagement.productlike.domain..", "com.loopers.engagement.brandlike.domain..",
					"com.loopers.cart.cart.domain..",
					"com.loopers.ordering.order.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] productlike domain은 다른 BC의 domain을 참조하지 않는다")
		void productLikeDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.engagement.productlike.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.cart.cart.domain..",
					"com.loopers.ordering.order.domain..",
					"com.loopers.engagement.brandlike.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] brandlike domain은 다른 BC의 domain을 참조하지 않는다")
		void brandLikeDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.engagement.brandlike.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.cart.cart.domain..",
					"com.loopers.ordering.order.domain..",
					"com.loopers.engagement.productlike.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] cart domain은 다른 BC의 domain을 참조하지 않는다")
		void cartDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.cart.cart.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.engagement.productlike.domain..", "com.loopers.engagement.brandlike.domain..",
					"com.loopers.ordering.order.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] order domain은 다른 BC의 domain을 참조하지 않는다")
		void orderDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.ordering.order.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.engagement.productlike.domain..", "com.loopers.engagement.brandlike.domain..",
					"com.loopers.cart.cart.domain.."
				)
				.check(importedClasses);
		}
	}

}
