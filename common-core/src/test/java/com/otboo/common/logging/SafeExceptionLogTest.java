package com.otboo.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class SafeExceptionLogTest {
    @Test
    void preservesLocationWithoutSensitiveMessageCauseOrSuppressed() {
        RuntimeException original = new IllegalStateException("SENSITIVE_EXTERNAL_BODY_MARKER",
                new RuntimeException("FAKE_API_KEY_MARKER"));
        original.addSuppressed(new RuntimeException("SENSITIVE_VECTOR_MARKER"));
        RuntimeException safe = SafeExceptionLog.sanitized(original);
        StringWriter output = new StringWriter();
        safe.printStackTrace(new PrintWriter(output));
        assertThat(output.toString()).contains(IllegalStateException.class.getName())
                .doesNotContain("SENSITIVE_EXTERNAL_BODY_MARKER", "FAKE_API_KEY_MARKER", "SENSITIVE_VECTOR_MARKER");
        assertThat(safe.getStackTrace()).containsExactly(original.getStackTrace());
        assertThat(original.getCause()).isNotNull();
        assertThat(original.getSuppressed()).hasSize(1);
    }
}
