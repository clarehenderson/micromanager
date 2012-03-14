package spim;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

public class SPIMAcquisition implements MMPlugin {
	// TODO: read these from the properties
	protected int motorMin = 1, motorMax = 8000,
		twisterMin = -180, twisterMax = 180;

	protected ScriptInterface app;
	protected CMMCore mmc;
	protected MMStudioMainFrame gui;

	protected String xyStageLabel, zStageLabel, twisterLabel,
		laserLabel, cameraLabel;

	protected JFrame frame;
	protected IntegerField xPosition, yPosition, zPosition, rotation,
		zFrom, zTo, stepsPerRotation, degreesPerStep,
		laserPower, exposure;
	protected MotorSlider xSlider, ySlider, zSlider, rotationSlider,
		laserSlider, exposureSlider;
	protected JCheckBox liveCheckbox, registrationCheckbox,
		multipleAngleCheckbox, continuousCheckbox;
	protected JButton ohSnap;

	protected boolean updateLiveImage;

	// MMPlugin stuff

	/**
	 *  The menu name is stored in a static string, so Micro-Manager
	 *  can obtain it without instantiating the plugin
	 */
	public static String menuName = "Acquire SPIM image";
	
	/**
	 * The main app calls this method to remove the module window
	 */
	@Override
	public void dispose() {
		if (frame == null)
			return;
		frame.dispose();
		frame = null;
		runToX.interrupt();
		runToY.interrupt();
		runToZ.interrupt();
		runToAngle.interrupt();
	}
   
	/**
	 * The main app passes its ScriptInterface to the module. This
	 * method is typically called after the module is instantiated.
	 * @param app - ScriptInterface implementation
	 */
	@Override
	public void setApp(ScriptInterface app) {
		this.app = app;
		mmc = app.getMMCore();
		gui = MMStudioMainFrame.getInstance();
	}
   
	/**
	 * Open the module window
	 */
	@Override
	public void show() {
		initUI();
		configurationChanged();
		frame.setVisible(true);
	}
   
	/**
	 * The main app calls this method when hardware settings change.
	 * This call signals to the module that it needs to update whatever
	 * information it needs from the MMCore.
	 */
	@Override
	public void configurationChanged() {
		zStageLabel = null;
		xyStageLabel = null;
		twisterLabel = null;

		for (String label : mmc.getLoadedDevices().toArray()) try {
			String driver = mmc.getDeviceNameInLibrary(label);
			if (driver.equals("Picard Twister"))
				twisterLabel = label;
			else if (driver.equals("Picard Z Stage")) { // TODO: read this from the to-be-added property
				zStageLabel = label;
			}
			else if (driver.equals("Picard XY Stage"))
				xyStageLabel = label;
			// testing
			else if (driver.equals("DStage")) {
				if (label.equals("DStage"))
					zStageLabel = label;
				else
					twisterLabel = label;
			}
			else if (driver.equals("DXYStage"))
				xyStageLabel = label;
		} catch (Exception e) {
			IJ.handleException(e);
		}
		cameraLabel = mmc.getCameraDevice();

		updateUI();
	}
   
	/**
	 * Returns a very short (few words) description of the module.
	 */
	@Override
	public String getDescription() {
		return "Open Source SPIM acquisition";
	}
   
	/**
	 * Returns verbose information about the module.
	 * This may even include a short help instructions.
	 */
	@Override
	public String getInfo() {
		// TODO: be more verbose
		return "See https://wiki.mpi-cbg.de/wiki/spiminabriefcase/";
	}
   
	/**
	 * Returns version string for the module.
	 * There is no specific required format for the version
	 */
	@Override
	public String getVersion() {
		return "0.01";
	}
   
	/**
	 * Returns copyright information
	 */
	@Override
	public String getCopyright() {
		return "Copyright Johannes Schindelin (2011)\n"
			+ "GPLv2 or later";
	}

	// UI stuff

	protected void initUI() {
		if (frame != null)
			return;
		frame = new JFrame("SPIM in a Briefcase");

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.PAGE_AXIS));
		left.setBorder(BorderFactory.createTitledBorder("Position/Angle"));

		xSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				runToX.run(value);
				maybeUpdateImage();
			}
		};
		ySlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				runToY.run(value);
				maybeUpdateImage();
			}
		};
		zSlider = new MotorSlider(motorMin, motorMax, 1) {
			@Override
			public void valueChanged(int value) {
				runToZ.run(value);
				maybeUpdateImage();
			}
		};
		rotationSlider = new MotorSlider(twisterMin, twisterMax, 0) {
			@Override
			public void valueChanged(int value) {
				runToAngle.run(value * 200 / 360);
				maybeUpdateImage();
			}
		};

		xPosition = new IntegerSliderField(xSlider);
		yPosition = new IntegerSliderField(ySlider);
		zPosition = new IntegerSliderField(zSlider);
		rotation = new IntegerSliderField(rotationSlider);

		zFrom = new IntegerField(1) {
			@Override
			public void valueChanged(int value) {
				if (value < motorMin)
					setText("" + motorMin);
				else if (value > motorMax)
					setText("" + motorMax);
			}
		};
		zTo = new IntegerField(motorMax) {
			@Override
			public void valueChanged(int value) {
				if (value < motorMin)
					setText("" + motorMin);
				else if (value > motorMax)
					setText("" + motorMax);
			}
		};
		stepsPerRotation = new IntegerField(4) {
			@Override
			public void valueChanged(int value) {
				degreesPerStep.setText("" + (360 / value));
			}
		};
		degreesPerStep = new IntegerField(90) {
			@Override
			public void valueChanged(int value) {
				stepsPerRotation.setText("" + (360 / value));
			}
		};

		addLine(left, Justification.LEFT, "x:", xPosition, "y:", yPosition, "z:", zPosition, "angle:", rotation);
		addLine(left, Justification.STRETCH, "x:", xSlider);
		addLine(left, Justification.RIGHT, new LimitedRangeCheckbox("Limit range", xSlider, 500, 2500));
		addLine(left, Justification.STRETCH, "y:", ySlider);
		addLine(left, Justification.RIGHT, new LimitedRangeCheckbox("Limit range", ySlider, 500, 2500));
		addLine(left, Justification.STRETCH, "z:", zSlider);
		addLine(left, Justification.RIGHT, new LimitedRangeCheckbox("Limit range", zSlider, 500, 2500));
		addLine(left, Justification.RIGHT, "from z:", zFrom, "to z:", zTo);
		addLine(left, Justification.STRETCH, "rotation:", rotationSlider);
		addLine(left, Justification.RIGHT, "steps/rotation:", stepsPerRotation, "degrees/step:", degreesPerStep);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.PAGE_AXIS));
		right.setBorder(BorderFactory.createTitledBorder("Acquisition"));

		// TODO: find out correct values
		laserSlider = new MotorSlider(0, 1000, 1000) {
			@Override
			public void valueChanged(int value) {
				// TODO
			}
		};
		// TODO: find out correct values
		exposureSlider = new MotorSlider(10, 1000, 10) {
			@Override
			public void valueChanged(int value) {
				try {
					mmc.setExposure(value);
				} catch (Exception e) {
					IJ.handleException(e);
				}
			}
		};

		laserPower = new IntegerSliderField(laserSlider);
		exposure = new IntegerSliderField(exposureSlider);

		liveCheckbox = new JCheckBox("Update Live View");
		liveCheckbox.setSelected(true);
		updateLiveImage = true;
		liveCheckbox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				updateLiveImage = e.getStateChange() == ItemEvent.SELECTED;
			}
		});
		registrationCheckbox = new JCheckBox("Perform SPIM registration");
		registrationCheckbox.setSelected(false);
		registrationCheckbox.setEnabled(false);
		multipleAngleCheckbox = new JCheckBox("Multiple Rotation Angles");
		multipleAngleCheckbox.setSelected(false);
		multipleAngleCheckbox.setEnabled(false);

		continuousCheckbox = new JCheckBox("Continuous z motion");
		continuousCheckbox.setEnabled(true);

		ohSnap = new JButton("Oh snap!");
		ohSnap.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final int zStart = zFrom.getValue();
				final int zEnd = zTo.getValue();
				final boolean isContinuous = continuousCheckbox.isSelected();
				new Thread() {
					@Override
					public void run() {
						try {
							ImagePlus image = isContinuous ?
								snapContinuousStack(zStart, zEnd) : snapStack(zStart, zEnd);
							image.show();
						} catch (Exception e) {
							IJ.handleException(e);
						}
					}
				}.start();
			}
		});

		addLine(right, Justification.RIGHT, "laser power:", laserPower, "exposure:", exposure);
		addLine(right, Justification.STRETCH, "laser:", laserSlider);
		addLine(right, Justification.STRETCH, "exposure:", exposureSlider);
		addLine(right, Justification.RIGHT, liveCheckbox);
		addLine(right, Justification.RIGHT, registrationCheckbox);
		addLine(right, Justification.RIGHT, multipleAngleCheckbox);
		addLine(right, Justification.RIGHT, continuousCheckbox);
		addLine(right, Justification.RIGHT, ohSnap);

		Container panel = frame.getContentPane();
		panel.setLayout(new GridLayout(1, 2));
		panel.add(left);
		panel.add(right);

		frame.pack();
	}

	protected void updateUI() {
		xPosition.setEnabled(xyStageLabel != null);
		yPosition.setEnabled(xyStageLabel != null);
		zPosition.setEnabled(zStageLabel != null);
		rotation.setEnabled(twisterLabel != null);

		xSlider.setEnabled(xyStageLabel != null);
		ySlider.setEnabled(xyStageLabel != null);
		zSlider.setEnabled(zStageLabel != null);
		zFrom.setEnabled(zStageLabel != null);
		zTo.setEnabled(zStageLabel != null);
		rotationSlider.setEnabled(twisterLabel != null);
		stepsPerRotation.setEnabled(twisterLabel != null);
		degreesPerStep.setEnabled(twisterLabel != null);

		laserPower.setEnabled(laserLabel != null);
		exposure.setEnabled(cameraLabel != null);
		laserSlider.setEnabled(laserLabel != null);
		exposureSlider.setEnabled(cameraLabel != null);
		liveCheckbox.setEnabled(cameraLabel != null);
		ohSnap.setEnabled(zStageLabel != null && cameraLabel != null);

		if (xyStageLabel != null) try {
			int x = (int)mmc.getXPosition(xyStageLabel);
			int y = (int)mmc.getYPosition(xyStageLabel);
			xPosition.setText("" + x);
			yPosition.setText("" + y);
			xSlider.setValue(x);
			ySlider.setValue(y);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (zStageLabel != null) try {
			int z = (int)mmc.getPosition(zStageLabel);
			zPosition.setText("" + z);
			zSlider.setValue(z);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (twisterLabel != null) try {
			// TODO: how to handle 200 steps per 360 degrees?
			int angle = (int)mmc.getPosition(twisterLabel);
			rotation.setText("" + angle);
			rotationSlider.setValue(angle);
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (laserLabel != null) try {
			// TODO: get current laser power
		} catch (Exception e) {
			IJ.handleException(e);
		}
		if (cameraLabel != null) try {
			// TODO: get current exposure
		} catch (Exception e) {
			IJ.handleException(e);
		}
	}

	// UI helpers

	protected enum Justification {
		LEFT, STRETCH, RIGHT
	};

	protected static void addLine(Container container, Justification justification, Object... objects) {
		JPanel panel = new JPanel();
		if (justification == Justification.STRETCH)
			panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		else
			panel.setLayout(new FlowLayout(justification == Justification.LEFT ? FlowLayout.LEADING : FlowLayout.TRAILING));
		for (Object object : objects) {
			Component component = (object instanceof String) ?
				new JLabel((String)object) :
				(Component)object;
			panel.add(component);
		}
		container.add(panel);
	}

	protected static abstract class MotorSlider extends JSlider implements ChangeListener {
		protected JTextField updating;
		protected Color background;

		public MotorSlider(int min, int max, int current) {
			super(JSlider.HORIZONTAL, min, max, Math.min(max, Math.max(min, current)));

			setMinorTickSpacing((int)((max - min) / 40));
			setMajorTickSpacing((int)((max - min) / 5));
			setPaintTrack(true);
			setPaintTicks(true);

			if (min == 1)
				setLabelTable(makeLabelTable(min, max, 5));
			setPaintLabels(true);

			addChangeListener(this);
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			final int value = getValue();
			if (getValueIsAdjusting()) {
				if (updating != null) {
					if (background == null)
						background = updating.getBackground();
					updating.setBackground(Color.YELLOW);
					updating.setText("" + value);
				}
			}
			else
				new Thread() {
					@Override
					public void run() {
						if (updating != null)
							updating.setBackground(background);
						valueChanged(value);
					}
				}.start();
		}

		public abstract void valueChanged(int value);
	}

	protected static class LimitedRangeCheckbox extends JPanel implements ItemListener {
		protected JTextField min, max;
		protected JCheckBox checkbox;
		protected MotorSlider slider;
		protected Dictionary originalLabels, limitedLabels;
		protected int originalMin, originalMax;
		protected int limitedMin, limitedMax;

		public LimitedRangeCheckbox(String label, MotorSlider slider, int min, int max) {
			setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
			checkbox = new JCheckBox(label);
			add(checkbox);
			this.min = new JTextField("" + min);
			add(this.min);
			add(new JLabel(" to "));
			this.max = new JTextField("" + max);
			add(this.max);

			this.slider = slider;
			originalLabels = slider.getLabelTable();
			originalMin = slider.getMinimum();
			originalMax = slider.getMaximum();
			limitedMin = min;
			limitedMax = max;
			checkbox.setSelected(false);
			checkbox.addItemListener(this);
		}

		@Override
		public void itemStateChanged(ItemEvent e) {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				limitedMin = getValue(min, limitedMin);
				limitedMax = getValue(max, limitedMax);
				limitedLabels = makeLabelTable(limitedMin, limitedMax, 5);
				int current = slider.getValue();
				if (current < limitedMin)
					slider.setValue(limitedMin);
				else if (current > limitedMax)
					slider.setValue(limitedMax);
				slider.setMinimum(limitedMin);
				slider.setMaximum(limitedMax);
				slider.setLabelTable(limitedLabels);
			}
			else {
				slider.setMinimum(originalMin);
				slider.setMaximum(originalMax);
				slider.setLabelTable(originalLabels);
			}
		}

		protected static int getValue(JTextField text, int defaultValue) {
			try {
				return Integer.parseInt(text.getText());
			} catch (Exception e) {
				return defaultValue;
			}
		}
	}

	protected static Dictionary makeLabelTable(int min, int max, int count) {
		int spacing = (int)((max - min) / count);
		spacing = 100 * ((spacing + 50) / 100); // round to nearest 100
		Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();
		table.put(min, new JLabel("" + min));
		for (int i = max; i > min; i -= spacing)
			table.put(i, new JLabel("" + i));
		return table;
	}

	protected static abstract class IntegerField extends JTextField {
		public IntegerField(int value) {
			this(value, 4);
		}

		public IntegerField(int value, int columns) {
			super(columns);
			setText("" + value);
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					if (e.getKeyCode() != KeyEvent.VK_ENTER)
						return;
					valueChanged(getValue());
				}
			});
			addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					valueChanged(getValue());
				}
			});
		}

		public int getValue() {
			String typed = getText();
			if(!typed.matches("\\d+"))
				return 0;
			return Integer.parseInt(typed);
		}

		public abstract void valueChanged(int value);
	}

	protected static class IntegerSliderField extends IntegerField {
		protected JSlider slider;

		public IntegerSliderField(JSlider slider) {
			super(slider.getValue());
			this.slider = slider;
			if (slider instanceof MotorSlider)
				((MotorSlider)slider).updating = this;
		}

		@Override
		public void valueChanged(int value) {
			if (slider != null)
				slider.setValue(value);
		}
	}

	// Accessing the devices

	protected void maybeUpdateImage() {
		if (cameraLabel != null && updateLiveImage)
			gui.updateImage();
	}

	protected abstract static class RunTo extends Thread {
		protected int goal, current = Integer.MAX_VALUE;

		@Override
		public void run() {
			for (;;) try {
				if (goal != current) synchronized (this) {
					if (get() == goal) {
						current = goal;
						done();
						notifyAll();
					}
				}
				Thread.currentThread().sleep(50);
			} catch (Exception e) {
				return;
			}
		}

		public void run(int value) {
			synchronized (this) {
				if (goal == value) {
					done();
					return;
				}
				goal = value;
				try {
					set(goal);
				} catch (Exception e) {
					return;
				}
				synchronized (this) {
					if (!isAlive())
						start();
					try {
						wait();
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}

		public abstract int get() throws Exception ;

		public abstract void set(int value) throws Exception;

		public abstract void done();
	}

	protected RunTo runToX = new RunTo() {
		@Override
		public int get() throws Exception {
			return (int)mmc.getXPosition(xyStageLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setXYPosition(xyStageLabel, value, mmc.getYPosition(xyStageLabel));
		}

		@Override
		public void done() {
			xPosition.setText("" + goal);
		}
	};

	protected RunTo runToY = new RunTo() {
		@Override
		public int get() throws Exception {
			return (int)mmc.getYPosition(xyStageLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setXYPosition(xyStageLabel, mmc.getXPosition(xyStageLabel), value);
		}

		@Override
		public void done() {
			yPosition.setText("" + goal);
		}
	};

	protected RunTo runToZ = new RunTo() {
		@Override
		public int get() throws Exception {
			return (int)mmc.getPosition(zStageLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setPosition(zStageLabel, value);
		}

		@Override
		public void done() {
			zPosition.setText("" + goal);
		}
	};

	protected RunTo runToAngle = new RunTo() {
		@Override
		public int get() throws Exception {
			return (int)mmc.getPosition(twisterLabel);
		}

		@Override
		public void set(int value) throws Exception {
			mmc.setPosition(twisterLabel, value);
		}

		@Override
		public void done() {
			rotation.setText("" + goal);
		}
	};

	protected ImageProcessor snapSlice() throws Exception {
		mmc.snapImage();

		int width = (int)mmc.getImageWidth();
		int height = (int)mmc.getImageHeight();
		if (mmc.getBytesPerPixel() == 1) {
			byte[] pixels = (byte[])mmc.getImage();
			return new ByteProcessor(width, height, pixels, null);
		} else if (mmc.getBytesPerPixel() == 2){
			short[] pixels = (short[])mmc.getImage();
			return new ShortProcessor(width, height, pixels, null);
		} else
			return null;
	}

	protected void snapAndShowContinuousStack(final int zStart, final int zEnd) throws Exception {
		// Cannot run this on the EDT
		if (SwingUtilities.isEventDispatchThread()) {
			new Thread() {
				public void run() {
					try {
						snapAndShowContinuousStack(zStart, zEnd);
					} catch (Exception e) {
						IJ.handleException(e);
					}
				}
			}.start();
			return;
		}

		snapContinuousStack(zStart, zEnd).show();
	}

	protected ImagePlus snapContinuousStack(int zStart, int zEnd) throws Exception {
		String meta = getMetaData();
		ImageStack stack = null;
		zSlider.setValue(zStart);
		runToZ.run(zStart);
		IJ.wait(50); // wait 50 milliseconds for the state to settle
		zSlider.setValue(zEnd);
		int zStep = (zStart < zEnd ? +1 : -1);
IJ.log("from " + zStart + " to " + zEnd + ", step: " + zStep);
		for (int z = zStart; z  * zStep <= zEnd * zStep; z = z + zStep) {
IJ.log("Waiting for " + z + " (" + (z * zStep) + " < " + ((int)mmc.getPosition(zStageLabel) * zStep) + ")");
			while (z * zStep > (int)mmc.getPosition(zStageLabel) * zStep)
				Thread.yield();
IJ.log("Got " + mmc.getPosition(zStageLabel));
			ImageProcessor ip = snapSlice();
			z = (int)mmc.getPosition(zStageLabel);
IJ.log("Updated z to " + z);
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("z: " + z, ip);
		}
IJ.log("Finished taking " + (zStep * (zEnd - zStart)) + " slices (really got " + (stack == null ? "0" : stack.getSize() + ")"));
		ImagePlus result = new ImagePlus("SPIM!", stack);
		result.setProperty("Info", meta);
		return result;
	}

	protected ImagePlus snapStack(int zStart, int zEnd) throws Exception {
		String meta = getMetaData();
		ImageStack stack = null;
		int zStep = (zStart < zEnd ? +1 : -1);
		for (int z = zStart; z * zStep <= zEnd * zStep; z = z + zStep) {
			zSlider.setValue(z);
			runToZ.run(z);
			ImageProcessor ip = snapSlice();
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("z: " + z, ip);
		}
		ImagePlus result = new ImagePlus("SPIM!", stack);
		result.setProperty("Info", meta);
		return result;
	}

	protected String getMetaData() throws Exception {
		String meta = "";
		if (xyStageLabel != "")
			meta += "x motor position: " + mmc.getXPosition(xyStageLabel) + "\n"
				+ "y motor position: " + mmc.getYPosition(xyStageLabel) + "\n";
		if (zStageLabel != "")
			meta +=  "z motor position: " + mmc.getPosition(zStageLabel) + "\n";
		if (twisterLabel != "")
			meta +=  "twister position: " + mmc.getPosition(twisterLabel) + "\n"
				+ "twister angle: " + (360.0 / 200.0 * mmc.getPosition(twisterLabel)) + "\n";
		return meta;
	}
}

