package io.bluetape4k.images.ocr;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThrows(
            TiffMultiPageOcrValidationException.class,
            () -> ocr.recognize(new byte[0], options, limits)
        );

        TiffMultiPageOcrValidationException legacyValidation = new TiffMultiPageOcrValidationException(
            TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN,
            null,
            "legacy"
        );
        assertNull(legacyValidation.getCause());

        IllegalStateException cause = new IllegalStateException("metadata");
        TiffMultiPageOcrException mapped = new TiffMultiPageOcrException(
            TiffMultiPageOcrFailureReason.DECODE_FAILED,
            0,
            "decode",
            cause
        );
        assertSame(cause, mapped.getCause());
    }
}
