package net.java.openjdk.cacio.ctc;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public class CTCGraphicsConfiguration extends GraphicsConfiguration {

    // Screen-size parsing is duplicated from FullScreenWindowFactory because
    // this class is loaded into the bootstrap classloader by CacioTestRunner,
    // where cacio-shared classes are not visible.
    private static final Dimension screenSize;
    static {
        String size = System.getProperty("cacio.managed.screensize", "1024x768");
        int x = size.indexOf('x');
        int width = Integer.parseInt(size.substring(0, x));
        int height = Integer.parseInt(size.substring(x + 1));
        screenSize = new Dimension(width, height);
    }

    private final ColorModel model;
    private final Raster raster;
    private CTCGraphicsDevice device;

    CTCGraphicsConfiguration(CTCGraphicsDevice dev) {
        BufferedImage bufferedImage = new BufferedImage(screenSize.width, screenSize.height, BufferedImage.TYPE_INT_ARGB);
        device = dev;
        model = bufferedImage.getColorModel();
        raster = bufferedImage.getRaster().createCompatibleWritableRaster(1, 1);
    }

    @Override
    public GraphicsDevice getDevice() {
        return device;
    }

    @Override
    public ColorModel getColorModel() {
        return ColorModel.getRGBdefault();
    }

    @Override
    public ColorModel getColorModel(int transparency) {
        return ColorModel.getRGBdefault();
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(0, 0, screenSize.width, screenSize.height);
    }

    @Override
    public BufferedImage createCompatibleImage(int width, int height) {
        WritableRaster wr = raster.createCompatibleWritableRaster(width, height);
        return new BufferedImage(model, wr, model.isAlphaPremultiplied(), null);
    }

    @Override
    public AffineTransform getDefaultTransform() {
        return AffineTransform.getScaleInstance(1, 1);
    }

    @Override
    public AffineTransform getNormalizingTransform() {
        return new AffineTransform();
    }

}
