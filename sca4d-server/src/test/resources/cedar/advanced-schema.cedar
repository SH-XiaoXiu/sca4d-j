entity Principal = {
  principalId: Long
};

entity AdvancedResource = {
  ownerPrincipalId: Long
};

action "READ" appliesTo {
  principal: Principal,
  resource: AdvancedResource,
  context: {}
};

action "WRITE" appliesTo {
  principal: Principal,
  resource: AdvancedResource,
  context: {}
};

action "MANAGE" appliesTo {
  principal: Principal,
  resource: AdvancedResource,
  context: {}
};
