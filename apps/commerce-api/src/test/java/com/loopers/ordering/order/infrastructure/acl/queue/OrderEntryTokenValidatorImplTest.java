package com.loopers.ordering.order.infrastructure.acl.queue;


import com.loopers.queue.waitingqueue.application.facade.EntryTokenCommandFacade;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEntryTokenValidatorImpl 단위 테스트")
class OrderEntryTokenValidatorImplTest {

    @Mock
    private EntryTokenCommandFacade entryTokenCommandFacade;

    private OrderEntryTokenValidatorImpl orderEntryTokenValidatorImpl;


    @BeforeEach
    void setUp() {
        orderEntryTokenValidatorImpl = new OrderEntryTokenValidatorImpl(entryTokenCommandFacade);
    }


    @Test
    @DisplayName("[validateAndLock()] 유효한 userId와 entryToken -> facade.validateAndLock() 위임 호출")
    void validateAndLockDelegatesToFacade() {
        // Arrange
        Long userId = 1L;
        String entryToken = "valid-token-abc";
        willDoNothing().given(entryTokenCommandFacade).validateAndLock(userId, entryToken);

        // Act
        orderEntryTokenValidatorImpl.validateAndLock(userId, entryToken);

        // Assert
        verify(entryTokenCommandFacade).validateAndLock(userId, entryToken);
    }


    @Test
    @DisplayName("[validateAndLock()] facade에서 CoreException 발생 -> 그대로 전파. INVALID_QUEUE_TOKEN 예외")
    void validateAndLockPropagatesCoreException() {
        // Arrange
        Long userId = 1L;
        String entryToken = "invalid-token";
        willThrow(new CoreException(ErrorType.INVALID_QUEUE_TOKEN))
            .given(entryTokenCommandFacade).validateAndLock(userId, entryToken);

        // Act
        CoreException exception = assertThrows(CoreException.class,
            () -> orderEntryTokenValidatorImpl.validateAndLock(userId, entryToken));

        // Assert
        assertAll(
            () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
            () -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
        );
    }


    @Test
    @DisplayName("[completeOrder()] 유효한 userId -> facade.completeOrder() 위임 호출")
    void completeOrderDelegatesToFacade() {
        // Arrange
        Long userId = 1L;
        willDoNothing().given(entryTokenCommandFacade).completeOrder(userId);

        // Act
        orderEntryTokenValidatorImpl.completeOrder(userId);

        // Assert
        verify(entryTokenCommandFacade).completeOrder(userId);
    }


    @Test
    @DisplayName("[releaseOrderLock()] 유효한 userId -> facade.releaseOrderLock() 위임 호출")
    void releaseOrderLockDelegatesToFacade() {
        // Arrange
        Long userId = 1L;
        willDoNothing().given(entryTokenCommandFacade).releaseOrderLock(userId);

        // Act
        orderEntryTokenValidatorImpl.releaseOrderLock(userId);

        // Assert
        verify(entryTokenCommandFacade).releaseOrderLock(userId);
    }
}
