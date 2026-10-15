package net.java.openjdk.cacio.ctc;

import sun.awt.image.SunVolatileImage;
import sun.awt.image.SurfaceManager;
import sun.awt.image.VolatileSurfaceManager;

class CTCSurfaceManagerFactory implements SurfaceManager.Factory {

    @Override
    public VolatileSurfaceManager createVolatileManager(SunVolatileImage image,
            Object context) {
        return new CTCVolatileSurfaceManager(image, context);
    }

}
