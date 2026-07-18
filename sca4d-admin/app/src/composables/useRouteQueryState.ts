import {
  onActivated,
  reactive,
  shallowRef,
  type ShallowRef
} from "vue";
import {
  onBeforeRouteUpdate,
  useRoute,
  useRouter,
  type LocationQuery,
  type LocationQueryRaw
} from "vue-router";

/** 定义页面查询条件与 URL query 之间的双向转换。 */
export interface RouteQueryCodec<State extends object> {
  /** 将当前 URL query 转为包含默认值和类型校验的页面查询条件。 */
  decode(query: LocationQuery): State;
  /** 只输出已应用查询条件需要持久化到 URL 的字段。 */
  encode(state: Readonly<State>): LocationQueryRaw;
}

/** 页面查询草稿、已应用条件及对应的路由操作。 */
export interface RouteQueryState<State extends object> {
  /** 表单正在编辑且尚未应用的查询条件。 */
  draft: State;
  /** 当前 URL 解码得到并已经用于加载列表的查询条件。 */
  applied: Readonly<ShallowRef<State>>;
  /** 将草稿写入 URL；URL 没有变化时重新加载当前条件。 */
  apply(): Promise<void>;
  /** 清除当前页面的查询条件并加载默认列表。 */
  reset(): Promise<void>;
  /** 基于已应用条件更新分页等字段，不提交其他草稿字段。 */
  update(patch: Partial<State>): Promise<void>;
  /** 使用当前 URL 已应用条件重新加载列表。 */
  reload(): Promise<void>;
}

/**
 * 让 URL 成为列表已应用查询条件的唯一来源，并在前进、后退和 KeepAlive 激活时重新加载。
 *
 * @param codec 当前页面查询字段的解码和编码规则
 * @param load 使用已应用条件加载列表的函数
 */
export function useRouteQueryState<State extends object>(
  codec: RouteQueryCodec<State>,
  load: (state: Readonly<State>) => void | Promise<void>
): RouteQueryState<State> {
  const route = useRoute();
  const router = useRouter();
  const initial = codec.decode(route.query);
  const draft = reactive(codec.decode(route.query)) as State;
  const applied = shallowRef(initial) as ShallowRef<State>;
  let activated = false;
  let appliedFullPath = route.fullPath;
  let draftPreservedForUpdate: State | null = null;

  async function reload(): Promise<void> {
    await load(applied.value);
  }

  async function replace(state: Readonly<State>, preservedDraft: State | null): Promise<void> {
    const target = { query: codec.encode(state) };
    const resolved = router.resolve(target);
    if (resolved.fullPath === route.fullPath) {
      await reload();
      return;
    }
    draftPreservedForUpdate = preservedDraft;
    try {
      await router.replace(target);
    } catch (error) {
      draftPreservedForUpdate = null;
      throw error;
    }
  }

  async function apply(): Promise<void> {
    await replace(draft, null);
  }

  async function reset(): Promise<void> {
    await replace(codec.decode({}), null);
  }

  async function update(patch: Partial<State>): Promise<void> {
    await replace({ ...applied.value, ...patch }, { ...draft, ...patch });
  }

  // setup 完成后再首次加载，避免读取页面中还未初始化的请求控制器。
  void Promise.resolve().then(() => load(initial));

  onBeforeRouteUpdate((to) => {
    const state = codec.decode(to.query);
    Object.assign(draft, draftPreservedForUpdate ?? state);
    draftPreservedForUpdate = null;
    applied.value = state;
    appliedFullPath = to.fullPath;
    void Promise.resolve().then(() => load(state));
  });

  onActivated(() => {
    if (activated) {
      if (route.fullPath === appliedFullPath) {
        void reload();
      } else {
        const state = codec.decode(route.query);
        Object.assign(draft, state);
        applied.value = state;
        appliedFullPath = route.fullPath;
        void load(state);
      }
    }
    activated = true;
  });

  return {
    draft,
    applied,
    apply,
    reset,
    update,
    reload
  };
}

/** 读取非空的单值 URL query，数组和空字符串按未提供处理。 */
export function routeText(query: LocationQuery, name: string): string | undefined {
  const value = query[name];
  return typeof value === "string" && value ? value : undefined;
}

/** 读取正整数 URL query，无效值返回页面声明的默认值。 */
export function routePositiveInteger(query: LocationQuery, name: string, fallback: number): number {
  const value = Number(routeText(query, name));
  return Number.isInteger(value) && value > 0 ? value : fallback;
}
