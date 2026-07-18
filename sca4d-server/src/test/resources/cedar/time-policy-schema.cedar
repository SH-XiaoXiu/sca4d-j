entity Principal = {
  kind: String,
  principalId: Long,
  tenantId?: Long
};

entity TimedResource = {
  activeFrom?: Long,
  activeUntil?: Long
};

action "READ" appliesTo {
  principal: Principal,
  resource: TimedResource,
  context: {
    requestTime: Long
  }
};

action "WRITE" appliesTo {
  principal: Principal,
  resource: TimedResource,
  context: {
    requestTime: Long
  }
};

action "MANAGE" appliesTo {
  principal: Principal,
  resource: TimedResource,
  context: {
    requestTime: Long
  }
};
