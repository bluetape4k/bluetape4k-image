package io.bluetape4k.images.ocr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TiffMultiPageOcrJavaApiTest {
    @Test
    void blockingSurfaceIsCallableWithExplicitArguments() {
        TiffMultiPageOcrLimits limits = new TiffMultiPageOcrLimits(
            1_024L, 1, 1_024L, 1_024L, 1_024, 1_024L, 10_000, 100
        );
        OcrOptions options = new OcrOptions(
            List.of("eng"),
            null,
            TesseractEngineMode.DEFAULT,
            TesseractPageSegmentationMode.AUTO,
            Map.of(),
            List.of(),
            true,
            OcrStructuredDetail.PLAIN_TEXT,
            List.of()
        );
        TiffMultiPageOcr ocr = new TiffMultiPageOcr();
        assertNotNull(ocr);
        assertNotNull(limits);
        try {
            ocr.recognize(new byte[0], options, limits);
        } catch (TiffMultiPageOcrValidationException expected) {
            // The call is intentionally invalid; compiling this invocation is the ABI smoke.
        }
    }
}
