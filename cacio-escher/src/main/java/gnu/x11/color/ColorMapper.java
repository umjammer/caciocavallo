/*
 * Copyright (c) 2019 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package gnu.x11.color;

import gnu.x11.Pixmap.Format;


/**
 * ColorMapper.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2019/04/18 umjammer initial version <br>
 */
public class ColorMapper {

    static ColorMapper instance  = new ColorMapper();

    /**
     * @return
     */
    public static ColorMapper getInstace() {
        return instance;
    }

    /**
     * @param pixel
     * @param format
     * @return
     */
    public int convertToNativePixel(int pixel, Format format) {
        return pixel; // TODO
    }
}

/* */
