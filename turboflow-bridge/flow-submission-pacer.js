// One shared deadline across all four workers. Draw once per actual request.
export class FlowSubmissionPacer {
  constructor({ now = Date.now, random = Math.random } = {}) {
    this.now = now;
    this.random = random;
    this.nextAllowedAt = 0;
  }

  get remainingMs() {
    return Math.max(0, this.nextAllowedAt - this.now());
  }

  submitted(at = this.now()) {
    this.nextAllowedAt = at + 5000 + Math.min(1, Math.max(0, this.random())) * 5000;
  }
}
