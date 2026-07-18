/* eslint-disable vue/one-component-per-file */
import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "@/i18n";

const mocks = vi.hoisted(() => ({
  warning: vi.fn(),
  error: vi.fn(),
  signAvatarUpload: vi.fn(),
  upload: vi.fn(),
  complete: vi.fn()
}));

vi.mock("ant-design-vue", () => ({ message: { warning: mocks.warning, error: mocks.error } }));
vi.mock("@/api/client", () => ({
  fileApi: {
    signAvatarUpload: mocks.signAvatarUpload,
    upload: mocks.upload,
    complete: mocks.complete
  }
}));
vi.mock("vue-advanced-cropper", () => ({
  CircleStencil: defineComponent({ name: "CircleStencil", render: () => null }),
  Cropper: defineComponent({
    name: "Cropper",
    props: {
      moveImage: { type: Object, required: true },
      resizeImage: { type: Object, required: true },
      stencilProps: { type: Object, required: true },
      stencilSize: { type: Function, required: true }
    },
    setup(_, { expose }) {
      const canvas = document.createElement("canvas");
      expose({ getResult: () => ({ canvas }), zoom: vi.fn(), rotate: vi.fn() });
      return () => h("div", { class: "cropper-stub" });
    }
  })
}));

import AvatarCropper from "./AvatarCropper.vue";

const ButtonStub = defineComponent({
  inheritAttrs: false,
  emits: ["click"],
  setup(_, { attrs, emit, slots }) {
    return () => h("button", { ...attrs, onClick: () => emit("click") }, slots.default?.());
  }
});
const ModalStub = defineComponent({
  setup(_, { slots }) {
    return () => h("div", { class: "modal-stub" }, [slots.default?.(), slots.footer?.()]);
  }
});

function mountCropper() {
  return mount(AvatarCropper, {
    global: {
      plugins: [i18n],
      stubs: {
        "a-button": ButtonStub,
        "a-modal": ModalStub,
        "a-tooltip": { template: "<span><slot /></span>" }
      }
    }
  });
}

describe("AvatarCropper", () => {
  const createObjectURL = vi.fn(() => `blob:avatar-${createObjectURL.mock.calls.length}`);
  const revokeObjectURL = vi.fn();
  const drawImage = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(URL, "createObjectURL", { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revokeObjectURL });
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({ drawImage } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation((callback, type) => {
      callback(new Blob(["webp"], { type: type ?? "image/webp" }));
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("拒绝非图片格式和超过 5 MB 的源文件", async () => {
    const wrapper = mountCropper();
    const input = wrapper.get("input[type=file]");

    Object.defineProperty(input.element, "files", { configurable: true, value: [new File(["x"], "x.gif", { type: "image/gif" })] });
    await input.trigger("change");
    Object.defineProperty(input.element, "files", {
      configurable: true,
      value: [new File([new Uint8Array(5 * 1024 * 1024 + 1)], "x.png", { type: "image/png" })]
    });
    await input.trigger("change");

    expect(mocks.warning).toHaveBeenCalledTimes(2);
    expect(createObjectURL).not.toHaveBeenCalled();
  });

  it("固定圆形裁剪视口并只允许用户移动和缩放图片", async () => {
    const wrapper = mountCropper();
    const input = wrapper.get("input[type=file]");
    Object.defineProperty(input.element, "files", {
      configurable: true,
      value: [new File(["source"], "avatar.png", { type: "image/png" })]
    });

    await input.trigger("change");

    const cropper = wrapper.getComponent({ name: "Cropper" });
    expect(cropper.props("stencilSize")({ boundaries: { width: 500, height: 400 } })).toEqual({
      width: 288,
      height: 288
    });
    expect(cropper.props("stencilProps")).toEqual({
      aspectRatio: 1,
      handlers: {},
      lines: {},
      movable: false,
      resizable: false
    });
    expect(cropper.props("resizeImage")).toEqual({
      adjustStencil: false,
      touch: true,
      wheel: { ratio: 0.1 }
    });
    expect(cropper.props("moveImage")).toEqual({ mouse: true, touch: true });
  });

  it("输出 512x512 WebP 并完成签名、上传和确认", async () => {
    mocks.signAvatarUpload.mockResolvedValue({
      fileId: "file-1",
      uploadMode: "PROXY",
      method: "PUT",
      url: "/files/uploads/file-1/content",
      headers: {},
      formFields: {},
      expiresAt: "2099-01-01T00:00:00Z",
      uploadGrant: "grant-1"
    });
    mocks.upload.mockResolvedValue(undefined);
    mocks.complete.mockResolvedValue(undefined);
    const wrapper = mountCropper();
    const input = wrapper.get("input[type=file]");
    Object.defineProperty(input.element, "files", {
      configurable: true,
      value: [new File(["source"], "avatar.png", { type: "image/png" })]
    });
    await input.trigger("change");

    await wrapper.findAll("button").at(-1)?.trigger("click");
    await vi.waitFor(() => expect(mocks.complete).toHaveBeenCalledWith("file-1"));

    const uploadedFile = mocks.signAvatarUpload.mock.calls[0]?.[0] as File;
    expect(uploadedFile).toMatchObject({ type: "image/webp", size: 4 });
    expect(drawImage).toHaveBeenCalledWith(expect.any(HTMLCanvasElement), 0, 0, 512, 512);
    expect(wrapper.emitted("uploaded")?.[0]?.[0]).toMatchObject({ fileId: "file-1", uploadGrant: "grant-1" });

    wrapper.unmount();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:avatar-1");
    expect(revokeObjectURL).not.toHaveBeenCalledWith("blob:avatar-2");
  });
});
