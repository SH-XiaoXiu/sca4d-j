/** 一个物模型展示字段的稳定路径和缺少翻译时使用的 code。 */
export interface ThingModelTranslationField {
  readonly fallback: string;
  readonly path: string;
}

/**
 * 从格式 2 物模型 JSON 收集后端要求的全部多语言展示路径。
 *
 * 路径使用成员 code 而不是 JSON 数组下标，模型调整顺序后原翻译仍然有效。
 */
export function collectThingModelTranslationFields(
  model: unknown,
): readonly ThingModelTranslationField[] {
  const root = requireObject(model, 'ThingModel');
  const fields: ThingModelTranslationField[] = [];
  const paths = new Set<string>();

  const add = (path: string, fallback: string): void => {
    if (paths.has(path)) {
      throw new Error(`ThingModel 包含重复展示路径: ${path}`);
    }
    paths.add(path);
    fields.push(Object.freeze({ fallback, path }));
  };

  add('/displayName', requireText(root, 'productCode'));
  visitFields(requireArray(root, 'properties'), '/properties', add);
  visitOperations(requireArray(root, 'events'), '/events', 'outputs', add);
  visitOperations(requireArray(root, 'commands'), '/commands', 'inputs', add);
  return Object.freeze(fields);
}

type AddTranslationField = (path: string, fallback: string) => void;

function visitFields(
  values: readonly unknown[],
  base: string,
  add: AddTranslationField,
): void {
  for (const value of values) {
    const field = requireObject(value, base);
    const code = requireText(field, 'code');
    const path = `${base}/${escapePointer(code)}`;
    add(`${path}/displayName`, code);
    visitValueType(field.valueType, `${path}/valueType`, add);
  }
}

function visitOperations(
  values: readonly unknown[],
  base: string,
  memberName: 'inputs' | 'outputs',
  add: AddTranslationField,
): void {
  for (const value of values) {
    const operation = requireObject(value, base);
    const code = requireText(operation, 'code');
    const path = `${base}/${escapePointer(code)}`;
    add(`${path}/displayName`, code);
    visitFields(
      requireArray(operation, memberName),
      `${path}/${memberName}`,
      add,
    );
  }
}

function visitValueType(
  value: unknown,
  base: string,
  add: AddTranslationField,
): void {
  const type = requireObject(value, base);
  for (const item of optionalArray(type, 'enumValues')) {
    const enumValue = requireObject(item, `${base}/enumValues`);
    const code = requireText(enumValue, 'code');
    add(`${base}/enumValues/${escapePointer(code)}/displayName`, code);
  }
  visitFields(
    optionalArray(type, 'requiredFields'),
    `${base}/requiredFields`,
    add,
  );
  visitFields(
    optionalArray(type, 'optionalFields'),
    `${base}/optionalFields`,
    add,
  );
  if (type.elementType !== undefined && type.elementType !== null) {
    visitValueType(type.elementType, `${base}/elementType`, add);
  }
}

function requireObject(
  value: unknown,
  location: string,
): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError(`${location} 必须是 JSON 对象`);
  }
  return value as Record<string, unknown>;
}

function requireArray(
  value: Record<string, unknown>,
  name: string,
): readonly unknown[] {
  const result = value[name];
  if (!Array.isArray(result)) {
    throw new TypeError(`${name} 必须是 JSON 数组`);
  }
  return result;
}

function optionalArray(
  value: Record<string, unknown>,
  name: string,
): readonly unknown[] {
  const result = value[name];
  if (result === undefined) return [];
  if (!Array.isArray(result)) {
    throw new TypeError(`${name} 必须是 JSON 数组`);
  }
  return result;
}

function requireText(value: Record<string, unknown>, name: string): string {
  const result = value[name];
  if (typeof result !== 'string' || result.length === 0) {
    throw new TypeError(`${name} 必须是非空字符串`);
  }
  return result;
}

function escapePointer(value: string): string {
  return value.replaceAll('~', '~0').replaceAll('/', '~1');
}
