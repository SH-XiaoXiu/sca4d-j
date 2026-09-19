import type { PermissionNode } from './types';

import { describe, expect, it } from 'vitest';

import { grantablePermissionCodes, permissionTree } from './permissions';

const nodes: readonly PermissionNode[] = [
  {
    code: 'platform',
    parentCode: null,
    nameKey: 'permission.platform',
    name: 'Platform',
    type: 'MODULE',
    grantable: false,
    sortOrder: 20,
    children: [
      {
        code: 'platform:accounts:read',
        parentCode: 'platform',
        nameKey: 'permission.platform.accounts.read',
        name: 'Read accounts',
        type: 'ACTION',
        grantable: true,
        sortOrder: 10,
        children: [],
      },
    ],
  },
];

describe('permission tree', () => {
  it('keeps grouping nodes and extracts only grantable codes', () => {
    const tree = permissionTree(nodes);

    expect(tree[0]?.title).toBe('Platform');
    expect(tree[0]?.children[0]?.key).toBe('platform:accounts:read');
    expect(grantablePermissionCodes(nodes)).toEqual(['platform:accounts:read']);
    expect(Object.isFrozen(tree)).toBe(true);
  });
});
