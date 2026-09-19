import type { RouteLocationNormalizedLoaded } from 'vue-router';

import { describe, expect, it } from 'vitest';

import { resolveActiveMenuPath } from './resolve-active-menu-path';

function route(
  path: string,
  activePath: string,
  params: RouteLocationNormalizedLoaded['params'],
) {
  return { meta: { activePath }, params, path } as Pick<
    RouteLocationNormalizedLoaded,
    'meta' | 'params' | 'path'
  >;
}

describe('resolveActiveMenuPath', () => {
  it('项目详情的三个分区解析为同一个固定项目菜单路径', () => {
    const activePath = '/projects/:projectId/groups';

    for (const section of ['groups', 'members', 'roles']) {
      expect(
        resolveActiveMenuPath(
          route(`/projects/20/${section}`, activePath, { projectId: '20' }),
        ),
      ).toBe('/projects/20/groups');
    }
  });

  it('动态参数缺失时保留当前路由路径', () => {
    expect(
      resolveActiveMenuPath(
        route('/projects', '/projects/:projectId/groups', {}),
      ),
    ).toBe('/projects');
  });
});
