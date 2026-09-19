import type { VxeGridInstance } from 'vxe-table';

import type { ExtendedFormApi } from '@vben-core/form-ui';

import { describe, expect, it, vi } from 'vitest';

import { VxeGridApi } from './api';

describe('VxeGridApi mount timing', () => {
  it('resolves concurrent pre-mount queries and rejects unmounted requests', async () => {
    const api = new VxeGridApi();
    const commitProxy = vi.fn().mockResolvedValue(undefined);
    const first = api.query();
    const second = api.reload();
    api.mount(
      { commitProxy } as unknown as VxeGridInstance,
      {} as ExtendedFormApi,
    );
    await Promise.all([first, second]);
    expect(commitProxy).toHaveBeenCalledTimes(2);
    api.unmount();
    const rejected = expect(api.query()).rejects.toThrow();
    api.unmount();
    await rejected;
  });

  it('does not dispatch a stale query into a remounted grid', async () => {
    const api = new VxeGridApi();
    const commitProxy = vi.fn();
    const rejected = expect(api.query()).rejects.toThrow();
    api.mount(
      { commitProxy } as unknown as VxeGridInstance,
      {} as ExtendedFormApi,
    );
    api.unmount();
    api.mount(
      { commitProxy } as unknown as VxeGridInstance,
      {} as ExtendedFormApi,
    );
    await rejected;
    expect(commitProxy).not.toHaveBeenCalled();
  });

  it('propagates query failures', async () => {
    const api = new VxeGridApi();
    api.mount(
      {
        commitProxy: vi.fn().mockRejectedValue(new Error('query failed')),
      } as unknown as VxeGridInstance,
      {} as ExtendedFormApi,
    );
    await expect(api.query()).rejects.toThrow('query failed');
  });
  it('waits for the grid to mount before querying', async () => {
    const api = new VxeGridApi();
    const commitProxy = vi.fn().mockResolvedValue(undefined);

    const query = api.query({ search: 'luminaire' });
    expect(commitProxy).not.toHaveBeenCalled();

    api.mount(
      { commitProxy } as unknown as VxeGridInstance,
      {} as ExtendedFormApi,
    );
    await query;

    expect(commitProxy).toHaveBeenCalledWith('query', {
      search: 'luminaire',
    });
  });

  it('waits for the grid to mount before reloading', async () => {
    const api = new VxeGridApi();
    const commitProxy = vi.fn().mockResolvedValue(undefined);

    const reload = api.reload({ page: 2 });
    expect(commitProxy).not.toHaveBeenCalled();

    api.mount(
      { commitProxy } as unknown as VxeGridInstance,
      {} as ExtendedFormApi,
    );
    await reload;

    expect(commitProxy).toHaveBeenCalledWith('reload', { page: 2 });
  });
});
