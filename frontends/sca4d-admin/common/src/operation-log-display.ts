import type { OperationLog } from './types';

import type { ResourceReferenceData } from './resource-reference';

import {
  resourceReferenceKey,
  resourceReferenceLabel,
} from './resource-reference';

/** 操作日志列表展示时使用的可本地化文本。 */
export interface OperationLogDisplayText {
  readonly empty: string;
  readonly operationLabels: Readonly<Record<string, string>>;
  readonly targetNames?: Readonly<Record<string, string>>;
  readonly targetTypeLabels: Readonly<Record<string, string>>;
}

/**
 * 返回操作日志在列表中显示的业务动作名称。
 *
 * @param log 后端返回的操作日志
 * @param text 页面当前语言下的展示文本
 * @return 已登记操作返回业务名称，未知历史操作返回格式化后的操作编码
 */
export function operationLogActionText(
  log: OperationLog,
  text: OperationLogDisplayText,
): string {
  return text.operationLabels[log.operation] ?? readableCode(log.operation);
}

/**
 * 返回操作日志在列表中显示的目标资源名称。
 *
 * @param log 后端返回的操作日志
 * @param text 页面当前语言下的展示文本
 * @return 存在目标时返回资源类型名称和资源标识，否则返回空值占位
 */
export function operationLogTargetText(
  log: OperationLog,
  text: OperationLogDisplayText,
): string {
  const reference = operationLogTargetReference(log, text);
  if (!reference.id) return text.empty;
  return `${resourceReferenceLabel(reference, text.empty)} ${reference.id}`;
}

/** 把日志目标转换成统一组件可以显示和定位的资源引用。 */
export function operationLogTargetReference(
  log: OperationLog,
  text: OperationLogDisplayText,
): ResourceReferenceData {
  const resourceType = log.targetType?.trim() || null;
  const id = log.targetId?.trim() || null;
  if (!resourceType || !id) {
    return { id: null, name: null, resourceType: null, typeLabel: null };
  }
  const key = resourceReferenceKey(resourceType, id);
  return {
    id,
    name: key ? (text.targetNames?.[key] ?? null) : null,
    resourceType,
    typeLabel: resourceType
      ? (text.targetTypeLabels[resourceType] ?? readableCode(resourceType))
      : null,
  };
}

/**
 * 把历史操作或未知资源类型转换成比原始分隔符更容易阅读的文本。
 *
 * @param code 后端保存的稳定编码
 * @return 用空格连接的编码片段
 */
export function readableCode(code: string): string {
  return code.split(/[/:.]/u).filter(Boolean).join(' ');
}
