import { onScopeDispose } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

import { Modal } from 'ant-design-vue';

import { useSjPresentation } from './sj-presentation';

/** 在切换对象、离开路由和关闭标签页前保护仍可继续编辑的草稿。 */
export function useSjDraftGuard(
  dirty: () => boolean,
  busy: () => boolean = () => false,
) {
  const { t } = useSjPresentation();
  /** 返回是否可以放弃当前草稿；正在提交时停止离开。 */
  function confirmDiscard(changed = dirty()): Promise<boolean> {
    if (busy()) return Promise.resolve(false);
    if (!changed) return Promise.resolve(true);
    return new Promise((resolve) => {
      Modal.confirm({
        title: t('discardChanges'),
        okText: t('discard'),
        cancelText: t('cancel'),
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  }
  /** 浏览器关闭时使用浏览器原生提示，不发送额外请求。 */
  function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty() || busy()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
  window.addEventListener('beforeunload', beforeUnload);
  onScopeDispose(() =>
    window.removeEventListener('beforeunload', beforeUnload),
  );
  onBeforeRouteLeave(() => confirmDiscard());
  return { confirmDiscard };
}
