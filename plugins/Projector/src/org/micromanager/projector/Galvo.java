/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.projector;

import ij.gui.Roi;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class Galvo implements ProjectionDevice {

   String galvo_;
   CMMCore mmc_;
   int side_ = 4096;
   ExecutorService galvoExecutor_;
   HashSet<OnStateListener> onStateListeners_ = new HashSet<OnStateListener>();

   public Galvo(CMMCore mmc) {
      mmc_ = mmc;
      galvo_ = mmc_.getGalvoDevice();
      galvoExecutor_ = Executors.newSingleThreadExecutor();
   }

   public String getName() {
       return galvo_;
   }
   
   public void displaySpot(final double x, final double y) {
      turnOn();
      galvoExecutor_.execute(new Runnable() {

         public void run() {
            try {
               mmc_.setGalvoPosition(galvo_, x, y);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
   }

   public void displaySpot(final double x, final double y, final double intervalUs) {
      galvoExecutor_.execute(new Runnable() {
         public void run() {
            try {
               mmc_.pointGalvoAndFire(galvo_, x, y, intervalUs);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      });
   }
   
   public void waitForDevice() {
       Future result = galvoExecutor_.submit(new Runnable() {
           @Override
           public void run() {
               // do nothing;
           }
       });
        try {
            result.get();
        } catch (Exception ex) {
            ReportingUtils.logError(ex);
        }
   }

   public double getWidth() {
      try {
         Double result = galvoExecutor_.submit(new Callable<Double>() {

            public Double call() {
               try {
                  return (double) mmc_.getGalvoXRange(galvo_);
               } catch (Exception ex) {
                  return 0.0;
               }
            }
         }).get();
         if (result == 0) {
            ReportingUtils.logError("Unable to get galvo width");
         }
         return result;
      } catch (Exception ex) {
         ReportingUtils.logError("Unable to get galvo width");
         return 0;
      }
   }

   public double getHeight() {
      try {
         Double result = galvoExecutor_.submit(new Callable<Double>() {

            public Double call() {
               try {
                  return (double) mmc_.getGalvoYRange(galvo_);
               } catch (Exception ex) {
                  return 0.0;
               }
            }
         }).get();
         if (result == 0) {
            ReportingUtils.logError("Unable to get galvo width");
         }
         return result;
      } catch (Exception ex) {
         ReportingUtils.logError("Unable to get galvo width");
         return 0;
      }
   }

   public void turnOn() {
      galvoExecutor_.submit(new Runnable() {
         public void run() {
            try {
               mmc_.setGalvoIlluminationState(galvo_, true);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      for (OnStateListener listener:onStateListeners_) {
        listener.turnedOn();
      }
   }

   public void turnOff() {
      galvoExecutor_.submit(new Runnable() {
         public void run() {
            try {
               mmc_.setGalvoIlluminationState(galvo_, false);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
      for (OnStateListener listener:onStateListeners_) {
        listener.turnedOff();
      }
   }

   public void setRois(final Polygon[] rois) {
      galvoExecutor_.submit(new Runnable() {
         public void run() {
            try {
               mmc_.deleteGalvoPolygons(galvo_);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
            int roiCount = 0;
            try {
               for (Polygon poly : rois) {
                  Point2D lastGalvoPoint = null;
                  for (int i = 0; i < poly.npoints; ++i) {
                     Point2D.Double galvoPoint = new Point2D.Double(poly.xpoints[i], poly.ypoints[i]);
                     if (i == 0) {
                        lastGalvoPoint = galvoPoint;
                     }
                     mmc_.addGalvoPolygonVertex(galvo_, roiCount, galvoPoint.getX(), galvoPoint.getY());
                     if (poly.npoints == 1) {
                        ++roiCount;
                     }
                  }
                  if (poly.npoints > 1) {
                     mmc_.addGalvoPolygonVertex(galvo_, roiCount, lastGalvoPoint.getX(), lastGalvoPoint.getY());
                     ++roiCount;
                  }
               }
            } catch (Exception e) {
               ReportingUtils.showError(e);
            }

            try {
               mmc_.loadGalvoPolygons(galvo_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
   }


   public void runPolygons() {
      galvoExecutor_.submit(new Runnable() {
         public void run() {
            try {
               mmc_.runGalvoPolygons(galvo_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });

   }

   public void addOnStateListener(OnStateListener listener) {
      onStateListeners_.add(listener);
   }

   public void removeOnStateListener(OnStateListener listener) {
      onStateListeners_.remove(listener);
   }

   public void setPolygonRepetitions(final int reps) {
      galvoExecutor_.submit(new Runnable() {
         public void run() {

            try {
               mmc_.setGalvoPolygonRepetitions(galvo_, reps);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
         }
      });
   }

    @Override
    public String getChannel() {
        Future<String> channel = galvoExecutor_.submit(new Callable<String>() {
            public String call() {
                try {
                    return mmc_.getGalvoChannel(galvo_);
                } catch (Exception ex) {
                    ReportingUtils.logError(ex);
                    return null;
                }
            }
        });
        try {
            return channel.get();
        } catch (Exception e) {
            return null;
        }
    }

   @Override
   public void setSpotInterval(long interval_us) {
      try {
         mmc_.setGalvoSpotInterval(galvo_, interval_us);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }
}
