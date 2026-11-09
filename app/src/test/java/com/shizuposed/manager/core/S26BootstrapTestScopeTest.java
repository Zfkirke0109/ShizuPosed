package com.shizuposed.manager.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public class S26BootstrapTestScopeTest {
    private static final String ANYTEXT = "com.hhvvg.anytext";
    private static final String DEMO = "codepath.apps.demointroandroid";

    @Test
    public void onlyTheDemoIsAnAllowedTarget() {
        assertTrue(S26BootstrapTestScope.allowsTarget(DEMO));
        assertFalse(S26BootstrapTestScope.allowsTarget(ANYTEXT));
        assertFalse(S26BootstrapTestScope.allowsTarget(null));
    }

    @Test
    public void anyTextMustBeExplicitlyScopedToOnlyTheDemo() {
        assertTrue(S26BootstrapTestScope.allowsModule(ANYTEXT, Set.of(DEMO), false, false));
        assertFalse(S26BootstrapTestScope.allowsModule("example.module", Set.of(DEMO), false, false));
        assertFalse(S26BootstrapTestScope.allowsModule(ANYTEXT, null, false, false));
        assertFalse(S26BootstrapTestScope.allowsModule(ANYTEXT, Set.of(), false, false));
        assertFalse(S26BootstrapTestScope.allowsModule(ANYTEXT, Set.of(DEMO, "example.other"), false, false));
        assertFalse(S26BootstrapTestScope.allowsModule(ANYTEXT, Set.of(DEMO), true, false));
        assertFalse(S26BootstrapTestScope.allowsModule(ANYTEXT, Set.of(DEMO), false, true));
    }
}
