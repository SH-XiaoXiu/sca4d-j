import { describe, expect, it } from 'vitest';

import {
  resourceReferenceId,
  resourceReferenceKey,
  resourceReferenceLabel,
} from './resource-reference';

describe('resource reference', () => {
  it('normalizes prefixed role identifiers for lookup and routing', () => {
    expect(resourceReferenceId('role', 'platform:41')).toBe('41');
    expect(resourceReferenceKey('role', 'platform:41')).toBe('role:41');
  });

  it('keeps other identifiers unchanged', () => {
    expect(resourceReferenceId('account', '9007199254740993')).toBe(
      '9007199254740993',
    );
    expect(resourceReferenceKey('file', '41')).toBe('file:41');
  });

  it('uses name, type label, identifier, then empty text in that order', () => {
    expect(
      resourceReferenceLabel(
        {
          id: '41',
          name: 'Administrator',
          resourceType: 'account',
          typeLabel: 'Account',
        },
        '-',
      ),
    ).toBe('Administrator');
    expect(
      resourceReferenceLabel(
        { id: '41', name: null, resourceType: 'account', typeLabel: 'Account' },
        '-',
      ),
    ).toBe('Account');
    expect(
      resourceReferenceLabel(
        { id: '41', name: null, resourceType: 'account', typeLabel: null },
        '-',
      ),
    ).toBe('41');
    expect(
      resourceReferenceLabel(
        { id: null, name: null, resourceType: null, typeLabel: null },
        '-',
      ),
    ).toBe('-');
  });
});
