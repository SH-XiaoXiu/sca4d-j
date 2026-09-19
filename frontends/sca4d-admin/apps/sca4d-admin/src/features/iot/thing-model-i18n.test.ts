import { describe, expect, it } from 'vitest';

import { collectThingModelTranslationFields } from './thing-model-i18n';

describe('collectThingModelTranslationFields', () => {
  it('按稳定 code 收集根、成员和递归值类型的翻译路径', () => {
    const fields = collectThingModelTranslationFields({
      commands: [
        {
          code: 'set/mode',
          inputs: [
            {
              code: 'payload',
              valueType: {
                kind: 'OBJECT',
                optionalFields: [
                  {
                    code: 'note',
                    valueType: { kind: 'STRING' },
                  },
                ],
                requiredFields: [
                  {
                    code: 'mode',
                    valueType: {
                      enumValues: [{ code: 'eco~mode' }],
                      kind: 'ENUM',
                    },
                  },
                ],
              },
            },
          ],
        },
      ],
      events: [],
      formatVersion: 2,
      productCode: 'air-conditioner',
      properties: [
        {
          code: 'samples',
          valueType: {
            elementType: {
              enumValues: [{ code: 'ok' }],
              kind: 'ENUM',
            },
            kind: 'ARRAY',
          },
        },
      ],
      version: 1,
    });

    expect(fields).toEqual([
      { fallback: 'air-conditioner', path: '/displayName' },
      { fallback: 'samples', path: '/properties/samples/displayName' },
      {
        fallback: 'ok',
        path: '/properties/samples/valueType/elementType/enumValues/ok/displayName',
      },
      { fallback: 'set/mode', path: '/commands/set~1mode/displayName' },
      {
        fallback: 'payload',
        path: '/commands/set~1mode/inputs/payload/displayName',
      },
      {
        fallback: 'mode',
        path: '/commands/set~1mode/inputs/payload/valueType/requiredFields/mode/displayName',
      },
      {
        fallback: 'eco~mode',
        path: '/commands/set~1mode/inputs/payload/valueType/requiredFields/mode/valueType/enumValues/eco~0mode/displayName',
      },
      {
        fallback: 'note',
        path: '/commands/set~1mode/inputs/payload/valueType/optionalFields/note/displayName',
      },
    ]);
    expect(Object.isFrozen(fields)).toBe(true);
  });

  it('拒绝缺少必需数组和产生重复稳定路径的模型', () => {
    expect(() =>
      collectThingModelTranslationFields({
        commands: [],
        events: [],
        productCode: 'demo',
      }),
    ).toThrow('properties 必须是 JSON 数组');

    expect(() =>
      collectThingModelTranslationFields({
        commands: [],
        events: [],
        productCode: 'demo',
        properties: [
          { code: 'same', valueType: { kind: 'BOOLEAN' } },
          { code: 'same', valueType: { kind: 'BOOLEAN' } },
        ],
      }),
    ).toThrow('ThingModel 包含重复展示路径');
  });
});
