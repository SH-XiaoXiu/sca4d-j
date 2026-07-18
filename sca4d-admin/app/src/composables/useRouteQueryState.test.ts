/* eslint-disable vue/one-component-per-file */
import { defineComponent, h, nextTick } from "vue";
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter, RouterView, type LocationQuery } from "vue-router";
import { describe, expect, it, vi } from "vitest";
import {
  routePositiveInteger,
  routeText,
  useRouteQueryState
} from "./useRouteQueryState";

interface TestQuery {
  page: number;
  search: string;
}

const codec = {
  decode(query: LocationQuery): TestQuery {
    return {
      page: routePositiveInteger(query, "page", 1),
      search: routeText(query, "search") ?? ""
    };
  },
  encode(state: Readonly<TestQuery>) {
    return {
      ...(state.page === 1 ? {} : { page: String(state.page) }),
      ...(state.search ? { search: state.search } : {})
    };
  }
};

async function mountQueryPage(
  initialPath: string,
  load: (state: Readonly<TestQuery>) => void | Promise<void>
) {
  let state!: ReturnType<typeof useRouteQueryState<TestQuery>>;
  const Page = defineComponent({
    setup() {
      state = useRouteQueryState(codec, load);
      return () => null;
    }
  });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/items", component: Page }]
  });
  await router.push(initialPath);
  await router.isReady();
  const wrapper = mount(defineComponent({
    setup: () => () => h(RouterView)
  }), { global: { plugins: [router] } });
  await nextTick();
  return { router, state, wrapper };
}

describe("useRouteQueryState", () => {
  it("页面 setup 完成后才执行首次加载", async () => {
    const load = vi.fn();
    let state!: ReturnType<typeof useRouteQueryState<TestQuery>>;
    const Page = defineComponent({
      setup() {
        let initialized = false;
        state = useRouteQueryState(codec, () => {
          if (!initialized) throw new Error("setup 尚未完成");
          load();
        });
        initialized = true;
        return () => null;
      }
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/items", component: Page }]
    });
    await router.push("/items");
    await router.isReady();
    const wrapper = mount(defineComponent({
      setup: () => () => h(RouterView)
    }), { global: { plugins: [router] } });
    await nextTick();

    expect(load).toHaveBeenCalledOnce();
    expect(state.applied.value).toEqual({ page: 1, search: "" });
    wrapper.unmount();
  });

  it("只通过 URL 应用草稿，并让分页保留已应用条件", async () => {
    const load = vi.fn();
    const { router, state, wrapper } = await mountQueryPage("/items?search=applied", load);

    expect(load).toHaveBeenLastCalledWith({ page: 1, search: "applied" });
    state.draft.search = "draft";
    await state.update({ page: 2 });
    await nextTick();
    expect(router.currentRoute.value.query).toEqual({ page: "2", search: "applied" });

    await state.apply();
    await nextTick();
    expect(router.currentRoute.value.query).toEqual({ page: "2", search: "draft" });

    await state.reset();
    await nextTick();
    expect(router.currentRoute.value.query).toEqual({});
    expect(state.draft).toEqual({ page: 1, search: "" });
    wrapper.unmount();
  });

  it("外部路由变化覆盖草稿，同一 URL 查询会重新加载", async () => {
    const load = vi.fn();
    const { router, state, wrapper } = await mountQueryPage("/items?search=first", load);

    state.draft.search = "unapplied";
    await router.push("/items?page=3&search=history");
    await nextTick();
    expect(state.draft).toEqual({ page: 3, search: "history" });
    expect(load).toHaveBeenLastCalledWith({ page: 3, search: "history" });

    const callCount = load.mock.calls.length;
    await state.apply();
    expect(load).toHaveBeenCalledTimes(callCount + 1);
    wrapper.unmount();
  });
});
