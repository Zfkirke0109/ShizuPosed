package com.shizuposed.manager.core;

import java.util.Set;

public final class S26BootstrapTestScope {
    public static final String SHELL_BASE = "/data/local/tmp/shizuposed-s26-qa";
    private static final String TARGET = "codepath.apps.demointroandroid";
    private static final String MODULE = "com.hhvvg.anytext";

    private S26BootstrapTestScope() {}

    public static boolean allowsTarget(String packageName) {
        return TARGET.equals(packageName);
    }

    public static boolean allowsModule(String packageName, Set<String> targets,
                                       boolean hookAllApps, boolean hookSystemApps) {
        return MODULE.equals(packageName) && !hookAllApps && !hookSystemApps
            && targets != null && targets.size() == 1 && targets.contains(TARGET);
    }
}
