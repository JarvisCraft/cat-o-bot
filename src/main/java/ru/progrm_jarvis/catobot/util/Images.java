package ru.progrm_jarvis.catobot.util;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.io.IOUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Utility for image-related operations.
 */
@UtilityClass
public class Images {

    /**
     * Gets a {@link byte} array which may be used a stub where bytes of an image are required.
     *
     * @return {@link byte}s which may be used as a stub for an image
     */
    public byte[] getStubBytes() {
        return StubHolder.STUB_BYTES;
    }

    /**
     * Gets an image stub which may be used whenever no image is available.
     *
     * @return image which may be used as a stub whenever an image is needed but is unavailable
     */
    public BufferedImage getStub() {
        return StubHolder.STUB;
    }

    /**
     * Holder of an image stub used for its lazy initialization.
     */
    private static final class StubHolder {

        /**
         * Bytes of a stub image
         */
        private static final byte[] STUB_BYTES;

        /**
         * Stub image.
         */
        private static final BufferedImage STUB;

        static {
            try {
                val url = Images.class.getResource("/fallback_cat.jpg");
                STUB_BYTES = IOUtils.toByteArray(url);
                STUB = ImageIO.read(url);
            } catch (final IOException e) {
                throw new IllegalStateException("Unable to create image stub", e);
            }
        }
    }
}
