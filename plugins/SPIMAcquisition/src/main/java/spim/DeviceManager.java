package spim;

import ij.IJ;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

/**
 * @author Luke Stuyvenberg
 *
 * This class manages the principal devices involved in a SPIM setup. The goal
 * is to provide an authoritative reference for the device labels Micro-Manager
 * uses within our project.
 */
public class DeviceManager extends JFrame {
	private static final long serialVersionUID = -3345675926744884431L;

	public static enum SPIMDevice {
		STAGE_X ("X Stage", DeviceType.StageDevice),
		STAGE_Y ("Y Stage", DeviceType.StageDevice),
		STAGE_XY ("XY Stage", DeviceType.XYStageDevice),
		STAGE_Z ("Z Stage", DeviceType.StageDevice),
		STAGE_THETA ("Theta Stage", DeviceType.StageDevice),
		LASER1 ("Laser", DeviceType.ShutterDevice),
		LASER2 ("Laser (2)", DeviceType.ShutterDevice),
		CAMERA1 ("Camera", DeviceType.CameraDevice),
		CAMERA2 ("Camera (2)", DeviceType.CameraDevice),
		SYNCHRONIZER ("Synchronizer", DeviceType.SignalIODevice);

		private final String text;
		private final DeviceType mmtype;
		private SPIMDevice(String text, DeviceType type) {
			this.text = text;
			this.mmtype = type;
		}

		public String getText() {
			return this.text;
		}

		public DeviceType getMMType() {
			return this.mmtype;
		}
	};

	public static class SPIMSetup extends JPanel {
		private static final long serialVersionUID = 5356126433493461392L;

		private EnumMap<SPIMDevice, String> labelMap;
		private JTextField name;
		private CMMCore core;
		private EnumMap<SPIMDevice, Double> stageDests;

		public SPIMSetup(CMMCore core, String name, EnumMap<SPIMDevice, String> labels, final DeviceManager parent) {
			this.core = core;
			labelMap = new EnumMap<SPIMDevice, String>(SPIMDevice.class);
			stageDests = new EnumMap<SPIMDevice, Double>(SPIMDevice.class);

			for(SPIMDevice type : SPIMDevice.values())
				labelMap.put(type, labels == null ? getMMDefaultDevice(type) : labels.get(type));

			for(SPIMDevice type : labelMap.keySet()) try
			{
				if(type.getMMType().equals(DeviceType.StageDevice))
					if(isConnected(type))
						stageDests.put(type, getPosition(type));
					else
						stageDests.put(type, 0.0D);
			}
			catch(Exception e)
			{
				ij.IJ.handleException(e);
				stageDests.put(type, 0.0D);
			}

			setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));

			this.name = new JTextField(name);
			this.name.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent ke) {
					parent.updateSetupName(SPIMSetup.this);
				}
			});
			add(LayoutUtils.titled("Setup Name/Label", (JComponent) LayoutUtils.labelMe(this.name, "Name: ")));

			HashMap<DeviceType, JPanel> panelMap = new HashMap<DeviceType, JPanel>();
			for(final SPIMDevice type : SPIMDevice.values()) {
				JPanel put = panelMap.get(type.getMMType());
				if(put == null) {
					put = new JPanel();
					put.setLayout(new BoxLayout(put, BoxLayout.PAGE_AXIS));
					put.setBorder(BorderFactory.createTitledBorder(cleanName(type.getMMType()))); // TODO: Make titles for these, or drop the titledBorder.
					panelMap.put(type.getMMType(), put);
				}

				JComboBox cmbo = new JComboBox(augmentNone(core.getLoadedDevicesOfType(type.getMMType())));
				cmbo.setSelectedItem(!getDeviceLabel(type).isEmpty() ? getDeviceLabel(type) : "(none)");
				cmbo.addItemListener(new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent ie) {
						labelMap.put(type, ie.getItem().toString().equals("(none)") ? "" : ie.getItem().toString());
					}
				});

				put.add(LayoutUtils.labelMe(cmbo, type.getText() + ": "));

				add(put);
			}
		}

		@Override
		public String getName() {
			return name.getText();
		}

		public String getDeviceLabel(SPIMDevice type) {
			String ret = labelMap.get(type);

			return ret != null ? ret : "";
		}

		/*
		 * Connected device routines
		 */

		public boolean isConnected(SPIMDevice type)
		{
			return coreHasDevOfType(type.getMMType(), getDeviceLabel(type));
		}

		public boolean isAllDevicesConnected()
		{
			for(SPIMDevice type : labelMap.keySet())
				if(!isConnected(type))
					return false;

			return true;
		}

		public int getStageDimensions()
		{
			return
				(isConnected(SPIMDevice.STAGE_XY) ? 2 :
					((isConnected(SPIMDevice.STAGE_X) ? 1 : 0) +
					(isConnected(SPIMDevice.STAGE_Y) ? 1 : 0))
				) +
				(isConnected(SPIMDevice.STAGE_Z) ? 1 : 0) +
				(isConnected(SPIMDevice.STAGE_THETA) ? 1 : 0);
		}

		/**
		 * Returns true if this setup consists of at least one camera, one
		 * illumination device, and two stage dimensions.
		 *
		 * @return true if this constitutes a minimal microscope.
		 */
		public boolean isMinimalMicroscope()
		{
			return isConnected(SPIMDevice.CAMERA1) &&
					isConnected(SPIMDevice.LASER1) &&
					(getStageDimensions() >= 2);
		}

		/**
		 * Returns true if this setup consists of at least one camera, one
		 * illumination device, and four stage dimensions.
		 *
		 * @return true if this constitutes a basic SPIM setup.
		 */
		public boolean isBasicSPIM()
		{
			return isMinimalMicroscope() && getStageDimensions() >= 4;
		}

		/*
		 * SetPosition variants
		 */

		public void setPosition(Double x, Double y, Double z, Double t) throws Exception
		{
			if(x != null)
				setPosition(SPIMDevice.STAGE_X, x);

			if(y != null)
				setPosition(SPIMDevice.STAGE_Y, y);

			if(z != null)
				setPosition(SPIMDevice.STAGE_Z, z);

			if(t != null)
				setPosition(SPIMDevice.STAGE_THETA, t);
		}

		public void setPosition(Vector3D xyz, double t) throws Exception
		{
			setPosition(xyz.getX(), xyz.getY(), xyz.getZ(), t);
		}

		public void setPosition(Vector3D xyz) throws Exception
		{
			setPosition(xyz.getX(), xyz.getY(), xyz.getZ(), null);
		}

		public void setPosition(double x, double y) throws Exception
		{
			setPosition(x, y, null, null);
		}

		public void waitOn(SPIMDevice... devices) throws Exception
		{
			for(SPIMDevice dev : devices)
				core.waitForDevice(getDeviceLabel(dev));
		}

		public void waitAll() throws Exception
		{
			for(String dev : labelMap.values())
				if(dev != null && !dev.isEmpty())
					core.waitForDevice(dev);
		}

		public void setPosition(SPIMDevice stage, double dest) throws Exception
		{
			if(stage != SPIMDevice.STAGE_X && stage != SPIMDevice.STAGE_Y &&
				stage != SPIMDevice.STAGE_Z && stage != SPIMDevice.STAGE_THETA)
				throw new Exception("setPosition called with non-Stage device " + stage.getText() +".");

			if(isConnected(stage))
			{
				stageDests.put(stage, dest);
				core.setPosition(getDeviceLabel(stage), dest);
			}
			else if((stage == SPIMDevice.STAGE_X || stage == SPIMDevice.STAGE_Y) &&
					isConnected(SPIMDevice.STAGE_XY))
			{
				stageDests.put(stage, dest);
				core.setXYPosition(getDeviceLabel(SPIMDevice.STAGE_XY),
					stageDests.get(SPIMDevice.STAGE_X),
					stageDests.get(SPIMDevice.STAGE_Y));
			}
			else
			{
				throw new Exception("setPosition called on disconnected Stage device " + stage.getText() + ".");
			}
		}

		/*
		 * GetPosition variants
		 */

		public double getDestination(SPIMDevice stage) throws Exception
		{
			if(stage != SPIMDevice.STAGE_X && stage != SPIMDevice.STAGE_Y &&
					stage != SPIMDevice.STAGE_Z && stage != SPIMDevice.STAGE_THETA)
					throw new Exception("getDestination called with non-Stage device " + stage.getText() + ".");

			return stageDests.get(stage);
		}

		public double getPosition(SPIMDevice stage) throws Exception
		{
			if(stage != SPIMDevice.STAGE_X && stage != SPIMDevice.STAGE_Y &&
					stage != SPIMDevice.STAGE_Z && stage != SPIMDevice.STAGE_THETA)
					throw new Exception("getPosition called with non-Stage device " + stage.getText() + ".");

			if(isConnected(stage))
			{
				return core.getPosition(getDeviceLabel(stage));
			}
			else if((stage == SPIMDevice.STAGE_X || stage == SPIMDevice.STAGE_Y) &&
					isConnected(SPIMDevice.STAGE_XY))
			{
				if(stage == SPIMDevice.STAGE_X)
					return core.getXPosition(getDeviceLabel(SPIMDevice.STAGE_XY));
				else
					return core.getYPosition(getDeviceLabel(SPIMDevice.STAGE_XY));
			}
			else
			{
				throw new Exception("getPosition called on disconnected Stage device " + stage.getText() + ".");
			}
		}

		public double getAngle() throws Exception
		{
			return getPosition(SPIMDevice.STAGE_THETA);
		}

		public Vector3D getPosition() throws Exception
		{
			return new Vector3D(getPosition(SPIMDevice.STAGE_X),
					getPosition(SPIMDevice.STAGE_Y),
					getPosition(SPIMDevice.STAGE_Z));
		}

		/*
		 * Private helper routines
		 */

		private String getMMDefaultDevice(SPIMDevice type) {
			switch(type) {
			case STAGE_X:
				if(coreHasDevOfType(DeviceType.StageDevice, core.getXYStageDevice() + ".X"))
					return core.getXYStageDevice() + ".X";

				return "";
			case STAGE_Y:
				if(coreHasDevOfType(DeviceType.StageDevice, core.getXYStageDevice() + ".Y"))
					return core.getXYStageDevice() + ".Y";

				return "";
			case STAGE_XY:
				return core.getXYStageDevice();
			case STAGE_Z:
				return core.getFocusDevice();
			case STAGE_THETA:
				for(String lbl : core.getLoadedDevicesOfType(DeviceType.StageDevice))
					if(!lbl.equals(core.getFocusDevice()) && !lbl.endsWith(".X") && !lbl.endsWith(".Y"))
						return lbl;

				return "";
			case LASER1:
				return core.getShutterDevice();
			case LASER2:
				for(String lbl : core.getLoadedDevicesOfType(DeviceType.ShutterDevice))
					if(!lbl.equals(core.getShutterDevice()))
						return lbl;

				return "";
			case CAMERA1:
				return core.getCameraDevice();
			case CAMERA2:
				for(String lbl : core.getLoadedDevicesOfType(DeviceType.CameraDevice))
					if(!lbl.equals(core.getCameraDevice()))
						return lbl;

				return null;
			case SYNCHRONIZER:
				return core.getShutterDevice();
			default:
				return "";
			}
		}

		private boolean coreHasDevOfType(DeviceType t, String lbl) {
			return strVecContains(core.getLoadedDevicesOfType(t), lbl);
		}

		private static String[] augmentNone(StrVector arg) {
			String[] out = new String[(int) (arg.size() + 1)];

			out[0] = "(none)";
			for(int s = 0; s < arg.size(); ++s)
				out[s + 1] = arg.get(s);

			return out;
		}

		private static boolean strVecContains(StrVector v, String s) {
			for(String s2 : v)
				if(s2.equals(s))
					return true;

			return false;
		}
		
		private static String cleanName(DeviceType type) {
			String cleaned = type.toString().replace("Device", "");

			java.util.regex.Pattern pat = java.util.regex.Pattern.compile("([a-z]|XY|IO)([A-Z])");
			return pat.matcher(cleaned).replaceAll("$1 $2");
		}
	}

	private CMMCore core;
	private JTabbedPane setupTabs;
	private List<SPIMSetup> setups;

	/**
	 * Returns the Micro-Manager device label for the specified device type, in
	 * the first index.
	 *
	 * @param type The device type to determine the label of
	 * @return Micro-Manager's device label for the current device, or null if
	 * 		   not available.
	 */
	public String getLabel(SPIMDevice type) {
		return getDeviceLabel(type, 0);
	}

	/**
	 * Returns the MM device label for the specified device type in the first
	 * SPIM setup.
	 * 
	 * @param type The device type to determine the label of.
	 * @param setup Which attached setup to get the device of.
	 * @return The correct device label, or empty string if not available.
	 */
	public String getDeviceLabel(SPIMDevice type, int setup) {
		if(setups.size() <= setup || !setups.get(setup).isConnected(type))
			return "";

		return setups.get(setup).getDeviceLabel(type);
	}

	/**
	 * Returns the setup object for the given setup. Used by external classes to
	 * check various attributes of the setup.
	 *
	 * @param setup Which attached setup to get the device of.
	 * @return The setup object, or null if not available.
	 */
	public SPIMSetup getSetup(int setup) {
		if(setups.size() <= setup)
			return null;

		return setups.get(setup);
	}

	/**
	 * Returns the first setup object.
	 * @return The first setup object, or null if there is none (weird...)
	 */
	public SPIMSetup getSetup() {
		if(setups.size() <= 0)
			return null;

		return setups.get(0);
	}

	public DeviceManager(CMMCore core) {
		super("SPIM Device Manager");

		this.core = core;
		this.setups = new ArrayList<SPIMSetup>(1);

		java.awt.Container me = getContentPane();
		me.setLayout(new BoxLayout(me, BoxLayout.PAGE_AXIS));

		setupTabs = new JTabbedPane(1);

		// Build the first setup automatically from MM's devices.
		addSetup(new SPIMSetup(core, "Default Setup", null, this));

		me.add(setupTabs);

		JButton addBtn = new JButton("Add Setup");
		addBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				SPIMSetup setup = new SPIMSetup(DeviceManager.this.core,
						"New Setup", setups.size() == 0 ? null : null, DeviceManager.this); // TODO: Make defaults blank?

				addSetup(setup);
			}
		});

		JButton removeBtn = new JButton("Remove Setup");
		removeBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				if(setupTabs.getSelectedIndex() == 0)
					return;

				removeSetup((SPIMSetup)setupTabs.getSelectedComponent());
			}
		});
		me.add(LayoutUtils.horizPanel(Box.createHorizontalStrut(386), removeBtn));

		pack();

		setMaximumSize(getPreferredSize());
	}

	private void addSetup(SPIMSetup setup)
	{
		setups.add(setup);

		if(setupTabs.getTabCount() > 0) {
			setupTabs.setSelectedIndex(0);
			setupTabs.insertTab(setup.getName(), null, setup, null, setupTabs.getTabCount() - 1);
		} else {
			setupTabs.insertTab(setup.getName(), null, setup, null, 0);
		}
		setupTabs.setSelectedComponent(setup);
	}

	public void save(Preferences prefs) throws BackingStoreException {
		for(int i=0; i < setups.size(); ++i)
		{
			Preferences setupPrefs = prefs.node(Integer.toString(i));

			setupPrefs.putBoolean("fromMM", false);
			setupPrefs.put("name", setups.get(i).getName());

			for(SPIMDevice type : SPIMDevice.values())
				if(setups.get(i).isConnected(type))
					setupPrefs.put(type.name().toLowerCase(), setups.get(i).getDeviceLabel(type));
		}
	}

	public void load(Preferences prefs) {
		try {
			String[] children = prefs.childrenNames(); // might throw; don't clear yet.

			if(children.length == 0) // don't clear unless we're actually loading something
				return;

			setups.clear();
			setupTabs.removeAll();
			setupTabs.add("+", new JPanel());

			for(String name : children) {
				Preferences setupPrefs = prefs.node(name);

				EnumMap<SPIMDevice, String> labelsMap = null;

				if(!setupPrefs.getBoolean("fromMM", false)) {
					labelsMap = new EnumMap<SPIMDevice, String>(SPIMDevice.class);

					for(SPIMDevice type : SPIMDevice.values())
						labelsMap.put(type, setupPrefs.get(type.name().toLowerCase(), null));
				}

				SPIMSetup setup = new SPIMSetup(core,
						setupPrefs.get("name", "(null)"),
						labelsMap,
						this);

				addSetup(setup);
			}
		} catch(BackingStoreException e) {
			IJ.handleException(e);
		}
	}

	protected void updateSetupName(SPIMSetup setup) {
		int idx = setupTabs.indexOfComponent(setup);

		if(idx >= 0)
			setupTabs.setTitleAt(idx, setup.getName());
		else
			System.out.println("Couldn't find tab...");
	}
	
	private void removeSetup(SPIMSetup setup) {
		if(setup == setupTabs.getSelectedComponent())
			setupTabs.setSelectedIndex(setupTabs.indexOfComponent(setup) - 1);

		setups.remove(setup);
		setupTabs.remove(setup);
	}
}
