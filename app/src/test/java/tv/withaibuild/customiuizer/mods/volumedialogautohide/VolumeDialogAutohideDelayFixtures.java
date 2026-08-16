package tv.withaibuild.customiuizer.mods.volumedialogautohide;

/**
 * Test fixtures for the VolumeDialogAutohideDelay B1 tests.
 *
 * These classes are structural stand-ins. They do not claim to represent real
 * HyperOS field types, method return types, callback threads, or runtime
 * behavior.
 */
public final class VolumeDialogAutohideDelayFixtures {

    private VolumeDialogAutohideDelayFixtures() {}

    /** Base class with the two primitive boolean fields declared in a superclass. */
    public static class BaseMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;
        public Boolean mIsSafetyShowing = false;
        public Boolean mSafetyWarning = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Exact root class with primitive boolean fields. */
    public static class MiuiVolumeDialogImpl extends BaseMiuiVolumeDialogImpl {
        @Override
        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Subclass used to test strict subclass mismatch at runtime. */
    public static class SubMiuiVolumeDialogImpl extends MiuiVolumeDialogImpl {
    }

    /** Class with wrapper Boolean fields; resolver must reject these. */
    public static class WrapperBooleanMiuiVolumeDialogImpl {
        public Boolean mHovering = false;
        public Boolean mExpanded = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class with wrapper mHovering and primitive mExpanded. */
    public static class WrapperHoveringMiuiVolumeDialogImpl {
        public Boolean mHovering = false;
        public boolean mExpanded = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class with primitive mHovering and wrapper mExpanded. */
    public static class WrapperExpandedMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public Boolean mExpanded = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class missing mHovering. */
    public static class MissingHoveringMiuiVolumeDialogImpl {
        public boolean mExpanded = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class missing mExpanded. */
    public static class MissingExpandedMiuiVolumeDialogImpl {
        public boolean mHovering = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class missing computeTimeoutH entirely. */
    public static class MissingMethodMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;
    }

    /** Class with a non-Int return type for computeTimeoutH. */
    public static class StringReturnMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;

        public String computeTimeoutH() {
            return "5000";
        }
    }

    /** Class where mExpanded is missing so the safety-true branch cannot read it. */
    public static class SafetyNoExpandedMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public Boolean mIsSafetyShowing = false;
        public Boolean mSafetyWarning = false;

        public int computeTimeoutH() {
            return 5000;
        }

        // mExpanded intentionally absent.
    }

    /** Target for mismatched field receiver failure tests. */
    public static class BadFieldTarget {
        public boolean badHovering = false;
        public boolean badExpanded = false;
    }

    /** Class with private primitive boolean fields to test IllegalAccessException mapping. */
    public static class PrivateFieldMiuiVolumeDialogImpl {
        private boolean mHovering = false;
        private boolean mExpanded = false;
        private Boolean mIsSafetyShowing = false;
        private Boolean mSafetyWarning = false;

        public int computeTimeoutH() {
            return 5000;
        }

        public void setHovering(boolean value) { this.mHovering = value; }
        public void setExpanded(boolean value) { this.mExpanded = value; }
        public void setSafetyShowing(Boolean value) { this.mIsSafetyShowing = value; }
        public void setSafetyWarning(Boolean value) { this.mSafetyWarning = value; }
    }

    /** Class with the safety alias fields present. */
    public static class SafetyAliasMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;
        public Boolean mIsSafetyShowing = false;
        public Boolean mSafetyWarning = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class with only the fallback safety field. */
    public static class SafetyFallbackOnlyMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;
        public Boolean mSafetyWarning = false;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class with the primary safety field throwing on cast. */
    public static class PrimarySafetyNullMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;
        public Boolean mIsSafetyShowing = null;
        public Boolean mSafetyWarning = true;

        public int computeTimeoutH() {
            return 5000;
        }
    }

    /** Class with the primary safety field missing and fallback present. */
    public static class PrimaryMissingFallbackPresentMiuiVolumeDialogImpl {
        public boolean mHovering = false;
        public boolean mExpanded = false;
        public Boolean mSafetyWarning = true;

        public int computeTimeoutH() {
            return 5000;
        }

        // mIsSafetyShowing intentionally absent.
    }
}
