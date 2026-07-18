<script setup lang="ts">
import type { NavigationMenuView } from "@sca4d/admin-common";
import {
  ChevronDown,
  CircleUserRound,
  Landmark,
  Languages,
  LogOut,
  Menu,
  Moon,
  ShieldCheck,
  Sun
} from "lucide-vue-next";
import { Modal } from "ant-design-vue";
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch, type VNode } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import WorkspaceTabs from "@/components/navigation/WorkspaceTabs.vue";
import DynamicMenuIcon from "@/components/navigation/DynamicMenuIcon.vue";
import DefaultProfileAvatar from "@/components/data-display/DefaultProfileAvatar.vue";
import { useSessionStore } from "../stores/session";
import { useLocaleStore } from "../stores/locale";
import { useLanguageStore } from "../stores/languages";
import { useThemeStore } from "../stores/theme";
import { useWorkspaceStore } from "../stores/workspace";
import { isApplicationLocale } from "../i18n";

const route = useRoute();
const router = useRouter();
const auth = useSessionStore();
const locale = useLocaleStore();
const languageStore = useLanguageStore();
const theme = useThemeStore();
const workspace = useWorkspaceStore();
const { t } = useI18n();
const mobileOpen = ref(false);
const openMenuKeys = ref<string[]>([]);
const localeMenuScrollTop = ref<number | null>(null);

interface NavigationMenuItem {
  key: string;
  label: string;
  icon: VNode;
  children?: NavigationMenuItem[];
}

const menuItems = computed(() => (auth.me?.navigation ?? []).map(navigationItem));
const selectedMenuPath = computed(() => activeNavigationPath(auth.me?.navigation ?? [], route.path));
const selectedGroupKeys = computed(() => navigationGroupKeys(auth.me?.navigation ?? [], route.path) ?? []);
const displayName = computed(() => auth.me?.subject?.displayName || t("navigation.currentUser"));
const currentContextLabel = computed(() => {
  const principal = auth.me?.principal;
  if (principal?.principalType === "PLATFORM") return t("navigation.platformContext");
  if (principal?.principalType === "TENANT") {
    return (
      auth.me?.tenants.find((tenant) => tenant.tenantId === principal.tenantId)?.tenantName ??
      t("navigation.tenantContext")
    );
  }
  return t("navigation.personalContext");
});
const platformAvailable = computed(() => auth.hasPrincipal("PLATFORM"));
const availableTenants = computed(() =>
  (auth.me?.tenants ?? []).filter(
    (tenant) =>
      tenant.tenantStatus === "ACTIVE" &&
      tenant.membershipStatus === "ACTIVE" &&
      auth.hasPrincipal("TENANT", tenant.tenantId)
  )
);

function navigate(path: string): void {
  mobileOpen.value = false;
  void router.push(path);
}

function selectMenu(info: { key: string | number }): void {
  const path = String(info.key);
  if (!path.startsWith("group:")) navigate(path);
}

function changeOpenMenus(keys: (string | number)[]): void {
  openMenuKeys.value = keys.map(String);
}

async function selectLocale(key: string | number): Promise<void> {
  const value = String(key);
  if (!isApplicationLocale(value)) return;
  const scrollTop = localeMenuScrollTop.value ?? window.scrollY;
  localeMenuScrollTop.value = null;
  if (workspace.hasDirtyTabs) {
    const confirmed = await new Promise<boolean>((resolve) => {
      const dirtyCount = workspace.tabs.filter((tab) => tab.dirty).length;
      Modal.confirm({
        title: t("navigation.languageDirtyTitle"),
        content: t("navigation.languageDirtyContent", { count: dirtyCount }),
        okText: t("navigation.switchLanguageAnyway"),
        cancelText: t("common.cancel"),
        okType: "danger",
        onOk: () => resolve(true),
        onCancel: () => resolve(false)
      });
    });
    if (!confirmed) {
      window.scrollTo({ top: scrollTop, behavior: "auto" });
      return;
    }
  }
  locale.select(value);
  try {
    await auth.bootstrap(true);
  } finally {
    // KeepAlive 会按语言版本重建当前页面；等待新页面挂载后恢复用户切换前的位置。
    await nextTick();
    window.scrollTo({ top: scrollTop, behavior: "auto" });
  }
}

function rememberLocaleMenuScroll(open: boolean): void {
  if (open) localeMenuScrollTop.value = window.scrollY;
}

async function switchContext(key: string | number): Promise<void> {
  const value = String(key);
  if (value === "PLATFORM") {
    await auth.selectPrincipal("PLATFORM");
    await router.push(firstNavigationPath(auth.me?.navigation ?? []) ?? "/dashboard");
    return;
  }
  if (value === "DEFAULT") {
    await auth.selectPrincipal("DEFAULT");
    await router.push(firstNavigationPath(auth.me?.navigation ?? []) ?? "/dashboard");
    return;
  }
  const tenantId = value.slice("TENANT:".length);
  await auth.selectPrincipal("TENANT", tenantId);
  await router.push(firstNavigationPath(auth.me?.navigation ?? []) ?? "/dashboard");
}

function navigationItem(item: NavigationMenuView): NavigationMenuItem {
  const children: NavigationMenuItem[] = item.children.map((child) => navigationItem(child));
  return {
    key: item.path ?? `group:${item.menuId}`,
    label: item.title,
    icon: h(DynamicMenuIcon, { name: item.icon, size: 18 }),
    ...(children.length ? { children } : {})
  };
}

function firstNavigationPath(items: NavigationMenuView[]): string | null {
  for (const item of items) {
    if (item.path) return item.path;
    const childPath = firstNavigationPath(item.children);
    if (childPath) return childPath;
  }
  return null;
}

function activeNavigationPath(items: NavigationMenuView[], currentPath: string): string | null {
  let selected: string | null = null;
  for (const item of items) {
    if (item.path && (currentPath === item.path || currentPath.startsWith(`${item.path}/`))) {
      if (!selected || item.path.length > selected.length) selected = item.path;
    }
    const childPath = activeNavigationPath(item.children, currentPath);
    if (childPath && (!selected || childPath.length > selected.length)) selected = childPath;
  }
  return selected;
}

function navigationGroupKeys(items: NavigationMenuView[], currentPath: string): string[] | null {
  for (const item of items) {
    if (item.path && (currentPath === item.path || currentPath.startsWith(`${item.path}/`))) return [];
    const childGroups = navigationGroupKeys(item.children, currentPath);
    if (childGroups) return item.path ? childGroups : [`group:${item.menuId}`, ...childGroups];
  }
  return null;
}

async function logout(): Promise<void> {
  if (workspace.hasDirtyTabs) {
    const confirmed = await new Promise<boolean>((resolve) => {
      const dirtyCount = workspace.tabs.filter((tab) => tab.dirty).length;
      Modal.confirm({
        title: t("navigation.logoutDirtyTitle"),
        content: t("navigation.logoutDirtyContent", { count: dirtyCount }),
        okText: t("navigation.logoutAnyway"),
        cancelText: t("common.cancel"),
        okType: "danger",
        onOk: () => resolve(true),
        onCancel: () => resolve(false)
      });
    });
    if (!confirmed) {
      return;
    }
  }
  await auth.logout();
  await router.replace("/login");
}

function protectDirtyWorkspace(event: BeforeUnloadEvent): void {
  if (!workspace.hasDirtyTabs) {
    return;
  }
  event.preventDefault();
  event.returnValue = "";
}

onMounted(() => window.addEventListener("beforeunload", protectDirtyWorkspace));
onBeforeUnmount(() => window.removeEventListener("beforeunload", protectDirtyWorkspace));
watch(
  selectedGroupKeys,
  (keys) => {
    openMenuKeys.value = [...new Set([...openMenuKeys.value, ...keys])];
  },
  { immediate: true }
);
</script>

<template>
  <a
    href="#main-content"
    class="skip-link"
  >{{ t("app.skipToContent") }}</a>
  <a-layout class="admin-shell">
    <a-layout-sider
      class="admin-sider desktop-sider"
      :width="224"
    >
      <div class="brand">
        <div class="brand__mark">
          <ShieldCheck :size="20" />
        </div>
        <div class="brand__copy">
          <strong>SCA4D</strong><span>{{ t("app.adminSuffix") }}</span>
        </div>
      </div>
      <a-menu
        class="main-navigation"
        mode="inline"
        :items="menuItems"
        :selected-keys="selectedMenuPath ? [selectedMenuPath] : []"
        :open-keys="openMenuKeys"
        @click="selectMenu"
        @open-change="changeOpenMenus"
      />
    </a-layout-sider>

    <a-drawer
      v-model:open="mobileOpen"
      placement="left"
      :width="280"
      :closable="false"
      class="mobile-navigation"
    >
      <div class="brand mobile-brand">
        <div class="brand__mark">
          <ShieldCheck :size="20" />
        </div>
        <div class="brand__copy">
          <strong>SCA4D</strong><span>{{ t("app.adminSuffix") }}</span>
        </div>
      </div>
      <a-menu
        class="main-navigation"
        mode="inline"
        :items="menuItems"
        :selected-keys="selectedMenuPath ? [selectedMenuPath] : []"
        :open-keys="openMenuKeys"
        @click="selectMenu"
        @open-change="changeOpenMenus"
      />
    </a-drawer>

    <a-layout class="admin-main">
      <a-layout-header class="admin-header">
        <a-button
          type="text"
          class="mobile-menu-button"
          :aria-label="t('navigation.open')"
          @click="mobileOpen = true"
        >
          <Menu :size="20" />
        </a-button>
        <div class="header-spacer" />
        <div class="header-actions">
          <a-dropdown placement="bottomRight">
            <a-button class="context-switcher">
              <Landmark :size="16" />
              <span>{{ currentContextLabel }}</span>
              <ChevronDown :size="14" />
            </a-button>
            <template #overlay>
              <a-menu @click="({ key }: { key: string | number }) => switchContext(key)">
                <a-menu-item key="DEFAULT">
                  {{ t("navigation.personalContext") }}
                </a-menu-item>
                <a-menu-item
                  v-if="platformAvailable"
                  key="PLATFORM"
                >
                  {{ t("navigation.platformContext") }}
                </a-menu-item>
                <a-menu-divider v-if="availableTenants.length" />
                <a-menu-item
                  v-for="tenant in availableTenants"
                  :key="`TENANT:${tenant.tenantId}`"
                >
                  {{ tenant.tenantName }}
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-dropdown
            placement="bottomRight"
            @open-change="rememberLocaleMenuScroll"
          >
            <a-button
              type="text"
              class="header-icon-button"
              :aria-label="t('app.language')"
              :title="t('app.language')"
            >
              <Languages :size="18" />
            </a-button>
            <template #overlay>
              <a-menu
                :selected-keys="[locale.locale]"
                @click="({ key }: { key: string | number }) => selectLocale(key)"
              >
                <a-menu-item
                  v-for="language in languageStore.applicationLanguages"
                  :key="language.code"
                >
                  {{ language.nativeName }}
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-button
            type="text"
            class="header-icon-button"
            :aria-label="theme.isDark ? t('navigation.lightTheme') : t('navigation.darkTheme')"
            :title="theme.isDark ? t('navigation.lightTheme') : t('navigation.darkTheme')"
            @click="theme.toggle"
          >
            <Sun
              v-if="theme.isDark"
              :size="18"
            />
            <Moon
              v-else
              :size="18"
            />
          </a-button>
          <a-dropdown placement="bottomRight">
            <a-button
              type="text"
              class="user-menu-button"
            >
              <DefaultProfileAvatar
                :name="displayName"
                :src="auth.avatarUrl"
                :size="28"
                @load-error="auth.refreshAvatar"
              />
              <span class="user-menu-name">{{ displayName }}</span>
              <ChevronDown :size="14" />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item
                  key="profile"
                  @click="navigate('/profile')"
                >
                  <span class="dropdown-item"><CircleUserRound :size="16" />{{ t("navigation.profile") }}</span>
                </a-menu-item>
                <a-menu-item
                  key="account-security"
                  @click="navigate('/account-security')"
                >
                  <span class="dropdown-item"><ShieldCheck :size="16" />{{ t("navigation.accountSecurity") }}</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item
                  key="logout"
                  @click="logout"
                >
                  <span class="dropdown-item"><LogOut :size="16" />{{ t("navigation.logout") }}</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <WorkspaceTabs />
      <a-layout-content
        id="main-content"
        class="admin-content"
      >
        <div class="page-container">
          <router-view v-slot="{ Component, route: viewRoute }">
            <KeepAlive
              :key="locale.revision"
              :include="workspace.cacheNames"
            >
              <component
                :is="Component"
                :key="workspace.routeKey(viewRoute)"
              />
            </KeepAlive>
          </router-view>
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>
