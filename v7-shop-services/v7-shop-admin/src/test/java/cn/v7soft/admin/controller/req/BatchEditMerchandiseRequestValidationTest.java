package cn.v7soft.admin.controller.req;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class BatchEditMerchandiseRequestValidationTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidSelectedAndOwnedAllRequests() {
        BatchEditMerchandiseRequest selected = validRequest();
        BatchEditMerchandiseRequest ownedAll = validRequest();
        ownedAll.setScope(BatchEditMerchandiseRequest.Scope.OWNED_ALL);
        ownedAll.setSpuIds(null);
        ownedAll.setDelimiter("、");
        ownedAll.setOperation(BatchEditMerchandiseRequest.Operation.OVERWRITE);

        assertTrue(validator.validate(selected).isEmpty());
        assertTrue(validator.validate(ownedAll).isEmpty());
    }

    @Test
    void rejectsScopeAndSpuIdMismatch() {
        BatchEditMerchandiseRequest selectedWithoutIds = validRequest();
        selectedWithoutIds.setSpuIds(List.of());
        BatchEditMerchandiseRequest ownedAllWithIds = validRequest();
        ownedAllWithIds.setScope(BatchEditMerchandiseRequest.Scope.OWNED_ALL);

        assertFalse(validator.validate(selectedWithoutIds).isEmpty());
        assertFalse(validator.validate(ownedAllWithIds).isEmpty());
    }

    @Test
    void rejectsInvalidDelimitersAndFieldContainingDelimiter() {
        for (String delimiter : List.of("||", " ", "=")) {
            BatchEditMerchandiseRequest request = validRequest();
            request.setDelimiter(delimiter);
            assertFalse(validator.validate(request).isEmpty());
        }

        BatchEditMerchandiseRequest request = validRequest();
        request.setField("A/B");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsBlankField() {
        BatchEditMerchandiseRequest request = validRequest();
        request.setField("   ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsBlankOriginalMerchandise() {
        BatchEditMerchandiseRequest request = validRequest();
        request.setOriginalMerchandise("   ");

        assertFalse(validator.validate(request).isEmpty());
    }

    private BatchEditMerchandiseRequest validRequest() {
        BatchEditMerchandiseRequest request = new BatchEditMerchandiseRequest();
        request.setScope(BatchEditMerchandiseRequest.Scope.SELECTED);
        request.setSpuIds(List.of(10L));
        request.setOperation(BatchEditMerchandiseRequest.Operation.ADD);
        request.setOriginalMerchandise("锅具套装");
        request.setField("XXXX");
        request.setDelimiter("/");
        request.setEmptyResultPolicy(BatchEditMerchandiseRequest.EmptyResultPolicy.SKIP);
        return request;
    }
}
