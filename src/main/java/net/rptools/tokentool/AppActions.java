/* The MIT License
 * 
 * Copyright (c) 2005 David Rice, Trevor Croft
 * 
 * Permission is hereby granted, free of charge, to any person 
 * obtaining a copy of this software and associated documentation files 
 * (the "Software"), to deal in the Software without restriction, 
 * including without limitation the rights to use, copy, modify, merge, 
 * publish, distribute, sublicense, and/or sell copies of the Software, 
 * and to permit persons to whom the Software is furnished to do so, 
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be 
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND 
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS 
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN 
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
 * SOFTWARE.
 */

package net.rptools.tokentool;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;

import net.rptools.tokentool.ui.OverlayManagementDialog;
import net.rptools.tokentool.util.RegionSelector;

import com.sun.imageio.plugins.png.PNGMetadata;

public class AppActions {

    public static final Action EXIT_APP = new AbstractAction () {
        
       {
           putValue(Action.NAME, "Exit");
           putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_MASK));
       }
       
       public void actionPerformed(ActionEvent e) {

           System.exit(0);
        }
    };
    
    public static final Action SHOW_ABOUT = new AbstractAction () {
    	
    	{
    		putValue(Action.NAME, "About");
    	}

    	public void actionPerformed(ActionEvent e) {

    		TokenTool.getFrame().showAboutDialog();
    	}
    };
    
    public static final Action SCREEN_CAP = new AbstractAction () {

    	
    	{
    		putValue(Action.NAME, "Screen Capture");
    	}

    	private Rectangle bounds = new Rectangle(100, 100, 600, 400);
    	
    	public void actionPerformed(ActionEvent e) {

    		RegionSelector selector = new RegionSelector();
    		selector.run(bounds);
    		bounds = selector.getBounds();
    		
    		if (bounds.width > 0 && bounds.height > 0) {
    			EventQueue.invokeLater(new Runnable() {
    				public void run() {
    		    		try {
    		    			BufferedImage image = new Robot().createScreenCapture(bounds);
    		    			TokenTool.getFrame().getTokenCompositionPanel().setToken(image);
    		    		} catch (AWTException ae) {
    		    			ae.printStackTrace();
    		    		}
    				}
    			});
    		}
    	}
    };
    
    public static final Action SHOW_OVERLAY_MANAGEMENT_DIALOG = new AbstractAction () {
    	
    	{
    		putValue(Action.NAME, "Manage Overlays");
    	}

    	public void actionPerformed(ActionEvent e) {

    		new OverlayManagementDialog(TokenTool.getFrame()).setVisible(true);
    	}
    };
    
    public static final Action SAVE_TOKEN = new AbstractAction () {
     
        {
            putValue(Action.NAME, "Save Token");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_MASK));
        }
        
        public void actionPerformed(java.awt.event.ActionEvent e) {
            
            new Thread() {
                public void run() {
                    
                    File file = TokenTool.getFrame().showSaveDialog(); 
                    if (file != null) {
                        
                        if (!file.getName().toUpperCase().endsWith(".PNG")) {
                            file = new File(file.getAbsolutePath() + ".png");
                        }
                        if (file.exists()) {
                            if (!TokenTool.confirm("File exists.  Overwrite?")) {
                                return;
                            }
                        }
                        
                        try {
                        	// PW: This code addes the pHYs chunk to the 
                        	// output png file with X & Y dpi set.
                        	BufferedImage img = TokenTool.getFrame().getComposedToken();
		                    ImageWriter writer = getImageWriterBySuffix("png");
		                    writer.setOutput(ImageIO.createImageOutputStream(file));
		                    ImageWriteParam param = writer.getDefaultWriteParam();
		                    PNGMetadata png = new PNGMetadata();
		                    // 39.375 inches per meter
		                    // I'm using the image width for the DPI under 
		                    // the assumption that the token fits within 
		                    // one cell.
		                    int resX = (int)(img.getWidth() * 39.375f);
		                    png.pHYs_pixelsPerUnitXAxis = resX;
		                    png.pHYs_pixelsPerUnitYAxis = resX;
		                    png.pHYs_unitSpecifier = 1; // Meters - alternative is "unknown"
		                    png.pHYs_present = true;
		                    
		                    // TBD save the location and dimensions of the base, so that MapTool
		                    // can scale this token so that the base fits within the grid while leaving
		                    // the rest to perhaps overflow!

		                    /*
		                    
		                    ArrayList<String> pngKeywords = new ArrayList<String>();
		                    ArrayList<Integer> pngValues = new ArrayList<Integer>();

		                    pngKeywords.add("offsetX");
		                    pngValues.add(TokenTool.getFrame().getTokenCompositionPanel().getTokenOffsetX());
		                    pngKeywords.add("offsetY");
		                    pngValues.add(TokenTool.getFrame().getTokenCompositionPanel().getTokenOffsetY());
		                    pngKeywords.add("overlayWidth");
		                    pngValues.add(TokenTool.getFrame().getTokenCompositionPanel().getOverlayWidth());
		                    pngKeywords.add("overlayHeight");
		                    pngValues.add(TokenTool.getFrame().getTokenCompositionPanel().getOverlayHeight());

		                    png.tEXt_keyword
		                    
		                    // png.tEXt_keyword = pngKeywords;
		                    // png.tEXt_text = pngValues;

							*/

		                    writer.write(null, new IIOImage(img, null, png), param);
							
                            //ImageIO.write(TokenTool.getFrame().getComposedToken(), "png", file);
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                            TokenTool.showError("Unable to write image: " + ioe);
                        }
                    }
                }
            }.start ();

        }
    };
    
    // Grabbed this from some code off the Sun Java pages
    public static ImageWriter getImageWriterBySuffix(String suffix)  throws IOException {
        Iterator writers = ImageIO.getImageWritersBySuffix(suffix);
        if (!writers.hasNext())
            throw new IOException("woops, no writers for " + suffix);
        return (ImageWriter) writers.next();
    }

}
