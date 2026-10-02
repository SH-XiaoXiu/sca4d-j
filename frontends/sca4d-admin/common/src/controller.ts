/** Controller 不可变状态订阅函数。 */
export type StateListener<S> = (state: Readonly<S>) => void;

/** 为纯 TypeScript Controller 提供不可变状态和同步订阅。 */
export abstract class ObservableController<S extends object> {
  /** 返回当前不可变状态。 */
  get state(): Readonly<S> {
    return this.current;
  }
  private current: Readonly<S>;
  private readonly initial: Readonly<S>;
  private readonly listeners = new Set<StateListener<S>>();
  private readonly reads = new Map<string, number>();

  private readSequence = 0;

  protected constructor(initial: S) {
    this.current = Object.freeze(initial);
    this.initial = this.current;
  }

  /** 关闭资源视图后停止接收所有读取结果。 */
  cancelReads(): void {
    this.reads.clear();
  }

  /** 清除 Session 的资源快照并使所有在途读取失效。 */
  resetState(): void {
    this.reads.clear();
    this.publish(this.initial);
  }

  /** 订阅状态；订阅时立即收到当前值。 */
  subscribe(listener: StateListener<S>): () => void {
    this.listeners.add(listener);
    listener(this.current);
    return () => {
      this.listeners.delete(listener);
      if (this.listeners.size === 0) this.cancelReads();
    };
  }

  /** 使当前作用域更早的读取失效，作用域使用稳定操作名称。 */
  protected beginRead(scope: string): number {
    const sequence = ++this.readSequence;
    this.reads.set(scope, sequence);
    return sequence;
  }

  /** 关闭独立详情时只取消该详情读取，不影响列表刷新。 */
  protected cancelRead(scope: string): void {
    this.reads.delete(scope);
  }

  /** 只有最近一次读取仍可发布结果；reset 后旧请求也失效。 */
  protected isCurrentRead(scope: string, sequence: number): boolean {
    return this.reads.get(scope) === sequence;
  }

  /** 发布新的不可变状态。 */
  protected publish(next: S): void {
    this.current = Object.freeze(next);
    this.listeners.forEach((listener) => listener(this.current));
  }
}

/** 资源页面统一状态。 */
export type ResourcePhase =
  | 'conflict'
  | 'empty'
  | 'error'
  | 'forbidden'
  | 'idle'
  | 'loading'
  | 'ready';
