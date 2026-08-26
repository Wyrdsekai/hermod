package org.wyrdsekai.hermod;

/**
 * What actually runs an admitted task on this device. hermod defines the seam;
 * an embedding application binds it to whatever does the work.
 */
public interface TaskExecutor {

    /**
     * The outcome of running a task.
     *
     * @param envelopeId the envelope this answers
     * @param ok         whether it ran successfully
     * @param output     the result, when it ran
     * @param error      why it did not, when it failed
     */
    record TaskResult(String envelopeId, boolean ok, String output, String error) {
        /**
         * The task ran.
         *
         * @param id  the envelope id
         * @param out the result
         * @return a successful result
         */
        public static TaskResult ok(String id, String out) { return new TaskResult(id, true, out, ""); }
        /**
         * The task did not run.
         *
         * @param id  the envelope id
         * @param err what went wrong
         * @return a failed result
         */
        public static TaskResult fail(String id, String err) { return new TaskResult(id, false, "", err); }
    }

    /** True if this executor can run the given task type. */
    /**
     * Whether this executor knows how to run a kind of task.
     *
     * @param taskType the task type from an envelope
     * @return true if this executor can run it
     */
    boolean handles(String taskType);

    /**
     * Run an admitted task.
     *
     * @param envelope the task, already admitted by the gate
     * @return the result, successful or not
     */
    TaskResult execute(TaskEnvelope envelope);
}
