package net.java.openjdk.cacio.ctc;

import java.awt.GraphicsDevice;

import sun.java2d.SunGraphicsEnvironment;

public class CTCGraphicsEnvironment extends SunGraphicsEnvironment {

    private static final CTCGraphicsEnvironment INSTANCE = new CTCGraphicsEnvironment();

    public static CTCGraphicsEnvironment getInstance() {
        return INSTANCE;
    }

    public CTCGraphicsEnvironment() {
    }

    @Override
    protected int getNumScreens() {
        return 1;
    }

    @Override
    protected GraphicsDevice makeScreenDevice(int screennum) {
        return new CTCGraphicsDevice();
    }

    @Override
    public boolean isDisplayLocal() {
        return true;
    }

}
