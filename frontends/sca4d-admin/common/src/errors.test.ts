import { describe, expect, it } from 'vitest';

import { mapHttpError } from './errors';

describe('hTTP error mapping', () => {
  it.each([
    [401, 'authentication'],
    [403, 'permission'],
    [409, 'conflict'],
    [412, 'versionConflict'],
    [428, 'precondition'],
    [503, 'unavailable'],
  ] as const)('maps status %s to %s', (status, kind) => {
    const data = [
      { rowNumber: 2, column: 'location', code: 'LOCATION_INVALID' },
    ];
    expect(
      mapHttpError(status, {
        callId: 'call-1',
        code: 'x',
        data,
        message: 'message',
      }),
    ).toMatchObject({
      status,
      kind,
      code: 'x',
      callId: 'call-1',
      data,
      message: 'message',
    });
  });
});
