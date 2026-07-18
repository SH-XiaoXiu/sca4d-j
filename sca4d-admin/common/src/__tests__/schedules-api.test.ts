import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createPlatformScheduleApi, createSystemScheduledJobApi } from "../apis/schedules";

const request = vi.fn();
const requestResult = vi.fn();
const get = vi.fn();
const client = { request, requestResult, get } as unknown as ApiClient;
const input = {
  name: "Maintenance notice",
  scheduleType: "ONCE" as const,
  executeAt: "2026-08-01T01:00:00.000Z",
  taskType: "notification.sse",
  taskPayload: {
    title: "Maintenance",
    content: "The service will be unavailable.",
    category: "SYSTEM" as const,
    recipients: [{ subjectId: "100", principalType: "DEFAULT" as const, principalId: "100" }]
  }
};

describe("schedule APIs", () => {
  beforeEach(() => vi.clearAllMocks());

  it("创建和人工执行使用请求头表达幂等意图", async () => {
    await createPlatformScheduleApi(client).create(input, "create-intent");
    await createPlatformScheduleApi(client).execute("200", "execute-intent");

    expect(requestResult).toHaveBeenNthCalledWith(1, {
      path: "/mg/platform/schedules",
      method: "POST",
      body: input,
      headers: { "Idempotency-Key": "create-intent" }
    });
    expect(requestResult).toHaveBeenNthCalledWith(2, {
      path: "/mg/platform/schedules/200/executions",
      method: "POST",
      headers: { "Idempotency-Key": "execute-intent" }
    });
  });

  it("更新和删除使用强 ETag 执行乐观并发控制", async () => {
    await createPlatformScheduleApi(client).update("200", { ...input, status: "PAUSED" }, '"7"');
    await createPlatformScheduleApi(client).delete("200", "8");

    expect(requestResult).toHaveBeenNthCalledWith(1, {
      path: "/mg/platform/schedules/200",
      method: "PUT",
      body: { ...input, status: "PAUSED" },
      headers: { "If-Match": '"7"' }
    });
    expect(requestResult).toHaveBeenNthCalledWith(2, {
      path: "/mg/platform/schedules/200",
      method: "DELETE",
      headers: { "If-Match": '"8"' }
    });
  });

  it("单次执行详情使用所属计划下的执行资源路径", async () => {
    const signal = new AbortController().signal;
    await createPlatformScheduleApi(client).execution("200", "300", signal);

    expect(get).toHaveBeenCalledWith(
      "/mg/platform/schedules/200/executions/300",
      undefined,
      signal
    );
  });

  it("系统任务详情对 Quartz 键的每个路径片段编码", async () => {
    await createSystemScheduledJobApi(client).detail("system jobs", "cleanup/subject");

    expect(get).toHaveBeenCalledWith("/mg/system/scheduled-jobs/system%20jobs/cleanup%2Fsubject");
  });
});
