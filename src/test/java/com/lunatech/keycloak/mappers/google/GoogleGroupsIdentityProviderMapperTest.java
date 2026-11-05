package com.lunatech.keycloak.mappers.google;

import org.junit.jupiter.api.Test;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderModel;

import static org.junit.jupiter.api.Assertions.*;

class GoogleGroupsIdentityProviderMapperTest {

    private static BrokeredIdentityContext context(String subject, String email) {
        IdentityProviderModel identityProvider = new IdentityProviderModel();
        identityProvider.setEnabled(true);
        BrokeredIdentityContext context = new BrokeredIdentityContext(subject, identityProvider);
        context.setEmail(email);
        return context;
    }

    @Test
    void userKeyIsTheGoogleSubjectNotTheEmail() {
        assertEquals("112233445566778899000",
                GoogleGroupsIdentityProviderMapper.googleUserKey(context("112233445566778899000", "someone@example.com")));
    }

    @Test
    void missingSubjectFailsInsteadOfFallingBackToTheEmail() {
        BrokeredIdentityContext context = context(" ", "someone@example.com");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> GoogleGroupsIdentityProviderMapper.googleUserKey(context));
        assertTrue(error.getMessage().contains("no subject"));
    }
}
