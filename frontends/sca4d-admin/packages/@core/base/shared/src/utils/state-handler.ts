/** 协调同一组件就绪周期的全部等待者，结束周期时拒绝旧等待。 */
export class StateHandler {
  private condition: boolean = false;
  private rejectCondition: ((reason?: Error) => void) | null = null;
  private resolveCondition: (() => void) | null = null;
  private pending: Promise<void> | null = null;

  /** 返回组件是否已经就绪。 */
  isConditionTrue(): boolean {
    return this.condition;
  }

  /** 结束当前等待周期；尚未完成的等待全部失败，新调用等待下一次就绪。 */
  reset() {
    this.setConditionFalse();
  }

  /** 标记未就绪，并拒绝当前周期的全部等待者。 */
  setConditionFalse() {
    this.condition = false;
    if (this.rejectCondition) {
      this.rejectCondition(new Error('Condition was set to false'));
      this.clearPromises();
    }
  }

  /** 标记已就绪，并完成当前周期的全部等待者。 */
  setConditionTrue() {
    this.condition = true;
    if (this.resolveCondition) {
      this.resolveCondition();
      this.clearPromises();
    }
  }

  /** 同一未就绪周期返回同一个 Promise，已经就绪时立即完成。 */
  waitForCondition(): Promise<void> {
    if (this.condition) return Promise.resolve();
    this.pending ??= new Promise((resolve, reject) => {
      this.resolveCondition = resolve;
      this.rejectCondition = reject;
    });
    return this.pending;
  }

  /** 释放已经结束周期的 Promise 和回调。 */
  private clearPromises() {
    this.resolveCondition = null;
    this.rejectCondition = null;
    this.pending = null;
  }
}
