package progacq;

import java.io.File;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.json.JSONObject;
import org.micromanager.utils.ReportingUtils;

import loci.common.DataTools;
import loci.common.services.ServiceFactory;

import loci.formats.IFormatWriter;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mmcorej.CMMCore;

import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.PositiveInteger;

public class OMETIFFHandler implements AcqOutputHandler {
	private File outputDirectory;
	private String[] xytzDevices;

	private IMetadata meta;
	private int imageCounter, sliceCounter;
	private IFormatWriter writer;


	private CMMCore core;
	private int stacks, timesteps;
	private int[] stackDepths;
	private double deltat;

	public OMETIFFHandler(CMMCore iCore, File outDir, String xyDev,
			String cDev, String zDev, String tDev, int[] iStackDepths,
			int iTimeSteps, double iDeltaT) {

		if(outDir == null || !outDir.exists() || !outDir.isDirectory())
			throw new IllegalArgumentException("Null path specified: " + outDir.toString());

		xytzDevices = new String[] {xyDev, cDev, zDev, tDev};

		imageCounter = sliceCounter = 0;

		stacks = iStackDepths.length;
		stackDepths = iStackDepths;
		core = iCore;
		timesteps = iTimeSteps;
		deltat = iDeltaT;
		outputDirectory = outDir;

		try {
			meta = new ServiceFactory().getInstance(OMEXMLService.class).createOMEXMLMetadata(null);

			meta.createRoot();
		} catch(Throwable t) {
			throw new IllegalArgumentException(t);
		}
	}

	private void openWriter(Vector3D position, double theta) throws Exception {
		File path = new File(outputDirectory,
				"blub" + imageCounter + "-x" + position.getX() + "-y" +
						position.getY() + "-theta" + theta + ".ome.tiff"
		);

		defaultMetaData();

		writer = new ImageWriter().getWriter(path.getAbsolutePath());

		writer.setWriteSequentially(true);
		writer.setMetadataRetrieve(meta);
		writer.setInterleaved(false);
		writer.setValidBitsPerPixel((int) core.getImageBitDepth());
		writer.setCompression("Uncompressed");
		writer.setId(path.getAbsolutePath());

		++imageCounter;
		sliceCounter = 0;
	}

	private void defaultMetaData() throws Exception {
		meta.setDatasetID(MetadataTools.createLSID("Dataset", 0), 0);

		meta.setImageID(MetadataTools.createLSID("Image", imageCounter), 0);
		meta.setPixelsID(MetadataTools.createLSID("Pixels", 0), 0);
		meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, 0);
		meta.setChannelID(MetadataTools.createLSID("Channel", 0), 0, 0);
		meta.setChannelSamplesPerPixel(new PositiveInteger(1), 0, 0);
		meta.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
		long bitDepth = core.getImageBitDepth();
		meta.setPixelsType(bitDepth == 8 ? PixelType.UINT8 : PixelType.UINT16, 0);

		meta.setPixelsSizeX(new PositiveInteger((int)core.getImageWidth()), 0);
		meta.setPixelsSizeY(new PositiveInteger((int)core.getImageHeight()), 0);
		meta.setPixelsSizeZ(new PositiveInteger(stackDepths[imageCounter % stackDepths.length]), 0);
		meta.setPixelsSizeC(new PositiveInteger(1), 0);
		meta.setPixelsSizeT(new PositiveInteger(timesteps), 0);

		meta.setPixelsPhysicalSizeX(new PositiveFloat(core.getPixelSizeUm()), 0);
		meta.setPixelsPhysicalSizeY(new PositiveFloat(core.getPixelSizeUm()), 0);
		meta.setPixelsPhysicalSizeZ(new PositiveFloat(1d), 0);
		meta.setPixelsTimeIncrement(new Double(deltat), 0);
	}

	@Override
	public ImagePlus getImagePlus() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	private Vector3D getPos(JSONObject metaobj) throws Exception {
		String xystr = metaobj.getString(xytzDevices[0]);
		double x = Double.parseDouble(xystr.substring(0, xystr.indexOf("x")));
		double y = Double.parseDouble(xystr.substring(xystr.indexOf("x") + 1));
		double z = metaobj.getDouble(xytzDevices[2]);

		return new Vector3D(x, y, z);
	}

	@Override
	public void processSlice(ImageProcessor ip, JSONObject metaobj)
			throws Exception {
		long bitDepth = core.getImageBitDepth();
		byte[] data = bitDepth == 8 ?
			(byte[])ip.getPixels() :
			DataTools.shortsToBytes((short[])ip.getPixels(), true);

		Vector3D pos = getPos(metaobj);

		// Determine differences from the last position.
		if(writer == null) {
			openWriter(pos, metaobj.getDouble(xytzDevices[1]));
		}


		meta.setPlanePositionX(pos.getX(), 0, sliceCounter);
		meta.setPlanePositionY(pos.getY(), 0, sliceCounter);
		meta.setPlanePositionZ(pos.getZ(), 0, sliceCounter);
		meta.setPlaneDeltaT(metaobj.getDouble(xytzDevices[3]), 0, sliceCounter);
		meta.setPlaneAnnotationRef(pos.getX() + "/" + pos.getY() + "/" + pos.getZ(), 0, sliceCounter, 0);

		writer.savePlane(sliceCounter, data);

		++sliceCounter;
	}

	@Override
	public void finalizeStack(int depth) throws Exception {
		ReportingUtils.logMessage("Finished stack along dimension " + depth);

		writer.close();
		writer = null;
	}

	@Override
	public void finalizeAcquisition() throws Exception {
		if(writer != null)
			writer.close();

		ReportingUtils.logMessage("" + imageCounter + " vs " + stacks);
		imageCounter = 0;

		writer = null;

	}
}