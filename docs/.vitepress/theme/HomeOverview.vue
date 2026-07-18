<script setup lang="ts">
import type { Component } from 'vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowUpRight,
  BadgeCheck,
  Bell,
  Braces,
  Building2,
  CalendarClock,
  Check,
  CreditCard,
  Database,
  Files,
  Fingerprint,
  Gauge,
  GitCompareArrows,
  GitFork,
  Github,
  KeyRound,
  Library,
  ListFilter,
  Mail,
  MessagesSquare,
  Network,
  PanelsTopLeft,
  Radio,
  RadioTower,
  Route,
  Send,
  ShieldCheck,
  Smartphone,
  UserCog,
  Workflow
} from 'lucide-vue-next'
import HeroShowcase from './HeroShowcase.vue'
import { platformModuleGroups, roadmapGroups, technologyRows } from './home-content'

const icons: Record<string, Component> = {
  'badge-check': BadgeCheck,
  bell: Bell,
  braces: Braces,
  'building-2': Building2,
  'calendar-clock': CalendarClock,
  'credit-card': CreditCard,
  database: Database,
  files: Files,
  fingerprint: Fingerprint,
  gauge: Gauge,
  'git-compare-arrows': GitCompareArrows,
  'git-fork': GitFork,
  library: Library,
  'list-filter': ListFilter,
  'key-round': KeyRound,
  mail: Mail,
  'messages-square': MessagesSquare,
  network: Network,
  'panels-top-left': PanelsTopLeft,
  radio: Radio,
  'radio-tower': RadioTower,
  route: Route,
  send: Send,
  'shield-check': ShieldCheck,
  smartphone: Smartphone,
  'user-cog': UserCog,
  workflow: Workflow
}

const root = ref<HTMLElement>()
const pageVisible = ref(true)
const heroStack = ['Java', 'Spring Boot', 'PostgreSQL', 'Vue']
let revealObserver: IntersectionObserver | undefined

function updateVisibility(): void {
  pageVisible.value = document.visibilityState === 'visible'
}

onMounted(() => {
  updateVisibility()
  document.addEventListener('visibilitychange', updateVisibility)

  const targets = root.value?.querySelectorAll<HTMLElement>('[data-reveal]') ?? []
  if (typeof IntersectionObserver === 'undefined') {
    targets.forEach((target) => target.classList.add('is-visible'))
    return
  }
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      entry.target.classList.add('is-visible')
      revealObserver?.unobserve(entry.target)
    })
  }, { threshold: 0.12, rootMargin: '0px 0px -40px' })
  targets.forEach((target) => revealObserver?.observe(target))
})

onBeforeUnmount(() => {
  revealObserver?.disconnect()
  document.removeEventListener('visibilitychange', updateVisibility)
})
</script>

<template>
  <div ref="root" class="home-overview" :class="{ 'is-page-hidden': !pageVisible }">
    <section class="platform-hero" aria-labelledby="platform-hero-title">
      <div class="platform-hero__copy" data-reveal>
        <p class="platform-hero__stack" aria-label="主要技术栈">
          <span v-for="item in heroStack" :key="item">{{ item }}</span>
        </p>
        <div class="platform-hero__title">
          <img src="/logo.png" alt="" width="72" height="72">
          <h1 id="platform-hero-title">Sca4d-J</h1>
        </div>
        <p class="platform-hero__slogan">写优雅代码, 享漂亮人生。</p>
        <p class="platform-hero__description">
          可直接运行的 Java 后端工程模板，包含数据库迁移、管理端、测试、构建和项目文档，并提供身份权限、消息、文件、实时通信与调度模块。
        </p>
        <div class="platform-hero__actions">
          <a class="platform-button platform-button--primary" href="/handbook/getting-started">快速开始</a>
          <a class="platform-button platform-button--secondary" href="/handbook/">项目文档</a>
          <a class="platform-button platform-button--secondary" href="/ai-collaboration/">AI 协作</a>
          <a
            class="platform-button platform-button--secondary"
            href="http://github.com/SH-XiaoXiu/sca4d-j"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Github :size="17" :stroke-width="1.9" aria-hidden="true" />
            GitHub
          </a>
        </div>
      </div>
      <HeroShowcase />
    </section>

    <section class="technology-stream" aria-labelledby="technology-stream-title">
      <div class="home-container home-section-heading home-section-heading--inverse technology-stream__heading" data-reveal>
        <h2 id="technology-stream-title" class="home-visually-hidden">技术栈</h2>
      </div>
      <div
        v-for="(row, rowIndex) in technologyRows"
        :key="rowIndex"
        class="technology-stream__viewport"
        :class="{ 'technology-stream__viewport--reverse': rowIndex === 1 }"
        tabindex="0"
        :aria-label="`技术栈第 ${rowIndex + 1} 组`"
      >
        <div class="technology-stream__track">
          <span v-for="item in row" :key="`${rowIndex}-${item}`">{{ item }}</span>
          <span v-for="item in row" :key="`${rowIndex}-${item}-copy`" aria-hidden="true">{{ item }}</span>
        </div>
      </div>
    </section>

    <section class="home-band home-band--modules" aria-labelledby="platform-modules-title">
      <div class="home-container">
        <div class="home-section-heading" data-reveal>
          <div>
            <h2 id="platform-modules-title">内置功能</h2>
            <p>按业务需要启用已经集成的身份、运行、通信和管理功能。</p>
          </div>
        </div>

        <div class="module-domains">
          <section
            v-for="group in platformModuleGroups"
            :key="group.id"
            class="module-domain"
            :class="`module-domain--${group.id}`"
            :aria-labelledby="`module-domain-${group.id}`"
          >
            <header class="module-domain__heading" data-reveal>
              <span class="module-domain__line" aria-hidden="true"></span>
              <h3 :id="`module-domain-${group.id}`">{{ group.title }}</h3>
              <p>{{ group.description }}</p>
            </header>

            <div class="module-domain__grid">
              <a
                v-for="(item, index) in group.modules"
                :key="item.id"
                class="module-card"
                :href="item.link"
                data-reveal
                :style="{ '--reveal-delay': `${(index % 2) * 70}ms` }"
              >
                <div class="module-card__header">
                  <span class="module-card__icon">
                    <component :is="icons[item.icon]" :size="21" :stroke-width="1.8" aria-hidden="true" />
                  </span>
                  <ArrowUpRight class="module-card__arrow" :size="19" :stroke-width="1.8" aria-hidden="true" />
                </div>
                <h4>{{ item.title }}</h4>
                <p>{{ item.description }}</p>
                <ul>
                  <li v-for="detail in item.details" :key="detail">
                    <Check :size="14" :stroke-width="2" aria-hidden="true" />
                    <span>{{ detail }}</span>
                  </li>
                </ul>
              </a>
            </div>
          </section>
        </div>
      </div>
    </section>

    <section class="home-band home-band--roadmap" aria-labelledby="roadmap-title">
      <div class="home-container">
        <div class="home-section-heading" data-reveal>
          <div>
            <h2 id="roadmap-title">支持计划</h2>
            <p>以下功能仍在规划中，不属于当前内置功能。</p>
          </div>
        </div>

        <div class="roadmap-layout">
          <section v-for="group in roadmapGroups" :key="group.title" class="roadmap-group" :aria-labelledby="`roadmap-${group.title}`" data-reveal>
            <div class="roadmap-group__heading">
              <h3 :id="`roadmap-${group.title}`">{{ group.title }}</h3>
              <p>{{ group.description }}</p>
            </div>
            <div class="roadmap-group__items">
              <article v-for="item in group.items" :key="item.title" class="roadmap-card">
                <div class="roadmap-card__header">
                  <span class="roadmap-card__icon">
                    <component :is="icons[item.icon]" :size="21" :stroke-width="1.8" aria-hidden="true" />
                  </span>
                  <span class="roadmap-card__status">计划支持</span>
                </div>
                <h4>{{ item.title }}</h4>
                <p>{{ item.description }}</p>
                <ul>
                  <li v-for="detail in item.details" :key="detail">{{ detail }}</li>
                </ul>
              </article>
            </div>
          </section>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-overview {
  --home-canvas: #ffffff;
  --home-canvas-soft: #f3f8f6;
  --home-band: #f6faf8;
  --home-surface: #ffffff;
  --home-surface-muted: #f8faf9;
  --home-surface-glass: rgb(255 255 255 / 0.92);
  --home-text-1: #17231f;
  --home-text-2: #52665f;
  --home-text-3: #70827c;
  --home-border: #d6e4df;
  --home-border-strong: #9dc9be;
  --home-brand: #15705f;
  --home-brand-strong: #0d5648;
  --home-brand-hover: #0f5e50;
  --home-brand-soft: #e8f4f1;
  --home-brand-border: #c3dfd8;
  --home-brand-border-strong: #4d9d8a;
  --home-blue: #28718b;
  --home-blue-soft: #edf6f8;
  --home-blue-border: #bfd9e2;
  --home-amber: #956815;
  --home-amber-soft: #fff5dc;
  --home-amber-border: #e8cf91;
  --home-neutral: #5d6f69;
  --home-neutral-soft: #f0f4f2;
  --home-neutral-border: #ccd8d3;
  --home-grid: rgb(21 112 95 / 0.08);
  --home-line: #78ad9f;
  --home-shadow-sm: 0 8px 24px rgb(24 78 67 / 0.07);
  --home-shadow-md: 0 16px 38px rgb(19 86 72 / 0.14);
  --home-shadow-lg: 0 24px 64px rgb(21 83 70 / 0.11);
  --home-console-text: #ebf8f4;
  color: var(--home-text-1);
  background: var(--home-canvas);
}

:global(.dark) .home-overview {
  --home-canvas: #090f0d;
  --home-canvas-soft: #0d1714;
  --home-band: #0f1a17;
  --home-surface: #13211d;
  --home-surface-muted: #101c18;
  --home-surface-glass: rgb(19 33 29 / 0.95);
  --home-text-1: #f0f7f4;
  --home-text-2: #b8cbc5;
  --home-text-3: #91a69f;
  --home-border: #2b433c;
  --home-border-strong: #47756a;
  --home-brand: #66c9b2;
  --home-brand-strong: #a1e2d2;
  --home-brand-hover: #7bd8c2;
  --home-brand-soft: rgb(102 201 178 / 0.12);
  --home-brand-border: rgb(102 201 178 / 0.26);
  --home-brand-border-strong: #5fb9a4;
  --home-blue: #79b4d0;
  --home-blue-soft: rgb(121 180 208 / 0.1);
  --home-blue-border: rgb(121 180 208 / 0.32);
  --home-amber: #e4ba68;
  --home-amber-soft: rgb(228 186 104 / 0.12);
  --home-amber-border: rgb(228 186 104 / 0.34);
  --home-neutral: #a9bbb5;
  --home-neutral-soft: rgb(169 187 181 / 0.1);
  --home-neutral-border: rgb(169 187 181 / 0.28);
  --home-grid: rgb(102 201 178 / 0.08);
  --home-line: #477f72;
  --home-shadow-sm: 0 8px 24px rgb(0 0 0 / 0.22);
  --home-shadow-md: 0 16px 38px rgb(0 0 0 / 0.3);
  --home-shadow-lg: 0 24px 64px rgb(0 0 0 / 0.34);
  --home-console-text: #eff9f6;
}

.platform-hero {
  position: relative;
  min-height: 820px;
  padding: 102px 0 26px;
  overflow: hidden;
  background: var(--home-canvas-soft);
}

.platform-hero__copy {
  position: relative;
  z-index: 3;
  width: min(840px, calc(100% - 32px));
  margin: 0 auto 10px;
  text-align: center;
}

.platform-hero__stack {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  padding: 5px 12px;
  color: var(--home-brand-strong);
  font-size: 12px;
  font-weight: 700;
  background: var(--home-brand-soft);
  border: 1px solid var(--home-brand-border);
  border-radius: 6px;
}

.platform-hero__stack span {
  display: inline-flex;
  align-items: center;
}

.platform-hero__stack span + span {
  margin-left: 9px;
  padding-left: 9px;
  border-left: 1px solid var(--home-brand-border-strong);
}

.platform-hero__title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

.platform-hero__title img {
  width: 72px;
  height: 72px;
  flex: 0 0 auto;
  object-fit: contain;
}

.platform-hero h1 {
  margin: 0;
  color: var(--home-brand-strong);
  font-size: 64px;
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: 0;
}

.platform-hero__slogan {
  margin-top: 13px;
  color: var(--home-text-1);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.35;
}

.platform-hero__description {
  max-width: 760px;
  margin: 18px auto 0;
  color: var(--home-text-2);
  font-size: 16px;
  line-height: 1.7;
}

.platform-hero__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 26px;
}

.platform-button {
  display: inline-flex;
  min-width: 124px;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 9px 18px;
  font-size: 14px;
  font-weight: 720;
  text-decoration: none;
  border-radius: 7px;
  transition: color 160ms ease, background-color 160ms ease, border-color 160ms ease, transform 160ms ease;
}

.platform-button:hover,
.platform-button:focus-visible {
  transform: translateY(-2px);
}

.platform-button--primary {
  color: #ffffff;
  background: var(--home-brand);
  border: 1px solid var(--home-brand);
}

.platform-button--primary:hover {
  background: var(--home-brand-hover);
}

.platform-button--secondary {
  color: var(--home-brand-strong);
  background: var(--home-surface-glass);
  border: 1px solid var(--home-border-strong);
}

.platform-button--secondary:hover {
  background: var(--home-surface);
  border-color: var(--home-brand-border-strong);
}

.home-band {
  padding-block: 104px;
}

.home-band--modules {
  background: var(--home-canvas);
}

.home-band--roadmap {
  background: var(--home-canvas-soft);
}

.home-container {
  width: min(1240px, calc(100% - 40px));
  margin-inline: auto;
}

.home-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.technology-stream {
  padding-block: 82px;
  overflow: hidden;
  color: var(--home-console-text);
  background: #112b25;
}

:global(.dark) .technology-stream {
  background: #071310;
}

.technology-stream__heading {
  margin-bottom: 30px;
}

.technology-stream__viewport {
  width: 100%;
  padding-block: 7px;
  overflow: hidden;
  outline-offset: -2px;
  mask-image: linear-gradient(to right, transparent, #000 8%, #000 92%, transparent);
}

.technology-stream__track {
  display: flex;
  width: max-content;
  gap: 12px;
  padding-inline: 6px;
  animation: technology-marquee 54s linear infinite;
  will-change: transform;
}

.technology-stream__viewport--reverse .technology-stream__track {
  animation-direction: reverse;
  animation-duration: 62s;
}

.technology-stream__viewport:hover .technology-stream__track,
.technology-stream__viewport:focus .technology-stream__track,
.is-page-hidden .technology-stream__track {
  animation-play-state: paused;
}

.technology-stream__track span {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  padding: 8px 14px;
  color: #d8e9e4;
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  background: #183b33;
  border: 1px solid #315d52;
  border-radius: 7px;
}

.module-domains {
  border-bottom: 1px solid var(--home-border);
}

.module-domain {
  --domain-accent: var(--home-brand);
  --domain-soft: var(--home-brand-soft);
  --domain-border: var(--home-brand-border);
  display: grid;
  grid-template-columns: minmax(190px, 3fr) minmax(0, 9fr);
  gap: 48px;
  padding-block: 52px;
  border-top: 1px solid var(--home-border);
}

.module-domain--runtime {
  --domain-accent: var(--home-blue);
  --domain-soft: var(--home-blue-soft);
  --domain-border: var(--home-blue-border);
}

.module-domain--communication {
  --domain-accent: var(--home-amber);
  --domain-soft: var(--home-amber-soft);
  --domain-border: var(--home-amber-border);
}

.module-domain--management {
  --domain-accent: var(--home-neutral);
  --domain-soft: var(--home-neutral-soft);
  --domain-border: var(--home-neutral-border);
}

.module-domain__heading {
  align-self: start;
  padding-top: 3px;
}

.module-domain__line {
  display: block;
  width: 40px;
  height: 3px;
  margin-bottom: 18px;
  background: var(--domain-accent);
  border-radius: 2px;
}

.module-domain__heading h3 {
  margin: 0;
  color: var(--home-text-1);
  font-size: 22px;
  font-weight: 750;
  line-height: 1.35;
}

.module-domain__heading p {
  max-width: 24ch;
  margin: 10px 0 0;
  color: var(--home-text-2);
  font-size: 14px;
  line-height: 1.65;
}

.module-domain__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.module-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 238px;
  flex-direction: column;
  padding: 22px;
  color: inherit;
  text-decoration: none;
  background: var(--home-surface);
  border: 1px solid var(--home-border);
  border-radius: 8px;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.module-card:hover,
.module-card:focus-visible {
  border-color: var(--domain-border);
  box-shadow: 0 12px 30px rgb(27 69 60 / 0.1);
  transform: translateY(-2px);
}

:global(.dark) .module-card:hover,
:global(.dark) .module-card:focus-visible {
  box-shadow: 0 12px 30px rgb(0 0 0 / 0.28);
}

.module-card:focus-visible {
  outline: 3px solid var(--domain-accent);
  outline-offset: 3px;
}

.module-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.module-card__icon,
.roadmap-card__icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: var(--home-brand);
  background: var(--home-surface-muted);
  border: 1px solid var(--home-border);
  border-radius: 7px;
}

.module-card__icon {
  color: var(--domain-accent);
  background: var(--domain-soft);
  border-color: var(--domain-border);
}

.module-card__arrow {
  flex: 0 0 auto;
  color: var(--home-text-3);
  transition: color 180ms ease, transform 180ms ease;
}

.module-card:hover .module-card__arrow,
.module-card:focus-visible .module-card__arrow {
  color: var(--domain-accent);
  transform: translate(2px, -2px);
}

.module-card h4 {
  margin-top: 18px;
  color: var(--home-text-1);
  font-size: 17px;
  font-weight: 750;
  line-height: 1.35;
}

.module-card > p {
  margin-top: 8px;
  color: var(--home-text-2);
  font-size: 14px;
  line-height: 1.65;
}

.roadmap-card ul {
  display: grid;
  gap: 7px;
  margin: auto 0 0;
  padding: 18px 0 0;
  list-style: none;
  border-top: 1px solid var(--home-border);
}

.roadmap-card li {
  position: relative;
  padding-left: 14px;
  color: var(--home-text-3);
  font-size: 12px;
  line-height: 1.45;
}

.roadmap-card li::before {
  position: absolute;
  top: 0.55em;
  left: 0;
  width: 5px;
  height: 5px;
  content: "";
  background: var(--home-brand);
  border-radius: 50%;
}

.module-card ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  margin: auto 0 0;
  padding: 18px 0 0;
  list-style: none;
}

.module-card li {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 7px;
  color: var(--home-text-3);
  font-size: 12px;
  line-height: 1.45;
}

.module-card li svg {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--domain-accent);
}

.module-card li span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.roadmap-layout {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px;
}

.roadmap-group__heading {
  min-height: 108px;
  padding-bottom: 20px;
  border-bottom: 2px solid var(--home-amber-border);
}

.roadmap-group__heading h3 {
  color: var(--home-text-1);
  font-size: 18px;
  font-weight: 750;
}

.roadmap-group__heading p {
  margin-top: 7px;
  color: var(--home-text-2);
  font-size: 13px;
  line-height: 1.55;
}

.roadmap-group__items {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.roadmap-card {
  display: flex;
  min-height: 280px;
  flex-direction: column;
  padding: 20px;
  background: var(--home-surface);
  border: 1px solid var(--home-border);
  border-radius: 8px;
  box-shadow: var(--home-shadow-sm);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.roadmap-card:hover,
.roadmap-card:focus-within {
  border-color: var(--home-amber-border);
  box-shadow: var(--home-shadow-md);
  transform: translateY(-3px);
}

.roadmap-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.roadmap-card__icon {
  color: var(--home-amber);
}

.roadmap-card__status {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  padding: 3px 8px;
  color: var(--home-amber);
  font-size: 11px;
  font-weight: 720;
  line-height: 1;
  background: var(--home-amber-soft);
  border: 1px solid var(--home-amber-border);
  border-radius: 5px;
}

.roadmap-card h4 {
  margin-top: 18px;
  color: var(--home-text-1);
  font-size: 16px;
  font-weight: 720;
  line-height: 1.4;
}

.roadmap-card > p {
  margin-top: 8px;
  color: var(--home-text-2);
  font-size: 13px;
  line-height: 1.6;
}

.roadmap-card li::before {
  background: var(--home-amber);
}

[data-reveal] {
  opacity: 0;
  transform: translateY(16px);
}

[data-reveal].is-visible {
  opacity: 1;
  transform: translateY(0);
  transition: opacity 500ms ease var(--reveal-delay, 0ms), transform 500ms cubic-bezier(0.22, 1, 0.36, 1) var(--reveal-delay, 0ms);
}

@keyframes technology-marquee {
  to { transform: translate3d(calc(-50% - 6px), 0, 0); }
}

@media (max-width: 959px) {
  .module-domain {
    grid-template-columns: 1fr;
    gap: 28px;
    padding-block: 44px;
  }

  .module-domain__heading p {
    max-width: 58ch;
  }
}

@media (max-width: 820px) {
  .platform-hero {
    min-height: 0;
    padding-top: 90px;
  }

  .platform-hero h1 {
    font-size: 52px;
  }

  .platform-hero__title img {
    width: 60px;
    height: 60px;
  }

  .platform-hero__slogan {
    font-size: 21px;
  }

  .home-band {
    padding-block: 82px;
  }

  .roadmap-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .roadmap-group:last-child {
    grid-column: 1 / -1;
  }

  .roadmap-group:last-child .roadmap-group__items {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .platform-hero {
    padding-top: 76px;
  }

  .platform-hero h1 {
    font-size: 42px;
  }

  .platform-hero__title {
    gap: 11px;
  }

  .platform-hero__title img {
    width: 50px;
    height: 50px;
  }

  .platform-hero__stack {
    flex-wrap: wrap;
    justify-content: center;
  }

  .platform-hero__slogan {
    font-size: 19px;
  }

  .platform-hero__description {
    font-size: 15px;
  }

  .platform-hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .platform-button {
    width: 100%;
  }

  .home-container {
    width: calc(100% - 24px);
  }

  .home-band {
    padding-block: 64px;
  }

  .technology-stream {
    padding-block: 64px;
  }

  .module-domain__grid {
    grid-template-columns: 1fr;
  }

  .module-card {
    min-height: 0;
  }

  .roadmap-layout,
  .roadmap-group:last-child .roadmap-group__items {
    grid-template-columns: 1fr;
  }

  .roadmap-group:last-child {
    grid-column: auto;
  }
}

@media (max-width: 420px) {
  .module-card ul {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  [data-reveal] {
    opacity: 1;
    transform: none;
  }

  [data-reveal].is-visible,
  .module-card,
  .roadmap-card,
  .platform-button {
    transition: none;
  }

  .technology-stream__track {
    animation: none;
  }

  .technology-stream__viewport {
    overflow-x: auto;
    mask-image: none;
  }
}
</style>
