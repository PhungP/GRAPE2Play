package com.grape.play;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AudioCoreJUnitTest {
    @Test public void audioCore() {
        java.util.List<String> f = AudioCoreTests.runAll();
        assertTrue("Test thất bại: " + f, f.isEmpty());
    }
}
