import { describe, expect, it } from 'vitest';

import { safeLoginRedirect } from './login-redirect';

describe('登录后站内跳转', () => {
  it('保留普通站内路径及查询参数', () => {
    expect(safeLoginRedirect('/accounts?status=ACTIVE', '/profile')).toBe(
      '/accounts?status=ACTIVE',
    );
  });

  it('兼容旧链接中经过一次编码的站内路径', () => {
    expect(safeLoginRedirect('%2Froles%3Ftab%3Dmine', '/profile')).toBe(
      '/roles?tab=mine',
    );
  });

  it.each([
    'https://example.com',
    '//example.com/path',
    String.raw`/\example.com/path`,
    '/auth/login',
    '/auth/code-login',
    '%E0%A4%A',
  ])('拒绝外部或不安全目标 %s', (target) => {
    expect(safeLoginRedirect(target, '/profile')).toBe('/profile');
  });
});
