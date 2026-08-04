package cn.v7soft.admin.controller.req;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class EditFrontServerRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void requiresAtLeastOneIpv4() {
        EditFrontServerRequest request = validRequest();
        request.setPrimaryIp(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsDuplicateAndNonIpv4Values() {
        EditFrontServerRequest request = validRequest();
        request.setFailoverIp("10.0.0.1");
        request.setFallbackIp("server.example.com");

        Set<ConstraintViolation<EditFrontServerRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("不能重复")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("IPv4")));
    }

    @Test
    void acceptsDistinctOptionalIpv4Values() {
        EditFrontServerRequest request = validRequest();
        request.setFailoverIp("10.0.0.2");
        request.setFallbackIp("10.0.0.3");

        assertTrue(validator.validate(request).isEmpty());
    }

    private EditFrontServerRequest validRequest() {
        EditFrontServerRequest request = new EditFrontServerRequest();
        request.setName("edge");
        request.setCnameRecord("edge.example.com");
        request.setPrimaryIp("10.0.0.1");
        return request;
    }
}
