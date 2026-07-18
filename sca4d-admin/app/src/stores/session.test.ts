import type { MeSelectionQuery, MeView } from "@sca4d/admin-common";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  current: vi.fn<(query?: MeSelectionQuery) => Promise<MeView>>(),
  resolveOne: vi.fn(),
  invalidate: vi.fn(),
  realtimeStart: vi.fn(),
  realtimeStop: vi.fn(),
  reconcile: vi.fn<() => Promise<void>>(),
  logout: vi.fn()
}));

vi.mock("../api/client", () => ({
  authenticationApi: { logout: mocks.logout },
  meApi: { current: mocks.current },
  realtimeClient: { start: mocks.realtimeStart, stop: mocks.realtimeStop }
}));
vi.mock("../router", () => ({ reconcileWorkspaceAccess: mocks.reconcile }));
vi.mock("./files", () => ({
  useFileStore: () => ({
    resolveOne: mocks.resolveOne,
    invalidate: mocks.invalidate,
    clear: vi.fn(),
    referenceKey: (reference: { scene: string; resourceId: string; fileId: string }) =>
      `${reference.scene}:${reference.resourceId}:${reference.fileId}`
  })
}));

import { useSessionStore } from "./session";

function snapshot(
  principalType: "DEFAULT" | "PLATFORM" | "TENANT",
  tenantId: string | null = null,
  platform = false
): MeView {
  return {
    subject: { subjectId: "10", displayName: "Alice" },
    account: { accountId: "100", scopeType: "GLOBAL", scopeId: null },
    avatar: null,
    profileVersion: "1",
    principal: { principalId: tenantId ?? "10", principalType, tenantId, displayName: "Alice" },
    session: { tokenName: "Authorization", deviceId: null, terminalName: null, loginTime: null, expireTime: null },
    roles: [],
    permissions: principalType === "TENANT" ? ["tenant:members:read"] : [],
    availablePrincipals: [
      { principalId: "10", principalType: "DEFAULT", tenantId: null, displayName: "Alice", status: "ACTIVE" },
      ...(platform
        ? [{ principalId: "20", principalType: "PLATFORM" as const, tenantId: null, displayName: "Alice", status: "ACTIVE" as const }]
        : []),
      { principalId: "30", principalType: "TENANT", tenantId: "40", displayName: "Alice", status: "ACTIVE" }
    ],
    tenants: [{
      membershipId: "50",
      principalId: "30",
      tenantId: "40",
      tenantCode: "acme",
      tenantName: "Acme",
      tenantType: "DEFAULT",
      tenantStatus: "ACTIVE",
      membershipStatus: "ACTIVE",
      version: "1"
    }],
    navigation: []
  };
}

const loginSession = {
  sessionId: "session-1",
  tokenName: "Authorization",
  tokenValue: "token",
  subjectId: "10",
  accountId: "100",
  deviceId: null,
  terminalName: null,
  loginTime: null,
  expireTime: null
};

describe("session store", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.clearAllMocks();
    mocks.resolveOne.mockResolvedValue(null);
    mocks.reconcile.mockResolvedValue(undefined);
    setActivePinia(createPinia());
  });

  it("平台身份可用时加载平台权限快照", async () => {
    mocks.current
      .mockResolvedValueOnce(snapshot("DEFAULT", null, true))
      .mockResolvedValueOnce(snapshot("PLATFORM", null, true));
    const session = useSessionStore();

    await session.establish(loginSession, false);

    expect(mocks.current).toHaveBeenNthCalledWith(1, { principalType: "DEFAULT" });
    expect(mocks.current).toHaveBeenNthCalledWith(2, { principalType: "PLATFORM" });
    expect(session.selection).toEqual({ principalType: "PLATFORM" });
    expect(sessionStorage.getItem("sca4d-admin.session.tab")).toContain("token");
  });

  it("选择租户身份后加载对应权限", async () => {
    mocks.current.mockResolvedValueOnce(snapshot("DEFAULT")).mockResolvedValueOnce(snapshot("TENANT", "40"));
    const session = useSessionStore();
    await session.establish(loginSession, false);

    await session.selectPrincipal("TENANT", "40");

    expect(session.hasPermission("tenant:members:read")).toBe(true);
    expect(session.selection).toEqual({ principalType: "TENANT", tenantId: "40" });
  });

  it("同一头像失败只重新换签一次", async () => {
    const me = snapshot("DEFAULT");
    me.avatar = { scene: "iam.default-profile.avatar", resourceId: "10", fileId: "file-1" };
    mocks.current.mockResolvedValueOnce(me);
    mocks.resolveOne.mockResolvedValueOnce("/avatar-1").mockResolvedValueOnce("/avatar-2");
    const session = useSessionStore();
    await session.establish(loginSession, false);

    await session.refreshAvatar();
    await session.refreshAvatar();

    expect(mocks.invalidate).toHaveBeenCalledTimes(1);
    expect(mocks.resolveOne).toHaveBeenCalledTimes(2);
    expect(session.avatarUrl).toBeNull();
  });
});
