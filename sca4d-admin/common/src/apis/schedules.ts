import type { ApiClient, ApiResult } from "../api-client";
import type {
  Id,
  PageData,
  PageQuery,
  QueryParameters,
  ScheduleExecutionView,
  SchedulePageQuery,
  ScheduleStatus,
  ScheduleTaskTypeView,
  ScheduleView,
  ScheduleWriteInput,
  SystemScheduledJobView
} from "../types";

/** 创建平台 Schedule 查询、写入、删除和人工执行请求方法。 */
export function createPlatformScheduleApi(client: ApiClient) {
  const root = "/mg/platform/schedules";
  return {
    page(query: SchedulePageQuery, signal?: AbortSignal): Promise<PageData<ScheduleView>> {
      return client.get(root, query, signal);
    },
    detail(scheduleId: Id, signal?: AbortSignal): Promise<ApiResult<ScheduleView>> {
      return client.requestResult({
        path: `${root}/${scheduleId}`,
        ...(signal ? { signal } : {})
      });
    },
    taskTypes(): Promise<ScheduleTaskTypeView[]> {
      return client.get("/mg/platform/schedule-task-types");
    },
    create(input: ScheduleWriteInput, idempotencyKey: string): Promise<ApiResult<ScheduleView>> {
      return client.requestResult({
        path: root,
        method: "POST",
        body: input,
        headers: { "Idempotency-Key": idempotencyKey }
      });
    },
    update(scheduleId: Id, input: ScheduleWriteInput & { status: ScheduleStatus }, etag: string): Promise<ApiResult<ScheduleView>> {
      return client.requestResult({
        path: `${root}/${scheduleId}`,
        method: "PUT",
        body: input,
        headers: { "If-Match": etag }
      });
    },
    delete(scheduleId: Id, version: Id): Promise<ApiResult<void>> {
      return client.requestResult({
        path: `${root}/${scheduleId}`,
        method: "DELETE",
        headers: { "If-Match": `"${version}"` }
      });
    },
    executions(
      scheduleId: Id,
      query: PageQuery & QueryParameters,
      signal?: AbortSignal
    ): Promise<PageData<ScheduleExecutionView>> {
      return client.get(`${root}/${scheduleId}/executions`, query, signal);
    },
    execution(scheduleId: Id, executionId: Id, signal?: AbortSignal): Promise<ScheduleExecutionView> {
      return client.get(`${root}/${scheduleId}/executions/${executionId}`, undefined, signal);
    },
    execute(scheduleId: Id, idempotencyKey: string): Promise<ApiResult<ScheduleExecutionView>> {
      return client.requestResult({
        path: `${root}/${scheduleId}/executions`,
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey }
      });
    }
  };
}

/** 创建只读查询系统 Quartz Job 状态的请求方法。 */
export function createSystemScheduledJobApi(client: ApiClient) {
  const root = "/mg/system/scheduled-jobs";
  return {
    jobs(): Promise<SystemScheduledJobView[]> {
      return client.get(root);
    },
    detail(jobGroup: string, jobName: string): Promise<SystemScheduledJobView> {
      return client.get(`${root}/${encodeURIComponent(jobGroup)}/${encodeURIComponent(jobName)}`);
    }
  };
}
