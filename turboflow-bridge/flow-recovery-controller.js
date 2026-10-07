// Round accounting is independent of task reporting and generic failure streaks.
export class FlowRecoveryController {
  constructor({ persist, drained, restart, stop, changed = () => {},
    now = Date.now, sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
    drainTimeoutMs = 35 * 60 * 1000 }) {
    Object.assign(this, { persist, drained, restart, stop, changed, now, sleep, drainTimeoutMs });
    this.state = { phase: 'initializing', roundId: 1, successfulTasks: [], emptyRounds: 0, target: null };
    this.pending = null;
    this.writes = Promise.resolve();
  }
  get blocked() { return this.state.phase !== 'running'; }
  get busy() { return !!this.pending; }
  snapshot() { return { ...this.state, successfulTasks: [...this.state.successfulTasks] }; }
  save() {
    const snapshot = this.snapshot();
    this.writes = this.writes.then(() => this.persist(snapshot));
    // Retain rejection for the recovery flow, without unhandled rejections.
    this.writes.catch(() => {});
    this.changed(snapshot);
    return this.writes;
  }
  async restore(saved, stopped = false) {
    if (saved) {
      this.state = { ...this.state, ...saved,
        successfulTasks: Array.isArray(saved.successfulTasks) ? saved.successfulTasks : [],
        emptyRounds: Math.max(0, Number(saved.emptyRounds) || 0),
        roundId: Math.max(1, Number(saved.roundId) || 1) };
    }
    const interrupted = saved && !['running', 'stopped'].includes(saved.phase);
    this.state.phase = stopped || interrupted || saved?.phase === 'stopped' ? 'stopped' : 'running';
    await this.save();
    if (interrupted) this.stop('Flow recovery was interrupted; click Run Now', 'RECOVERY_INTERRUPTED');
  }
  success(roundId, taskId) {
    if (roundId !== this.state.roundId || this.state.successfulTasks.includes(taskId)) return;
    this.state.successfulTasks.push(taskId);
    this.save();
  }
  halt() {
    this.state.phase = 'stopped';
    this.save();
  }
  request(target, { manual = false } = {}) {
    if (this.pending) return this.pending;
    if (!manual && this.blocked) return Promise.resolve(false);
    if (manual) this.state.emptyRounds = 0;
    if (target) this.state.target = target;
    this.state.phase = 'draining'; // synchronous gate before any await
    const persisted = this.save();
    this.pending = this.run(persisted, manual).finally(() => { this.pending = null; });
    return this.pending;
  }
  async run(persisted, manual) {
    try {
      await persisted;
      const deadline = this.now() + this.drainTimeoutMs;
      while (!this.drained()) {
        if (this.state.phase === 'stopped') return false;
        if (this.now() >= deadline) throw new Error('Timed out waiting for Flow tasks to finish; tabs retained');
        await this.sleep(100);
      }
      if (this.state.phase === 'stopped') return false;
      if (!manual) {
        this.state.emptyRounds = this.state.successfulTasks.length ? 0 : this.state.emptyRounds + 1;
        await this.save();
        if (this.state.emptyRounds >= 2) {
          this.halt();
          this.stop('连续两轮没有成功译图，请点击 Run Now 手动恢复', 'FLOW_TWO_EMPTY_ROUNDS');
          return false;
        }
      }
      const changePhase = async phase => {
        if (this.state.phase === 'stopped') throw new Error('Recovery stopped');
        this.state.phase = phase;
        await this.save();
      };
      await this.restart(this.state.target, changePhase);
      if (this.state.phase === 'stopped') return false;
      this.state.roundId++;
      this.state.successfulTasks = [];
      this.state.phase = 'running';
      await this.save();
      return true;
    } catch (error) {
      // A persistence failure must not permit destructive recovery actions.
      this.state.phase = 'stopped';
      this.changed(this.snapshot());
      this.stop(`Flow recovery failed: ${error.message}`, 'RECOVERY_FAILED');
      return false;
    }
  }
}
