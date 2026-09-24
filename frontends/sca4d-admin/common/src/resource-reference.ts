/** 页面展示资源引用时使用的名称、类型和稳定标识。 */
export interface ResourceReferenceData {
  readonly id: null | string;
  readonly name: null | string;
  readonly resourceType: null | string;
  readonly typeLabel: null | string;
}

/** 返回可以用于 Map 查找和路由定位的资源引用键。 */
export function resourceReferenceKey(
  resourceType: null | string | undefined,
  id: null | string | undefined,
): null | string {
  const type = textOrNull(resourceType);
  const normalizedId = resourceReferenceId(resourceType, id);
  return type && normalizedId ? `${type}:${normalizedId}` : null;
}

/** 去除应用在审计 Resource 标识上使用的领域前缀。 */
export function resourceReferenceId(
  resourceType: null | string | undefined,
  id: null | string | undefined,
): null | string {
  const value = textOrNull(id);
  if (!value) return null;
  return resourceType === 'IAM/ROLE' && value.startsWith('platform:')
    ? value.slice('platform:'.length)
    : value;
}

/** 返回资源引用在没有名称时使用的主要文本。 */
export function resourceReferenceLabel(
  reference: ResourceReferenceData,
  empty: string,
): string {
  return reference.name || reference.typeLabel || reference.id || empty;
}

/** 返回非空文本，保留 ID 的原始字符串形式。 */
function textOrNull(value: null | string | undefined): null | string {
  const checked = value?.trim() ?? '';
  return checked || null;
}
