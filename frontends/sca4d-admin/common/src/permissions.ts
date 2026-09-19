import type { PermissionNode } from './types';

/** Ant Design 等树组件可直接消费的框架无关权限节点。 */
export interface PermissionTreeItem {
  readonly children: readonly PermissionTreeItem[];
  readonly code: string;
  readonly grantable: boolean;
  readonly key: string;
  readonly title: string;
}

/** 把后端权限目录转换为稳定、不可变的展示树。 */
export function permissionTree(
  nodes: readonly PermissionNode[],
): readonly PermissionTreeItem[] {
  return Object.freeze(
    [...nodes]
      .toSorted(
        (left, right) =>
          left.sortOrder - right.sortOrder ||
          left.code.localeCompare(right.code),
      )
      .map((node) =>
        Object.freeze({
          key: node.code,
          title: node.name,
          code: node.code,
          grantable: node.grantable,
          children: permissionTree(node.children),
        }),
      ),
  );
}

/** 只返回可以授予角色的权限编码，忽略模块和资源分组节点。 */
export function grantablePermissionCodes(
  nodes: readonly PermissionNode[],
): readonly string[] {
  const result: string[] = [];
  const visit = (items: readonly PermissionNode[]): void => {
    items.forEach((item) => {
      if (item.grantable) result.push(item.code);
      visit(item.children);
    });
  };
  visit(nodes);
  return Object.freeze(result);
}
