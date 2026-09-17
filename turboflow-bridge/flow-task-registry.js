export class FlowTaskRegistry {
  constructor(limit = 4) {
    this.limit = Math.max(1, Number(limit) || 1);
    this.submissionOwner = null;
    this.generatingOwners = new Set();
  }

  get inUse() {
    return this.generatingOwners.size + (this.submissionOwner ? 1 : 0);
  }

  get hasCapacity() {
    return this.inUse < this.limit;
  }

  reserveSubmission(assignmentId) {
    if (!assignmentId || this.submissionOwner || !this.hasCapacity
        || this.generatingOwners.has(assignmentId)) return false;
    this.submissionOwner = assignmentId;
    return true;
  }

  acceptSubmission(assignmentId) {
    if (!assignmentId || this.submissionOwner !== assignmentId) return false;
    this.submissionOwner = null;
    this.generatingOwners.add(assignmentId);
    return true;
  }

  release(assignmentId) {
    if (!assignmentId) return false;
    let released = false;
    if (this.submissionOwner === assignmentId) {
      this.submissionOwner = null;
      released = true;
    }
    if (this.generatingOwners.delete(assignmentId)) released = true;
    return released;
  }

  snapshot() {
    return {
      limit: this.limit,
      inUse: this.inUse,
      submissionOwner: this.submissionOwner,
      generatingOwners: Array.from(this.generatingOwners),
    };
  }
}
