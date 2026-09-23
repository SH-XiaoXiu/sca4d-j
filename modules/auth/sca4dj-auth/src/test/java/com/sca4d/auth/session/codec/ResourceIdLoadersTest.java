package com.sca4d.auth.session.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.authentication.principalsession.ResourceIdLoader;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.identity.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ResourceIdLoadersTest {

    @Test
    @DisplayName("Merges Distinct Numeric Resources By Principal And Type")
    void mergesDistinctNumericResourcesByPrincipalAndType() {
        Principal principal = () -> 31;
        ResourceIdLoader first = ignored -> Map.of(
                31L,
                List.of(new ResourceIdSet(TreeCode.ofSegment("PROJECT"), List.of(101L, 102L))));
        ResourceIdLoader second = ignored -> Map.of(
                31L,
                List.of(
                        new ResourceIdSet(TreeCode.ofSegment("PROJECT"), List.of(103L)),
                        new ResourceIdSet(TreeCode.ofSegment("DEVICE"), List.of(201L))));

        var result = new ResourceIdLoaders(List.of(first, second), Runnable::run)
                .load(List.of(principal));

        assertThat(result.get(31L)).hasSize(2);
        assertThat(result.get(31L).get(0).resourceType()).isEqualTo(TreeCode.ofSegment("PROJECT"));
        assertThat(result.get(31L).get(0).ids().toArray()).containsExactly(101L, 102L, 103L);
        assertThat(result.get(31L).get(1).resourceType()).isEqualTo(TreeCode.ofSegment("DEVICE"));
    }

    @Test
    @DisplayName("Rejects Duplicate Resource IDs And Unknown Principals")
    void rejectsDuplicateResourceIdsAndUnknownPrincipals() {
        Principal principal = () -> 31;
        ResourceIdLoader first = ignored -> Map.of(
                31L,
                List.of(new ResourceIdSet(TreeCode.ofSegment("PROJECT"), List.of(101L))));
        ResourceIdLoader duplicate = ignored -> Map.of(
                31L,
                List.of(new ResourceIdSet(TreeCode.ofSegment("PROJECT"), List.of(101L))));
        ResourceIdLoader unknown = ignored -> Map.of(
                99L,
                List.of(new ResourceIdSet(TreeCode.ofSegment("PROJECT"), List.of(102L))));

        assertThatThrownBy(
                () -> new ResourceIdLoaders(List.of(first, duplicate), Runnable::run)
                        .load(List.of(principal)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Resource 标识重复");
        assertThatThrownBy(
                () -> new ResourceIdLoaders(List.of(unknown), Runnable::run)
                        .load(List.of(principal)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("输入外的 Principal");
    }
}
