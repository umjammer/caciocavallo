package net.java.openjdk.awt.peer.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

class LibraryLoader {

    private static final Logger logger = System.getLogger(LibraryLoader.class.getName());

    private static boolean loaded;

    static synchronized void loadLibs() {
	if (! loaded) {
	    String libName = System.mapLibraryName("cacioweb");
	    System.err.println("loading library: /" + libName);
	    InputStream in = LibraryLoader.class.getResourceAsStream("/" + libName);
	    File outFile = new File(System.getProperty("java.io.tmpdir")  + File.separator + libName);
	    try {
              Files.copy(in, FileSystems.getDefault().getPath(outFile.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
	    } catch (IOException ex) {
            logger.log(Level.ERROR, ex.getMessage(), ex);
	        System.exit(-1);
	    }
	    System.load(outFile.getAbsolutePath());
	    loaded = true;
	}
    }
}
