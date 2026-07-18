import { describe, expect, it } from "vitest";
import { formatDateTime, toApiDateTime } from "../date-time";
import { createIdempotencyKey, withIdempotencyKey } from "../idempotency";
import { normalizePageData } from "../page";

describe("common support", () => {
  it("生成稳定的幂等请求字段", () => {
    const key = createIdempotencyKey();
    expect(key.length).toBeGreaterThan(10);
    expect(withIdempotencyKey({ name: "demo" }, key)).toEqual({ name: "demo", idempotencyKey: key });
  });

  it("按项目时区格式化后端时间", () => {
    expect(formatDateTime("2026-07-10T00:00:00Z")).toBe("2026-07-10 08:00:00");
    expect(toApiDateTime("2026-07-10T08:00:00+08:00")).toBe("2026-07-10T00:00:00.000Z");
    expect(formatDateTime("invalid")).toBe("--");
  });

  it("显式归一化后端字符串分页元数据", () => {
    expect(normalizePageData({ records: ["a"], page: "2", size: "20", total: "41" })).toEqual({
      records: ["a"],
      page: 2,
      size: 20,
      total: 41
    });
  });
});
