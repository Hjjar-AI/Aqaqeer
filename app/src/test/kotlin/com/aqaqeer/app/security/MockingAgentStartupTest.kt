package com.aqaqeer.app.security

import java.lang.instrument.Instrumentation
import java.lang.management.ManagementFactory
import org.junit.Assert.*
import org.junit.Test

/** Both flavors must provide mocking instrumentation at JVM startup, without self-attachment. */
class MockingAgentStartupTest {
    @Test fun byteBuddyIsPreloadedAndDynamicAttachmentIsDisabled() {
        val arguments = ManagementFactory.getRuntimeMXBean().inputArguments
        assertTrue("Byte Buddy must be configured as a startup agent", arguments.any { argument ->
            argument.startsWith("-javaagent:") && argument.substringBefore('=').replace('\\', '/')
                .substringAfterLast('/').let { it.startsWith("byte-buddy-agent-") && it.endsWith(".jar") }
        })
        assertTrue("Tests must not fall back to dynamic agent attachment", "-XX:-EnableDynamicAgentLoading" in arguments)
        val installer = Class.forName("net.bytebuddy.agent.Installer", false, ClassLoader.getSystemClassLoader())
        val instrumentation = installer.getMethod("getInstrumentation").invoke(null) as Instrumentation
        assertTrue("Mocking needs class retransformation", instrumentation.isRetransformClassesSupported)
    }
}
