import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type {
  CreateEmailSenderInput,
  EmailSender,
  EmailTemplate,
  EmailTemplateInput,
  EmailTemplatePage,
  Int64String,
  LocaleCatalog,
  PreviewEmailTemplateInput,
  RenderedEmail,
  ReplaceEmailSenderInput,
} from './types';

import { ObservableController } from './controller';
import { AdminError } from './errors';
import { freezeLocaleCatalog } from './localized-fields';

/** SMTP 配置页面使用的不可变状态。 */
export interface EmailSendersState {
  readonly detail: EmailSender | null;
  readonly detailEtag: null | string;
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly senders: readonly EmailSender[];
}

/** Email 模板页面使用的不可变状态。 */
export interface EmailTemplatesState {
  readonly detail: EmailTemplate | null;
  readonly detailEtag: null | string;
  readonly error: AdminError | null;
  readonly localeCatalog: LocaleCatalog | null;
  readonly page: number;
  readonly phase: ResourcePhase;
  readonly preview: RenderedEmail | null;
  readonly size: number;
  readonly templates: readonly EmailTemplate[];
  readonly total: number;
}

/** 管理 SMTP 配置、密码修改语义、启用状态和连接测试。 */
export class EmailSendersController extends ObservableController<EmailSendersState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      detail: null,
      detailEtag: null,
      error: null,
      phase: 'idle',
      senders: Object.freeze([]),
    });
  }

  /** 清除抽屉使用的 SMTP 配置详情和 ETag。 */
  clearDetail(): void {
    this.publish({ ...this.state, detail: null, detailEtag: null });
  }

  /** 创建默认停用的 SMTP 配置并刷新列表。 */
  async create(input: CreateEmailSenderInput): Promise<EmailSender> {
    try {
      const result = await this.api.post<EmailSender>(
        '/system/email/senders',
        input,
      );
      await this.load();
      return freezeSender(result.data);
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 按详情 ETag 删除一个停用 SMTP 配置。 */
  async delete(senderId: Int64String): Promise<void> {
    try {
      const current = await this.readVersioned(senderId);
      await this.api.delete(senderPath(senderId), {
        'If-Match': current.etag,
      });
      if (this.state.detail?.senderId === senderId) this.clearDetail();
      await this.load();
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 读取全部 SMTP 配置；空列表仍允许页面创建第一项配置。 */
  async load(): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result = await this.api.get<readonly EmailSender[]>(
        '/system/email/senders',
      );
      this.publish({
        ...this.state,
        error: null,
        phase: 'ready',
        senders: Object.freeze(result.data.map(freezeSender)),
      });
    } catch (error) {
      this.loadFailed(error);
      throw error;
    }
  }

  /** 读取 SMTP 配置详情和后续修改使用的强 ETag。 */
  async require(senderId: Int64String): Promise<EmailSender> {
    try {
      const result = await this.readVersioned(senderId);
      const detail = freezeSender(result.data);
      this.publish({
        ...this.state,
        detail,
        detailEtag: result.etag,
        error: null,
      });
      return detail;
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 按详情 ETag 完整替换 SMTP 配置。 */
  async replace(input: ReplaceEmailSenderInput): Promise<EmailSender> {
    const detail = this.state.detail;
    const etag = this.state.detailEtag;
    if (!detail || !etag) throw new Error('No Email sender detail is loaded');
    try {
      const result = await this.api.put<EmailSender>(
        senderPath(detail.senderId),
        input,
        { 'If-Match': etag },
      );
      if (!result.etag) throw new Error('Email sender response has no ETag');
      const updated = freezeSender(result.data);
      this.publish({
        ...this.state,
        detail: updated,
        detailEtag: result.etag,
        error: null,
      });
      await this.load();
      return updated;
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 按最新详情 ETag 启用或停用 SMTP 配置。 */
  async setEnabled(senderId: Int64String, enabled: boolean): Promise<void> {
    try {
      const current = await this.readVersioned(senderId);
      await this.api.patch<EmailSender>(
        `${senderPath(senderId)}/status`,
        { enabled },
        { 'If-Match': current.etag },
      );
      await this.load();
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 使用指定 SMTP 配置建立并关闭连接，不发送邮件。 */
  async testConnection(senderId: Int64String): Promise<void> {
    try {
      await this.api.post(`${senderPath(senderId)}/connection-tests`);
      this.publish({ ...this.state, error: null });
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 读取详情并拒绝缺失 ETag 的响应。 */
  private async readVersioned(senderId: Int64String) {
    const result = await this.api.get<EmailSender>(senderPath(senderId));
    if (!result.etag) throw new Error('Email sender response has no ETag');
    return { data: result.data, etag: result.etag };
  }

  /** 把列表读取失败转换为 ResourceState 使用的阶段。 */
  private loadFailed(error: unknown): void {
    const value = asAdminError(error);
    this.publish({
      ...this.state,
      error: value,
      phase: failurePhase(value),
    });
  }

  /** 保留已加载列表，只让权限或 ETag 失败替换页面阶段。 */
  private mutationFailed(error: unknown): void {
    const value = asAdminError(error);
    const phase =
      value.kind === 'permission' || value.kind === 'versionConflict'
        ? failurePhase(value)
        : this.state.phase;
    this.publish({ ...this.state, error: value, phase });
  }
}

/** 管理多语言统一通知模板和草稿预览。 */
export class EmailTemplatesController extends ObservableController<EmailTemplatesState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      detail: null,
      detailEtag: null,
      error: null,
      localeCatalog: null,
      page: 1,
      phase: 'idle',
      preview: null,
      size: 20,
      templates: Object.freeze([]),
      total: 0,
    });
  }

  /** 清除抽屉使用的模板详情、ETag 和预览。 */
  clearDetail(): void {
    this.publish({
      ...this.state,
      detail: null,
      detailEtag: null,
      preview: null,
    });
  }

  /** 清除上一次草稿预览。 */
  clearPreview(): void {
    this.publish({ ...this.state, preview: null });
  }

  /** 创建默认停用的完整多语言 Email 模板并刷新列表。 */
  async create(input: EmailTemplateInput): Promise<EmailTemplate> {
    try {
      const result = await this.api.post<EmailTemplate>(
        '/system/email/templates',
        input,
      );
      await this.load(1, this.state.size);
      return freezeTemplate(result.data);
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 按详情 ETag 删除一个停用 Email 模板。 */
  async delete(templateId: Int64String): Promise<void> {
    try {
      const current = await this.readVersioned(templateId);
      await this.api.delete(templatePath(templateId), {
        'If-Match': current.etag,
      });
      if (this.state.detail?.templateId === templateId) this.clearDetail();
      await this.load();
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 同时读取指定模板页和应用 Locale；空列表仍允许创建模板。 */
  async load(page = this.state.page, size = this.state.size): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const [templates, locales] = await Promise.all([
        this.api.get<EmailTemplatePage>('/system/email/templates', {
          page,
          size,
        }),
        this.api.get<LocaleCatalog>('/system/locales'),
      ]);
      if (
        templates.data.items.length === 0 &&
        templates.data.total > 0 &&
        page > 1
      ) {
        await this.load(page - 1, size);
        return;
      }
      this.publish({
        ...this.state,
        error: null,
        localeCatalog: freezeLocaleCatalog(locales.data),
        page: templates.data.page,
        phase: 'ready',
        size: templates.data.size,
        templates: Object.freeze(templates.data.items.map(freezeTemplate)),
        total: templates.data.total,
      });
    } catch (error) {
      this.loadFailed(error);
      throw error;
    }
  }

  /** 使用未保存草稿生成经过服务端安全处理的预览。 */
  async preview(input: PreviewEmailTemplateInput): Promise<RenderedEmail> {
    try {
      const result = await this.api.post<RenderedEmail>(
        '/system/email/templates/previews',
        input,
      );
      const preview = Object.freeze({ ...result.data });
      this.publish({ ...this.state, error: null, preview });
      return preview;
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 读取模板详情、变量集合和后续修改使用的强 ETag。 */
  async require(templateId: Int64String): Promise<EmailTemplate> {
    try {
      const result = await this.readVersioned(templateId);
      const detail = freezeTemplate(result.data);
      this.publish({
        ...this.state,
        detail,
        detailEtag: result.etag,
        error: null,
        preview: null,
      });
      return detail;
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 按详情 ETag 完整替换通知编码和全部 Locale 字段。 */
  async replace(input: EmailTemplateInput): Promise<EmailTemplate> {
    const detail = this.state.detail;
    const etag = this.state.detailEtag;
    if (!detail || !etag) throw new Error('No Email template detail is loaded');
    try {
      const result = await this.api.put<EmailTemplate>(
        templatePath(detail.templateId),
        input,
        { 'If-Match': etag },
      );
      if (!result.etag) throw new Error('Email template response has no ETag');
      const updated = freezeTemplate(result.data);
      this.publish({
        ...this.state,
        detail: updated,
        detailEtag: result.etag,
        error: null,
      });
      await this.load();
      return updated;
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 按最新详情 ETag 启用或停用 Email 模板。 */
  async setEnabled(templateId: Int64String, enabled: boolean): Promise<void> {
    try {
      const current = await this.readVersioned(templateId);
      await this.api.patch<EmailTemplate>(
        `${templatePath(templateId)}/status`,
        { enabled },
        { 'If-Match': current.etag },
      );
      await this.load();
    } catch (error) {
      this.mutationFailed(error);
      throw error;
    }
  }

  /** 读取详情并拒绝缺失 ETag 的响应。 */
  private async readVersioned(templateId: Int64String) {
    const result = await this.api.get<EmailTemplate>(templatePath(templateId));
    if (!result.etag) throw new Error('Email template response has no ETag');
    return { data: result.data, etag: result.etag };
  }

  /** 把列表读取失败转换为 ResourceState 使用的阶段。 */
  private loadFailed(error: unknown): void {
    const value = asAdminError(error);
    this.publish({
      ...this.state,
      error: value,
      phase: failurePhase(value),
    });
  }

  /** 保留已加载列表，只让权限或 ETag 失败替换页面阶段。 */
  private mutationFailed(error: unknown): void {
    const value = asAdminError(error);
    const phase =
      value.kind === 'permission' || value.kind === 'versionConflict'
        ? failurePhase(value)
        : this.state.phase;
    this.publish({ ...this.state, error: value, phase });
  }
}

/** 冻结 SMTP 配置，避免页面改写 Controller 状态。 */
function freezeSender(sender: EmailSender): EmailSender {
  return Object.freeze({ ...sender });
}

/** 冻结模板、多语言富文本和变量集合。 */
function freezeTemplate(template: EmailTemplate): EmailTemplate {
  return Object.freeze({
    ...template,
    content: freezeTemplateContent(template.content),
    variables: Object.freeze([...template.variables]),
  });
}

/** 递归冻结统一通知模板，避免页面改写 Controller 状态。 */
function freezeTemplateContent(
  content: EmailTemplate['content'],
): EmailTemplate['content'] {
  return Object.freeze(
    Object.fromEntries(
      Object.entries(content).map(([locale, value]) => [
        locale,
        Object.freeze({
          body: freezeRichTextNode(value.body),
          title: freezeRichTextNode(value.title),
        }),
      ]),
    ),
  );
}

/** 递归冻结一个 ProseMirror JSON 节点及其属性、marks 和子节点。 */
function freezeRichTextNode<T extends EmailTemplate['content'][string]['body']>(
  node: T,
): T {
  return Object.freeze({
    ...node,
    ...(node.attrs ? { attrs: Object.freeze({ ...node.attrs }) } : {}),
    ...(node.content
      ? { content: Object.freeze(node.content.map(freezeRichTextNode)) }
      : {}),
    ...(node.marks
      ? {
          marks: Object.freeze(
            node.marks.map((mark) =>
              Object.freeze({
                ...mark,
                ...(mark.attrs
                  ? { attrs: Object.freeze({ ...mark.attrs }) }
                  : {}),
              }),
            ),
          ),
        }
      : {}),
  }) as T;
}

/** 编码 SMTP 配置资源路径。 */
function senderPath(senderId: Int64String): string {
  return `/system/email/senders/${encodeURIComponent(senderId)}`;
}

/** 编码 Email 模板资源路径。 */
function templatePath(templateId: Int64String): string {
  return `/system/email/templates/${encodeURIComponent(templateId)}`;
}

/** 把统一错误映射为资源页面阶段。 */
function failurePhase(error: AdminError): ResourcePhase {
  if (error.kind === 'permission') return 'forbidden';
  if (error.kind === 'versionConflict') return 'conflict';
  return 'error';
}

/** 保留 API 客户端错误，其余错误使用未知错误。 */
function asAdminError(error: unknown): AdminError {
  return error instanceof AdminError
    ? error
    : new AdminError('unknown', 'Request failed');
}
