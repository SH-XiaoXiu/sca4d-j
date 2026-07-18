import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import RemoteResourceSelect from "./RemoteResourceSelect.vue";

const SelectStub = defineComponent({
  props: {
    options: { type: Array, default: () => [] },
    loading: Boolean
  },
  emits: ["search", "popup-scroll", "update:value"],
  setup(props) {
    return () => h(
      "div",
      { "data-loading": String(props.loading) },
      (props.options as Array<{ value: string; label: string }>).map((option) =>
        h("span", { key: option.value, class: "option" }, option.label)
      )
    );
  }
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe("RemoteResourceSelect", () => {
  afterEach(() => vi.useRealTimers());

  it("新搜索开始后忽略较晚完成的旧响应", async () => {
    vi.useFakeTimers();
    type Result = {
      records: Array<{ id: string; label: string }>;
      page: number;
      size: number;
      total: number;
    };
    const first = deferred<Result>();
    const second = deferred<Result>();
    const signals: AbortSignal[] = [];
    const loadOptions = vi.fn((_search: string, _page: number, _size: number, signal: AbortSignal) => {
      signals.push(signal);
      return signals.length === 1 ? first.promise : second.promise;
    });
    const wrapper = mount(RemoteResourceSelect, {
      props: {
        loadOptions,
        placeholder: "选择资源",
        emptyText: "暂无资源"
      },
      global: {
        stubs: {
          "a-select": SelectStub,
          "a-spin": { template: "<span />" }
        }
      }
    });
    await nextTick();
    expect(loadOptions).toHaveBeenCalledTimes(1);

    wrapper.getComponent(SelectStub).vm.$emit("search", "new");
    await vi.advanceTimersByTimeAsync(250);
    expect(loadOptions).toHaveBeenCalledTimes(2);
    expect(signals[0]?.aborted).toBe(true);

    second.resolve({ records: [{ id: "new", label: "新结果" }], page: 1, size: 20, total: 1 });
    await flushPromises();
    expect(wrapper.findAll(".option").map((option) => option.text())).toEqual(["新结果"]);

    first.resolve({ records: [{ id: "old", label: "旧结果" }], page: 1, size: 20, total: 1 });
    await flushPromises();
    expect(wrapper.findAll(".option").map((option) => option.text())).toEqual(["新结果"]);
    expect(wrapper.emitted("loadError")).toBeUndefined();

    wrapper.unmount();
    expect(signals[1]?.aborted).toBe(true);
  });
});
