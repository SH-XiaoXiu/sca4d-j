import { describe, expect, it } from "vitest";
import { OVERLAY_Z_INDEX } from "./overlay";

describe("OVERLAY_Z_INDEX", () => {
  it("保持抽屉、决策弹窗、弹出控件和反馈的固定层级", () => {
    expect(OVERLAY_Z_INDEX).toEqual({
      drawer: 1100,
      modal: 1200,
      popup: 1300,
      feedback: 1400
    });
  });
});
