entity Principal = {
  kind: String,
  principalId: Long,
  tenantId?: Long
};

entity TestResource = {
  status: String
};

action "READ" appliesTo {
  principal: Principal,
  resource: TestResource,
  context: {}
};

action "WRITE" appliesTo {
  principal: Principal,
  resource: TestResource,
  context: {}
};

action "MANAGE" appliesTo {
  principal: Principal,
  resource: TestResource,
  context: {}
};
