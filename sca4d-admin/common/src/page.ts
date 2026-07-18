import type { PageData, PageResult } from "./types";

function nonNegativeInteger(value: string, field: string): number {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 0) {
    throw new TypeError(`分页字段 ${field} 不是有效的安全整数: ${value}`);
  }
  return number;
}

/** 把后端字符串分页字段转换为页面使用的 number。 */
export function normalizePageData<T>(page: PageData<T>): PageResult<T> {
  return {
    records: page.records,
    page: nonNegativeInteger(page.page, "page"),
    size: nonNegativeInteger(page.size, "size"),
    total: nonNegativeInteger(page.total, "total")
  };
}
