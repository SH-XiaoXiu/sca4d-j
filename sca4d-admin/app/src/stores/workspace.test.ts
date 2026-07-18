import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { useWorkspaceStore, type WorkspaceRoute } from "./workspace";

function route(
  path: string,
  name: string,
  title: string,
  cacheName: string,
  extra: WorkspaceRoute["meta"] = {}
): WorkspaceRoute {
  return {
    path: path.split("?")[0] ?? path,
    fullPath: path,
    name,
    meta: { title, cacheName, workspace: true, ...extra }
  };
}

describe("workspace store", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("同一路由复用标签并记录最新查询地址", () => {
    const workspace = useWorkspaceStore();
    workspace.restore("10001");
    workspace.visitRoute(route("/dashboard", "dashboard", "工作台", "DashboardPage", { pinned: true }));
    workspace.visitRoute(
      route("/system/operation-logs?page=1", "operation-logs", "操作日志", "OperationLogPage")
    );
    workspace.visitRoute(
      route("/system/operation-logs?page=3", "operation-logs", "操作日志", "OperationLogPage")
    );

    expect(workspace.tabs).toHaveLength(2);
    expect(workspace.activeTab?.path).toBe("/system/operation-logs?page=3");
  });

  it("关闭当前标签后返回最近访问的页面", () => {
    const workspace = useWorkspaceStore();
    workspace.restore("10001");
    workspace.visitRoute(route("/profile", "profile", "账号信息", "ProfilePage"));
    workspace.visitRoute(
      route("/system/operation-logs", "operation-logs", "操作日志", "OperationLogPage")
    );
    workspace.activate("route:profile");

    const next = workspace.close("route:profile");

    expect(next?.key).toBe("route:operation-logs");
    expect(workspace.tabs.some((tab) => tab.key === "route:profile")).toBe(false);
  });

  it("按身份持久化标签但不恢复未保存状态", () => {
    const workspace = useWorkspaceStore();
    workspace.restore("10001");
    workspace.visitRoute(route("/profile", "profile", "账号信息", "ProfilePage"));
    workspace.markCurrentDirty(true);

    setActivePinia(createPinia());
    const restored = useWorkspaceStore();
    restored.restore("10001");

    expect(restored.activeKey).toBe("route:profile");
    expect(restored.activeTab?.dirty).toBe(false);

    restored.restore("20002");
    expect(restored.tabs.map((tab) => tab.key)).toEqual(["route:dashboard"]);
  });

  it("关闭其他和关闭全部时始终保留工作台", () => {
    const workspace = useWorkspaceStore();
    workspace.restore("10001");
    workspace.visitRoute(route("/profile", "profile", "账号信息", "ProfilePage"));
    workspace.visitRoute(
      route("/system/operation-logs", "operation-logs", "操作日志", "OperationLogPage")
    );

    workspace.closeOthers("route:profile");
    expect(workspace.tabs.map((tab) => tab.key)).toEqual(["route:dashboard", "route:profile"]);

    workspace.closeAll();
    expect(workspace.tabs.map((tab) => tab.key)).toEqual(["route:dashboard"]);
    expect(workspace.activeKey).toBe("route:dashboard");
  });

  it("移动标签后按身份保存顺序且工作台固定在首位", () => {
    const workspace = useWorkspaceStore();
    workspace.restore("10001");
    workspace.visitRoute(route("/profile", "profile", "账号信息", "ProfilePage"));
    workspace.visitRoute(
      route("/system/operation-logs", "operation-logs", "操作日志", "OperationLogPage")
    );

    workspace.move("route:operation-logs", "route:profile", "before");
    expect(workspace.tabs.map((tab) => tab.key)).toEqual([
      "route:dashboard",
      "route:operation-logs",
      "route:profile"
    ]);

    workspace.move("route:operation-logs", "route:dashboard", "before");
    expect(workspace.tabs[0]?.key).toBe("route:dashboard");

    setActivePinia(createPinia());
    const restored = useWorkspaceStore();
    restored.restore("10001");
    expect(restored.tabs.map((tab) => tab.key)).toEqual([
      "route:dashboard",
      "route:operation-logs",
      "route:profile"
    ]);
  });

  it("完整地址模式允许同一页面打开多个标签", () => {
    const workspace = useWorkspaceStore();
    workspace.restore("10001");
    workspace.visitRoute(
      route("/reports?id=1", "report", "报表一", "ReportPage", { tabMode: "full-path" })
    );
    workspace.visitRoute(
      route("/reports?id=2", "report", "报表二", "ReportPage", { tabMode: "full-path" })
    );

    expect(workspace.tabs).toHaveLength(3);
    expect(workspace.activeKey).toBe("full:/reports?id=2");
  });
});
