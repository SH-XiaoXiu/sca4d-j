import type { ObservableController } from '@sca4d/admin';

import { onScopeDispose, shallowReadonly, shallowRef } from 'vue';
/** 按组件作用域订阅不可变 Controller 快照，离开后释放监听器。 */
export function useSjControllerState<State extends object>(
  controller: ObservableController<State>,
) {
  const state = shallowRef<Readonly<State>>(controller.state);
  const unsubscribe = controller.subscribe((next) => {
    state.value = next;
  });
  onScopeDispose(unsubscribe);
  return shallowReadonly(state);
}
