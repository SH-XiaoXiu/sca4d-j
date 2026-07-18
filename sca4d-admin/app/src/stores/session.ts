import {
  ApiError,
  type ClientStateChange,
  type LoginSession,
  type MeSelectionQuery,
  type MeView,
  type PrincipalType,
  type SessionToken
} from "@sca4d/admin-common";
import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { authenticationApi, meApi, realtimeClient, type ApiStateBinding } from "../api/client";
import { useFileStore } from "./files";
import { useWorkspaceStore } from "./workspace";

const LOCAL_SESSION_KEY = "sca4d-admin.session.local";
const TAB_SESSION_KEY = "sca4d-admin.session.tab";

interface PersistedSession {
  session: LoginSession;
  remember: boolean;
}

function readPersistedSession(): PersistedSession | null {
  const raw = sessionStorage.getItem(TAB_SESSION_KEY) ?? localStorage.getItem(LOCAL_SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as PersistedSession;
  } catch {
    localStorage.removeItem(LOCAL_SESSION_KEY);
    sessionStorage.removeItem(TAB_SESSION_KEY);
    return null;
  }
}

/** 保存登录会话、当前用户快照、请求身份和权限刷新状态。 */
export const useSessionStore = defineStore("session", () => {
  const session = ref<LoginSession | null>(null);
  const me = ref<MeView | null>(null);
  const avatarUrl = ref<string | null>(null);
  const retriedAvatarKey = ref<string | null>(null);
  const remember = ref(false);
  const bootstrapped = ref(false);
  const leavingSession = ref(false);
  const selection = ref<MeSelectionQuery>({ principalType: "DEFAULT" });
  const lastClientState = ref<ClientStateChange | null>(null);
  const menusChangedSequence = ref(0);
  const authenticated = computed(() => Boolean(session.value?.tokenValue));
  const subjectId = computed(() => me.value?.subject?.subjectId ?? session.value?.subjectId ?? null);
  const account = computed(() => me.value?.account ?? null);
  const accountId = computed(() => account.value?.accountId ?? session.value?.accountId ?? null);
  const permissions = computed(() => new Set(me.value?.permissions ?? []));
  let refreshPromise: Promise<void> | null = null;
  let refreshQueued = false;
  let leaveSessionPromise: Promise<void> | null = null;

  /** 从当前标签或持久存储恢复登录会话。 */
  function restore(): void {
    const stored = readPersistedSession();
    session.value = stored?.session ?? null;
    remember.value = stored?.remember ?? false;
  }

  function persist(): void {
    localStorage.removeItem(LOCAL_SESSION_KEY);
    sessionStorage.removeItem(TAB_SESSION_KEY);
    if (!session.value) return;
    const serialized = JSON.stringify({ session: session.value, remember: remember.value } satisfies PersistedSession);
    (remember.value ? localStorage : sessionStorage).setItem(
      remember.value ? LOCAL_SESSION_KEY : TAB_SESSION_KEY,
      serialized
    );
  }

  /** 接收认证完成后创建的 Session，并加载默认工作区。 */
  async function establish(next: LoginSession, keep: boolean): Promise<void> {
    session.value = next;
    remember.value = keep;
    persist();
    useWorkspaceStore().detach();
    await bootstrap(true);
  }

  /** 清除 Session、当前账号默认资料、文件地址和工作区相关运行状态。 */
  function clear(): void {
    realtimeClient.stop();
    refreshQueued = false;
    session.value = null;
    me.value = null;
    avatarUrl.value = null;
    retriedAvatarKey.value = null;
    bootstrapped.value = false;
    selection.value = { principalType: "DEFAULT" };
    useFileStore().clear();
    localStorage.removeItem(LOCAL_SESSION_KEY);
    sessionStorage.removeItem(TAB_SESSION_KEY);
  }

  /** 结束当前服务端 Session，并清除浏览器中的会话状态。 */
  async function logout(): Promise<void> {
    try {
      if (session.value) await authenticationApi.logout();
    } finally {
      clear();
    }
  }

  /** 加载当前用户、可选身份、权限和头像。 */
  async function bootstrap(force = false, requested?: MeSelectionQuery): Promise<void> {
    if (!session.value) return;
    const target = requested ?? selection.value;
    const sameSelection = target.principalType === selection.value.principalType
      && target.tenantId === selection.value.tenantId;
    if (bootstrapped.value && !force && sameSelection) return;
    try {
      if (!bootstrapped.value && !requested) {
        const initial = await meApi.current({ principalType: "DEFAULT" });
        const platform = initial.availablePrincipals.find(
          (principal) => principal.principalType === "PLATFORM" && principal.status === "ACTIVE"
        );
        if (platform) {
          me.value = await meApi.current({ principalType: "PLATFORM" });
          selection.value = { principalType: "PLATFORM" };
        } else {
          me.value = initial;
          selection.value = { principalType: "DEFAULT" };
        }
      } else {
        me.value = await meApi.current(target);
        selection.value = { ...target };
      }
      const files = useFileStore();
      const avatar = me.value?.avatar ?? null;
      const avatarKey = avatar ? files.referenceKey(avatar) : null;
      if (retriedAvatarKey.value !== avatarKey) retriedAvatarKey.value = null;
      try {
        avatarUrl.value = await files.resolveOne(avatar);
      } catch {
        avatarUrl.value = null;
      }
      bootstrapped.value = true;
      realtimeClient.start();
    } catch (error) {
      if (error instanceof ApiError && error.httpStatus === 401) await leaveInvalidSession();
      throw error;
    }
  }

  /** 使用指定平台或租户身份重新加载当前用户权限快照。 */
  async function selectPrincipal(
    principalType: Exclude<PrincipalType, "SYSTEM">,
    tenantId?: string
  ): Promise<void> {
    const target: MeSelectionQuery = { principalType };
    if (tenantId) target.tenantId = tenantId;
    await bootstrap(true, target);
  }

  /** 判断当前账号是否可以选择指定身份。 */
  function hasPrincipal(
    principalType: Exclude<PrincipalType, "SYSTEM">,
    tenantId?: string
  ): boolean {
    if (principalType === "DEFAULT") return true;
    return Boolean(me.value?.availablePrincipals.some(
      (principal) => principal.principalType === principalType
        && principal.status === "ACTIVE"
        && (principalType !== "TENANT" || principal.tenantId === tenantId)
    ));
  }

  /** 判断当前权限快照是否包含页面需要的权限。 */
  function hasPermission(permission: string | undefined): boolean {
    return !permission || permissions.value.has(permission);
  }

  function handleClientState(change: ClientStateChange): void {
    lastClientState.value = change;
    if (change.stale || change.refreshTargets.some(
      (target) => target === "me" || target === "menus" || target === "permissions"
    )) {
      void requestStateRefresh().catch(() => undefined);
    }
  }

  /** 将响应头、实时事件和重连信号合并为串行的当前用户刷新。 */
  async function requestStateRefresh(): Promise<void> {
    refreshQueued = true;
    if (refreshPromise) return refreshPromise;
    refreshPromise = (async () => {
      while (refreshQueued && session.value) {
        refreshQueued = false;
        try {
          await bootstrap(true, selection.value);
        } catch (error) {
          if (!(error instanceof ApiError) || error.httpStatus !== 403 || selection.value.principalType === "DEFAULT") {
            throw error;
          }
          await bootstrap(true, { principalType: "DEFAULT" });
        }
        const { reconcileWorkspaceAccess } = await import("../router");
        await reconcileWorkspaceAccess();
      }
    })();
    try {
      await refreshPromise;
    } finally {
      refreshPromise = null;
      if (refreshQueued && session.value) void requestStateRefresh().catch(() => undefined);
    }
  }

  function handleRealtimeEvent(event: string): void {
    if (event === "authentication.session.changed") {
      void requestStateRefresh().catch(() => undefined);
      return;
    }
    if (event === "system.menus.changed") {
      menusChangedSequence.value += 1;
      void requestStateRefresh().catch(() => undefined);
    }
  }

  /** 离开依赖失效 Session 的页面后清理本地状态，重复 401 只执行一次。 */
  function leaveInvalidSession(): Promise<void> {
    if (leaveSessionPromise) return leaveSessionPromise;
    const redirect = `${window.location.pathname}${window.location.search}`;
    leavingSession.value = true;
    leaveSessionPromise = (async () => {
      try {
        const { router } = await import("../router");
        if (router.currentRoute.value.name !== "login") {
          await router.replace({ name: "login", query: { redirect } });
        }
      } finally {
        clear();
        useWorkspaceStore().detach();
        leavingSession.value = false;
        leaveSessionPromise = null;
      }
    })();
    return leaveSessionPromise;
  }

  function handleUnauthorized(): void {
    void leaveInvalidSession().catch(() => undefined);
  }

  /** 头像加载失败后为同一文件重新换签一次。 */
  async function refreshAvatar(): Promise<void> {
    const avatar = me.value?.avatar ?? null;
    if (!avatar) {
      avatarUrl.value = null;
      return;
    }
    const files = useFileStore();
    const avatarKey = files.referenceKey(avatar);
    if (retriedAvatarKey.value === avatarKey) {
      avatarUrl.value = null;
      return;
    }
    retriedAvatarKey.value = avatarKey;
    files.invalidate(avatar);
    try {
      avatarUrl.value = await files.resolveOne(avatar);
    } catch {
      avatarUrl.value = null;
    }
  }

  const apiBinding: ApiStateBinding = {
    token: (): SessionToken | null => session.value
      ? { tokenName: session.value.tokenName, tokenValue: session.value.tokenValue }
      : null,
    unauthorized: handleUnauthorized,
    clientState: handleClientState,
    realtimeEvent: handleRealtimeEvent,
    realtimeReconnect: () => void requestStateRefresh().catch(() => undefined)
  };

  return {
    session,
    me,
    avatarUrl,
    remember,
    bootstrapped,
    leavingSession,
    selection,
    lastClientState,
    menusChangedSequence,
    authenticated,
    subjectId,
    account,
    accountId,
    permissions,
    restore,
    establish,
    clear,
    logout,
    bootstrap,
    selectPrincipal,
    refreshAvatar,
    hasPrincipal,
    hasPermission,
    requestStateRefresh,
    leaveInvalidSession,
    token: apiBinding.token,
    unauthorized: apiBinding.unauthorized,
    clientState: apiBinding.clientState,
    realtimeEvent: apiBinding.realtimeEvent,
    realtimeReconnect: apiBinding.realtimeReconnect
  };
});
