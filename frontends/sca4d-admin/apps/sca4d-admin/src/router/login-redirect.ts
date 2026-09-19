/**
 * 读取登录后的站内跳转地址。
 *
 * 绝对 URL、协议相对地址、反斜杠路径和登录页自身都会回退到默认首页。
 */
export function safeLoginRedirect(value: unknown, fallback: string): string {
  if (typeof value !== 'string' || value.length === 0) return fallback;

  let candidate: string;
  try {
    candidate = decodeURIComponent(value);
  } catch {
    return fallback;
  }

  if (
    !candidate.startsWith('/') ||
    candidate.startsWith('//') ||
    candidate.includes('\\') ||
    candidate.startsWith('/auth/login') ||
    candidate.startsWith('/auth/code-login') ||
    [...candidate].some((character) => {
      const code = character.codePointAt(0) ?? 0;
      return code < 32 || code === 127;
    })
  ) {
    return fallback;
  }

  return candidate;
}
