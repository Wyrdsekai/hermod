package org.wyrdsekai.hermod;

/**
 * Owned by the EXECUTING device — the serialization point of the whole
 * mesh. A routing decision is only ever a proposal; this door decides.
 * Refusal is a normal outcome and must be cheap.
 */
public interface AdmissionGate {

    /** What a device decided to do with an offered task. */
    enum Verdict {
        /** Take it and run it now. */
        ADMIT,
        /** Accept in principle but not yet; the caller should try elsewhere. */
        QUEUE,
        /** Decline. Normal traffic, not an error. */
        REFUSE
    }

    /**
     * A verdict and why.
     *
     * @param verdict what the device decided
     * @param reason  a short explanation, shown to the origin when declined
     */
    record Decision(Verdict verdict, String reason) {
        /**
         * Take the task.
         *
         * @return a decision to run it
         */
        public static Decision admit() { return new Decision(Verdict.ADMIT, ""); }
        /**
         * Not now.
         *
         * @param why why it cannot run now
         * @return a decision to defer
         */
        public static Decision queue(String why) { return new Decision(Verdict.QUEUE, why); }
        /**
         * No.
         *
         * @param why why the task was declined
         * @return a decision to refuse
         */
        public static Decision refuse(String why) { return new Decision(Verdict.REFUSE, why); }
    }

    /**
     * Must verify: envelope signature, expiry, token budget vs local
     * policy, and — for any dataDomain != "none" — the SignedGrant
     * against the authority key. Grant verification happens HERE, at the data,
     * never at the router.
     *
     * @param envelope the offered task
     * @return whether to run it, and why not if not
     */
    Decision consider(TaskEnvelope envelope);
}
