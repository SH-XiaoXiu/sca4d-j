import { describe, expect, it } from 'vitest';

import {
  resourceQueryId,
  resourceReferenceRoute,
  withoutResourceQuery,
} from './resource-reference-routes';

const ALL_ACCESS = { accounts: true, email: true, roles: true };

describe('resource reference routes', () => {
  it('routes the four supported resource types to their existing pages', () => {
    expect(route('IAM/ACCOUNT', '41')).toEqual({
      path: '/sca4d-server/accounts',
      query: { accountId: '41' },
    });
    expect(route('IAM/ROLE', 'platform:42')).toEqual({
      path: '/sca4d-server/roles',
      query: { roleId: '42' },
    });
    expect(route('NOTIFICATION/EMAIL/SENDERS', '43')).toEqual({
      path: '/sca4d-server/email',
      query: { senderId: '43', tab: 'senders' },
    });
    expect(route('NOTIFICATION/EMAIL/TEMPLATES', '44')).toEqual({
      path: '/sca4d-server/email',
      query: { tab: 'templates', templateId: '44' },
    });
  });

  it('does not route unknown, file, or empty references', () => {
    expect(route('STORAGE/FILE', '41')).toBeNull();
    expect(route('unknown', '41')).toBeNull();
    expect(route('IAM/ACCOUNT', null)).toBeNull();
    expect(route('IAM/ACCOUNT', 'not-an-id')).toBeNull();
  });

  it('does not create links without matching resource read access', () => {
    expect(
      resourceReferenceRoute(
        { id: '41', name: null, resourceType: 'IAM/ACCOUNT', typeLabel: null },
        { accounts: false, email: true, roles: true },
      ),
    ).toBeNull();
  });

  it('reads one query value and removes only consumed keys', () => {
    const query = { accountId: ['41', '42'], page: '2' };
    expect(resourceQueryId(query, 'accountId')).toBe('41');
    expect(resourceQueryId({ accountId: 'not-an-id' }, 'accountId')).toBeNull();
    expect(withoutResourceQuery(query, 'accountId')).toEqual({ page: '2' });
  });
});

function route(resourceType: string, id: null | string) {
  return resourceReferenceRoute(
    { id, name: null, resourceType, typeLabel: null },
    ALL_ACCESS,
  );
}
