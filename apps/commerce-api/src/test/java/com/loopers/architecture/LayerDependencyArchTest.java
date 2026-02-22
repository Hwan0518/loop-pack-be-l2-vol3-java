package com.loopers.architecture;


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
 * 8. Service -> Service prohibition
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
		@DisplayName("[facade] facade는 port를 직접 호출하지 않는다")
		void facadeShouldNotDependOnPort() {
			noClasses()
				.that().resideInAnyPackage("..application.facade..")
				.should().dependOnClassesThat().resideInAnyPackage("..application.port..", "..infrastructure.acl..")
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


	// 8. Service -> Service prohibition
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


	// 7. Cross-BC boundary
	@Nested
	@DisplayName("Bounded Context 경계 규칙")
	class CrossBcBoundary {

		@Test
		@DisplayName("[Cross-BC] user domain은 다른 BC의 domain을 참조하지 않는다")
		void userDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.user.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.like.domain..",
					"com.loopers.cart.domain..",
					"com.loopers.order.domain.."
				)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] catalog(brand) domain은 다른 BC의 domain을 참조하지 않는다")
		void brandDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.catalog.brand.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.domain..",
					"com.loopers.like.domain..",
					"com.loopers.cart.domain..",
					"com.loopers.order.domain.."
				)
				.allowEmptyShould(true)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] catalog(product) domain은 다른 BC의 domain을 참조하지 않는다")
		void productDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.catalog.product.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.domain..",
					"com.loopers.like.domain..",
					"com.loopers.cart.domain..",
					"com.loopers.order.domain.."
				)
				.allowEmptyShould(true)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] like domain은 다른 BC의 domain을 참조하지 않는다")
		void likeDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.like.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.cart.domain..",
					"com.loopers.order.domain.."
				)
				.allowEmptyShould(true)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] cart domain은 다른 BC의 domain을 참조하지 않는다")
		void cartDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.cart.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.like.domain..",
					"com.loopers.order.domain.."
				)
				.allowEmptyShould(true)
				.check(importedClasses);
		}

		@Test
		@DisplayName("[Cross-BC] order domain은 다른 BC의 domain을 참조하지 않는다")
		void orderDomainShouldNotDependOnOtherBcDomains() {
			noClasses()
				.that().resideInAnyPackage("com.loopers.order.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
					"com.loopers.user.domain..",
					"com.loopers.catalog.brand.domain..",
					"com.loopers.catalog.product.domain..",
					"com.loopers.like.domain..",
					"com.loopers.cart.domain.."
				)
				.allowEmptyShould(true)
				.check(importedClasses);
		}
	}

}
