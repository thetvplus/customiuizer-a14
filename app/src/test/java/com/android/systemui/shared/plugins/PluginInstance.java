package com.android.systemui.shared.plugins;

import com.android.systemui.plugins.Plugin;

/** Extra overload ensures discovery selects the verified no-argument ABI. */
public final class PluginInstance {
    public static final class PluginFactory {
        public Plugin createPlugin() { return null; }
        public Plugin createPlugin(int unrelated) { return null; }
    }
}
