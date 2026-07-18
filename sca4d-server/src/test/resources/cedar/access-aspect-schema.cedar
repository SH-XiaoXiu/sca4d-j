entity Principal = {
  kind: String,
  principalId: Long,
  tenantId?: Long
};

entity DemoResource = {};

action "READ" appliesTo {
  principal: Principal,
  resource: DemoResource,
  context: {}
};

action "WRITE" appliesTo {
  principal: Principal,
  resource: DemoResource,
  context: {}
};

action "MANAGE" appliesTo {
  principal: Principal,
  resource: DemoResource,
  context: {}
};
