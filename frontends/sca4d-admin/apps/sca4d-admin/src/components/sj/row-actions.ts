import type { Component } from 'vue';
/** 危险操作确认时显示的对象与后果。 */
export interface SjActionConfirm {
  /** 确认标题。 */ readonly title: string;
  /** 对象和直接后果。 */ readonly content?: string;
  /** 确认动作名称。 */ readonly okText: string;
}
/** 调用方已经完成权限及状态判断的对象命令。 */
export interface SjRowAction {
  /** 稳定动作标识。 */ readonly key: string;
  /** 用户可理解的动作名称。 */ readonly label: string;
  /** 动作图标。 */ readonly icon: Component;
  /** 未授权时不展示。 */ readonly visible?: boolean;
  /** 当前状态不允许执行。 */ readonly disabled?: boolean;
  /** 禁用的具体原因。 */ readonly disabledReason?: string;
  /** 调用方的提交状态。 */ readonly loading?: boolean;
  /** 是否造成对象或访问失效。 */ readonly danger?: boolean;
  /** 执行前确认内容。 */ readonly confirm?: SjActionConfirm;
  /** 执行命令；失败必须抛出或由流程完整展示。 */ readonly run:
    | (() => Promise<unknown>)
    | (() => void);
}
