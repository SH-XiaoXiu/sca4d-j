entity Principal = {
  kind: String,
  principalId: Long,
  tenantId?: Long
};

entity TenantResource = {
  tenantId?: Long,
  collection: Bool,
  granted: Bool
};

action "READ" appliesTo {
  principal: Principal,
  resource: TenantResource,
  context: {}
};

action "WRITE" appliesTo {
  principal: Principal,
  resource: TenantResource,
  context: {}
};

action "MANAGE" appliesTo {
  principal: Principal,
  resource: TenantResource,
  context: {}
};
