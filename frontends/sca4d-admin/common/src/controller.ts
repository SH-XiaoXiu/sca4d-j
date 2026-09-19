/** Controller 不可变状态订阅函数。 */
export type StateListener<S> = (state: Readonly<S>) => void;

/** 为纯 TypeScript Controller 提供不可变状态和同步订阅。 */
export abstract class ObservableController<S extends object> {
  /** 返回当前不可变状态。 */
  get state(): Readonly<S> {
    return this.current;
  }
  private current: Readonly<S>;

  private readonly listeners = new Set<StateListener<S>>();

  protected constructor(initial: S) {
    this.current = Object.freeze(initial);
  }

  /** 订阅状态；订阅时立即收到当前值。 */
  subscribe(listener: StateListener<S>): () => void {
    this.listeners.add(listener);
    listener(this.current);
    return () => this.listeners.delete(listener);
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
