package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 验证字典引用检查器注册、重复拒绝和批量引用查询。 */
class DictionaryReferenceRegistryTest {

    @Test
    void shouldQueryEachMatchingDomainOnceForTheWholeCandidateSet() {
        DictionaryValueReference vehicle = mock(DictionaryValueReference.class);
        DictionaryValueReference contract = mock(DictionaryValueReference.class);
        DictionaryValueReference unrelated = mock(DictionaryValueReference.class);
        Set<String> candidates = new LinkedHashSet<>(List.of("BYD", "TESLA", "NIO"));
        when(vehicle.supports("vehicle.brand")).thenReturn(true);
        when(contract.supports("vehicle.brand")).thenReturn(true);
        when(unrelated.supports("vehicle.brand")).thenReturn(false);
        when(vehicle.findReferencedValues(9L, "vehicle.brand", candidates))
                .thenReturn(new LinkedHashSet<>(List.of("NIO", "BYD")));
        when(contract.findReferencedValues(9L, "vehicle.brand", candidates)).thenReturn(Set.of("TESLA"));
        DictionaryReferenceRegistry registry = new DictionaryReferenceRegistry(List.of(vehicle, contract, unrelated));

        Set<String> referenced = registry.referencedValues(9L, "vehicle.brand", candidates);

        assertThat(referenced).containsExactly("BYD", "TESLA", "NIO");
        verify(vehicle).supports("vehicle.brand");
        verify(vehicle).findReferencedValues(9L, "vehicle.brand", candidates);
        verify(contract).supports("vehicle.brand");
        verify(contract).findReferencedValues(9L, "vehicle.brand", candidates);
        verify(unrelated).supports("vehicle.brand");
        verifyNoMoreInteractions(vehicle, contract, unrelated);
    }
}
