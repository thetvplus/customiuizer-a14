package tv.withaibuild.customiuizer.mods.notificationautoexpand;

import java.util.HashSet;
import java.util.Set;

/**
 * Test fixtures for the Notification Auto-Expand B1 tests.
 *
 * These classes are structural stand-ins. They do not claim to represent real HyperOS field types,
 * method return types, callback threads, or runtime behavior.
 */
public final class NotificationAutoExpandFixtures {

    private NotificationAutoExpandFixtures() {}

    /** Simulates a `StatusBarNotification` with a package name. */
    public static class StatusBarNotification {
        private final String packageName;
        public boolean expanded = false;

        public StatusBarNotification(String packageName) {
            this.packageName = packageName;
        }

        public String getPackageName() {
            return packageName;
        }
    }

    /** Simulates a notification entry containing `mSbn`. */
    public static class NotificationEntry {
        public final StatusBarNotification mSbn;

        public NotificationEntry(String packageName) {
            this.mSbn = new StatusBarNotification(packageName);
        }
    }

    /** Base class with the primitive boolean `mOnKeyguard` field. */
    public static class BaseExpandableNotificationRow {
        public boolean mOnKeyguard = false;
    }

    /** Exact root class with all required members. */
    public static class ExpandableNotificationRow extends BaseExpandableNotificationRow {
        public final NotificationEntry entry;
        public final Set<String> expandedPackages = new HashSet<>();

        public ExpandableNotificationRow(String packageName) {
            this.entry = new NotificationEntry(packageName);
        }

        public Object getEntry() {
            return entry;
        }

        public void setSystemExpanded(boolean expanded) {
            if (expanded) {
                expandedPackages.add(entry.mSbn.getPackageName());
            } else {
                expandedPackages.remove(entry.mSbn.getPackageName());
            }
        }
    }

    /** Subclass used to test strict subclass mismatch at runtime. */
    public static class SubExpandableNotificationRow extends ExpandableNotificationRow {
        public SubExpandableNotificationRow(String packageName) {
            super(packageName);
        }
    }

    /** Class with a boxed `Boolean` `mOnKeyguard`; resolver must reject. */
    public static class WrapperMOnKeyguardRow {
        public Boolean mOnKeyguard = false;

        public Object getEntry() {
            return new NotificationEntry("com.example");
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class missing `mOnKeyguard`. */
    public static class MissingMOnKeyguardRow {
        public Object getEntry() {
            return new NotificationEntry("com.example");
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class missing `getEntry`. */
    public static class MissingGetEntryRow extends BaseExpandableNotificationRow {
        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class missing `setSystemExpanded`. */
    public static class MissingSetSystemExpandedRow extends BaseExpandableNotificationRow {
        public Object getEntry() {
            return new NotificationEntry("com.example");
        }
    }

    /** Class with both primitive and boxed `setSystemExpanded` overloads. */
    public static class OverloadedSetSystemExpandedRow extends BaseExpandableNotificationRow {
        public final Set<String> expandedPrimitive = new HashSet<>();
        public final Set<String> expandedBoxed = new HashSet<>();
        public final NotificationEntry entry;

        public OverloadedSetSystemExpandedRow(String packageName) {
            this.entry = new NotificationEntry(packageName);
        }

        public Object getEntry() {
            return entry;
        }

        public void setSystemExpanded(boolean expanded) {
            if (expanded) {
                expandedPrimitive.add(entry.mSbn.getPackageName());
            }
        }

        public void setSystemExpanded(Boolean expanded) {
            if (expanded != null && expanded) {
                expandedBoxed.add(entry.mSbn.getPackageName());
            }
        }
    }

    /** Class where `getPackageName` throws, to test `InvocationTargetException` mapping. */
    public static class GetPackageNameThrowsRow extends BaseExpandableNotificationRow {
        public final NotificationEntry entry;

        public GetPackageNameThrowsRow(String packageName) {
            this.entry = new NotificationEntry(packageName);
        }

        public Object getEntry() {
            return entry;
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class where `getEntry()` throws, to test FAST `getEntry` failure boundary. */
    public static class ThrowingGetEntryRow extends BaseExpandableNotificationRow {

        public Object getEntry() {
            throw new RuntimeException("simulated getEntry failure");
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Entry object without the `mSbn` field, to test retained LEGACY helper failure. */
    public static class EntryWithoutMbn {
        public final String packageName;

        public EntryWithoutMbn(String packageName) {
            this.packageName = packageName;
        }
    }

    /** Row whose `getEntry()` returns an object with no `mSbn` field. */
    public static class EntryWithoutMbnRow extends BaseExpandableNotificationRow {
        public final EntryWithoutMbn entry;
        public int getEntryCalls = 0;

        public EntryWithoutMbnRow(String packageName) {
            this.entry = new EntryWithoutMbn(packageName);
        }

        public Object getEntry() {
            getEntryCalls++;
            return entry;
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Notification with a `getPackageName` that throws. */
    public static class ThrowingStatusBarNotification extends StatusBarNotification {
        public ThrowingStatusBarNotification(String packageName) {
            super(packageName);
        }

        @Override
        public String getPackageName() {
            throw new RuntimeException("simulated getPackageName failure");
        }
    }

    /** Entry whose `mSbn` is a `ThrowingStatusBarNotification`. */
    public static class ThrowingPackageNameEntry {
        public final ThrowingStatusBarNotification mSbn;

        public ThrowingPackageNameEntry(String packageName) {
            this.mSbn = new ThrowingStatusBarNotification(packageName);
        }
    }

    /** Row with `getEntry` returning an entry whose `getPackageName` throws. */
    public static class ThrowingPackageNameRow extends BaseExpandableNotificationRow {
        public final ThrowingPackageNameEntry entry;

        public ThrowingPackageNameRow(String packageName) {
            this.entry = new ThrowingPackageNameEntry(packageName);
        }

        public Object getEntry() {
            return entry;
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class where `getEntry` throws `IllegalAccessException` on private method. */
    public static class PrivateGetEntryRow extends BaseExpandableNotificationRow {
        private Object getEntry() {
            return new NotificationEntry("com.example");
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class with both zero-arg and one-arg `getEntry` overloads. */
    public static class OverloadedGetEntryRow extends BaseExpandableNotificationRow {
        public final NotificationEntry entry = new NotificationEntry("com.example");

        public Object getEntry() {
            return entry;
        }

        public Object getEntry(String arg) {
            return new NotificationEntry(arg);
        }

        public void setSystemExpanded(boolean expanded) {}
    }

    /** Class with private primitive boolean fields to test `IllegalAccessException` mapping. */
    public static class PrivateFieldRow {
        private boolean mOnKeyguard = false;
        private final NotificationEntry entry = new NotificationEntry("com.example");

        public Object getEntry() {
            return entry;
        }

        public void setSystemExpanded(boolean expanded) {}

        public void setMOnKeyguard(boolean value) {
            this.mOnKeyguard = value;
        }
    }

    /** Target for mismatched field receiver failure tests. */
    public static class BadFieldTarget {
        public boolean badMOnKeyguard = false;
    }
}
