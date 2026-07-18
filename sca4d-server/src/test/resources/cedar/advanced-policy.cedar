@id("advanced-owner")
permit(principal, action, resource)
when { principal.principalId == resource.ownerPrincipalId };
