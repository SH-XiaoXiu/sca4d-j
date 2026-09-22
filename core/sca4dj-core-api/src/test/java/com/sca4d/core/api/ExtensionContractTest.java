package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.authentication.principalsession.ResourceLoader;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.PrincipalResolver;
import com.sca4d.core.api.permission.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
class ExtensionContractTest {

    @Test
    @DisplayName("Extends Core With Concrete Principal Types")
    void extendsCoreWithConcretePrincipalTypes() {
        PlatformOperator operator = new PlatformOperator(8);
        PrincipalResolver<PlatformOperator> resolver = principalId -> operator.principalId() == principalId
                ? Optional.of(operator)
                : Optional.empty();
        Operation readAccount = new Operation("platform:account:read");
        ResourceLoader loader = principals -> Map.of(operator.principalId(), Set.of(readAccount));

        Principal resolved = resolver.resolve(operator.principalId()).orElseThrow();
        var resources = loader.load(List.of(operator)).get(operator.principalId());

        assertInstanceOf(PlatformOperator.class, resolved);
        assertTrue(resolver.resolve(9).isEmpty());
        assertTrue(resources.contains(readAccount));
    }

    private static final class PlatformOperator implements Principal {

        private final long id;

        private PlatformOperator(long id) {
            this.id = id;
        }

        @Override
        public long principalId() {
            return id;
        }

    }

}
