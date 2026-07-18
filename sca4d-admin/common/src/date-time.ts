import dayjs, { type ConfigType, type Dayjs } from "dayjs";
import utc from "dayjs/plugin/utc";
import timezone from "dayjs/plugin/timezone";

dayjs.extend(utc);
dayjs.extend(timezone);

/** 管理端解析和展示无时区时间时使用的默认时区。 */
export const DEFAULT_TIME_ZONE = "Asia/Shanghai";
/** 管理端日期时间的默认展示格式。 */
export const DEFAULT_DATE_TIME_FORMAT = "YYYY-MM-DD HH:mm:ss";

/** 把输入解析为 dayjs 对象；调用方通过 isValid() 判断空值或无效值。 */
export function parseDateTime(value: ConfigType): Dayjs {
  return dayjs(value);
}

/** 按指定格式和时区展示后端时间；空值或无效值返回占位符。 */
export function formatDateTime(
  value: ConfigType,
  format = DEFAULT_DATE_TIME_FORMAT,
  timeZone = DEFAULT_TIME_ZONE
): string {
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.tz(timeZone).format(format) : "--";
}

/** 把 Date、dayjs 或字符串转换为后端接受的 ISO 时间。 */
export function toApiDateTime(value: ConfigType): string | null {
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.toISOString() : null;
}
