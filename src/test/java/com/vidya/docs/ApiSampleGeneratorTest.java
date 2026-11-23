package com.vidya.docs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Writes the API request / response samples and the HTML sample screens. Skipped in normal builds; run it on purpose:
 *
 * <pre>./mvnw -o test -Dtest=ApiSampleGeneratorTest -Dvidya.samples.out=/path/to/APiReqDoc/samples/api</pre>
 *
 * Optional {@code -Dvidya.templates.out} (default {@code src/main/resources/templates/samples}).
 */
@DisplayName("API sample generator")
class ApiSampleGeneratorTest {

    @Test
    @DisplayName("writes Markdown samples for every endpoint and an HTML page per module")
    void generate() throws Exception {
        String out = System.getProperty("vidya.samples.out");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set -Dvidya.samples.out to generate the samples");
        Path html = Path.of(System.getProperty("vidya.templates.out", "src/main/resources/templates/samples"));
        new ApiSampleGenerator(Path.of(out), html).run();
    }
}
