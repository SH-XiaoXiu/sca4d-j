/** 从大小写不敏感的响应头中读取 ETag。 */
export function responseEtag(
  headers: Readonly<Record<string, string>>,
): null | string {
  const entry = Object.entries(headers).find(
    ([name]) => name.toLowerCase() === 'etag',
  );
  return entry?.[1] ?? null;
}

/** 从大小写不敏感的响应头中读取 Location。 */
export function responseLocation(
  headers: Readonly<Record<string, string>>,
): null | string {
  const entry = Object.entries(headers).find(
    ([name]) => name.toLowerCase() === 'location',
  );
  return entry?.[1] ?? null;
}
