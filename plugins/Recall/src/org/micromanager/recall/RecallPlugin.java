/**
 * Micro-Manager "Live Replay"
 * 
 * This plugin will copy all images in the Micro-Manager circular buffer
 * into a viewer window.  The counter of the circular buffer is set to 0 (i.e.
 * images in the circular buffer will be destroyed.
 * 
 * This is useful when you see something interesting in live mode and want to 
 * save this data.
 * 
 * No attempt is made to interpret the data coming from the circular buffer.
 * Images and tags currently are not processed by ImageProcessors (this would
 * be a nice addition)
 * 
 * Nico Stuurman, 2009(?)
 * 
 * copyright University of California
 * 
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.recall;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;



public class RecallPlugin implements MMPlugin {
   public static String menuName = "Live Replay";
   public static String tooltipDescription = "Recalls live images remaining in internal"
		   +" buffer.  The size of Micromanager's internal buffer can be changed" +
		   "in options (under Tools menu)";
   private CMMCore core_;
   private MMStudioMainFrame gui_;

   public void setApp(ScriptInterface app) {
      gui_ = (MMStudioMainFrame) app;                                        
      core_ = app.getMMCore();                                               
   }

   public void dispose() {
      // nothing todo:
   }

   public void show() {
      try {
         String ig = "Live Replay";
         if (gui_.acquisitionExists(ig))
            gui_.closeAcquisition(ig);

         int remaining = core_.getRemainingImageCount();

         if (remaining < 1) {
            ReportingUtils.showMessage("There are no Images in the Micro-Manage buffer");
            return;
         }

         gui_.openAcquisition(ig, "tmp", core_.getRemainingImageCount(), 1, 1, true);

         long width = core_.getImageWidth();
         long height = core_.getImageHeight();
         long depth = core_.getBytesPerPixel();


         gui_.initializeAcquisition(ig, (int) width,(int) height, (int) depth);

         try {
            String binning = core_.getProperty(core_.getCameraDevice(), "Binning");
            int bin = Integer.parseInt(binning);
            for (int i=0; i < remaining; i++) {
               TaggedImage tImg = core_.popNextTaggedImage();

               tImg.tags.put("Time", MDUtils.getCurrentTime());
               tImg.tags.put("Frame", i);
               tImg.tags.put("ChannelIndex", 0);
               tImg.tags.put("Slice", 0);
               tImg.tags.put("PositionIndex", 0);
               tImg.tags.put("Width", width);
               tImg.tags.put("Height", height);
               MDUtils.setBinning(tImg.tags, bin);

               if (depth == 1)
                  tImg.tags.put("PixelType", "GRAY8");
               else if (depth == 2)
                  tImg.tags.put("PixelType", "GRAY16");
               gui_.addImage(ig, tImg);
               if (i == 0) {
                  gui_.setContrastBasedOnFrame(ig, 0, 0);
               }
            }
         } catch (Exception ex){
         }
      } catch (MMScriptException e) {
         ReportingUtils.showError(e);
      }

   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "Recalls live images remaining in internal buffer.  Set size of the buffer in options (under Tools menu)";
   }

   public String getDescription() {
      return tooltipDescription;
   }
   
   public String getVersion() {
      return "First version";
   }
   
   public String getCopyright() {
      return "University of California, 2010";
   }
}
