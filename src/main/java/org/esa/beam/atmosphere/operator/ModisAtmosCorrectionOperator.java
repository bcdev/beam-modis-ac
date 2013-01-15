package org.esa.beam.atmosphere.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.PixelData;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.waterradiance.AuxdataProvider;
import org.esa.beam.waterradiance.AuxdataProviderFactory;

import java.awt.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * Main operator for the MODIS atmospheric correction.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "MismatchedReadAndWriteOfArray"})
@OperatorMetadata(alias = "Modis.AtmosphericCorrection",
                  version = "1.0-SNAPSHOT",
                  authors = "Roland Doerffer, Olaf Danne",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "MODIS atmospheric correction using a neural net.")
public class ModisAtmosCorrectionOperator extends Operator {

    @SourceProduct(label = "MODIS L1b input product", description = "The MODIS L1b input product.")
    private Product modisL1bProduct;

    @SourceProduct(label = "MODIS GEO input product", description = "The MODIS GEO input product.")
    private Product modisGeoProduct;

    @SourceProduct(label = "MODIS Flag input product", description = "A MODIS flag input product (optional, e.g. the L2).",
                   optional = true)
    private Product modisFlagProduct;

    @TargetProduct(description = "The atmospheric corrected output product.")
    private Product targetProduct;


    @Parameter(label = "MODIS net (full path required for other than default)",
               defaultValue = Constants.MODIS_ATMOSPHERIC_NET_NAME,
               description = "The file of the atmospheric net to be used instead of the default neural net.",
               notNull = false)
    private File atmoNetModisFile;

    @Parameter(defaultValue = "true", label = "Output TOSA reflectance",
               description = "Toggles the output of Top of Standard Atmosphere reflectance.")
    private boolean outputTosa;

    @Parameter(defaultValue = "", // todo
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "",   // todo
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(label = "Use climatology map for salinity and temperature", defaultValue = "true",
               description = "By default a climatology map is used. If set to 'false' the specified average values are used " +
                       "for the whole scene.")
    private boolean useSnTMap;

    @Parameter(label = "Average salinity", defaultValue = "35", unit = "PSU", description = "The salinity of the water")
    private double averageSalinity;

    @Parameter(label = "Average temperature", defaultValue = "15", unit = "°C", description = "The Water temperature")
    private double averageTemperature;


    public static final String MODIS_ATMOS_CORRECTION_VERSION = "1.0-SNAPSHOT";

    private String modisNeuralNetString;
    private Date date;
    private AuxdataProvider snTProvider;

    private RasterDataNode latNode;
    private RasterDataNode lonNode;
    private RasterDataNode solzenNode;
    private RasterDataNode solaziNode;
    private RasterDataNode satzenNode;
    private RasterDataNode sataziNode;

    private Band[] spectralNodes;
    private RasterDataNode flagsNode;

    private int nadirColumnIndex;


    @Override
    public void initialize() throws OperatorException {
        validateModisL1bProduct(modisL1bProduct);

        latNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_LATITUDE_BAND_NAME);
        lonNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_LONGITUDE_BAND_NAME);
        solzenNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_SUN_ZENITH_BAND_NAME);
        solaziNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_SUN_AZIMUTH_BAND_NAME);
        satzenNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_VIEW_ZENITH_BAND_NAME);
        sataziNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_VIEW_AZIMUTH_BAND_NAME);

        spectralNodes = new Band[Constants.MODIS_SPECTRAL_BAND_NAMES.length];
        for (int i = 0; i < Constants.MODIS_SPECTRAL_BAND_NAMES.length; i++) {
            spectralNodes[i] = modisL1bProduct.getBand(Constants.MODIS_SPECTRAL_BAND_NAMES[i]);
        }

        final int rasterHeight = modisL1bProduct.getSceneRasterHeight();
        final int rasterWidth = modisL1bProduct.getSceneRasterWidth();

        Product outputProduct = new Product(modisL1bProduct.getName() + "_AC", "MODIS_L2_AC", rasterWidth, rasterHeight);
        outputProduct.setStartTime(modisL1bProduct.getStartTime());
        outputProduct.setEndTime(modisL1bProduct.getEndTime());
        ProductUtils.copyMetadata(modisL1bProduct, outputProduct);
        ProductUtils.copyGeoCoding(modisL1bProduct, outputProduct);

        setTargetProduct(outputProduct);
        addTargetBands(outputProduct);

        InputStream modisNeuralNetStream = getNeuralNetStream(Constants.MODIS_ATMOSPHERIC_NET_NAME, atmoNetModisFile);
        modisNeuralNetString = readNeuralNetFromStream(modisNeuralNetStream);

        nadirColumnIndex = ModisFlightDirection.findNadirColumnIndex(modisL1bProduct);

        if (useSnTMap) {
            snTProvider = createSnTProvider();
            date = modisL1bProduct.getStartTime().getAsDate();

        }
        if (modisFlagProduct != null) {
            flagsNode = modisFlagProduct.getRasterDataNode(Constants.MODIS_FLAG_BAND_NAME);
            ProductUtils.copyFlagBands(modisFlagProduct, outputProduct, true);
        }

        setTargetProduct(outputProduct);

    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws
            OperatorException {
        pm.beginTask("Correcting atmosphere...", targetRectangle.height);
        try {
            final Map<String, ProductData> modisSampleDataMap = preLoadModisSources(targetRectangle);
            final Map<String, ProductData> targetSampleDataMap = getTargetSampleData(targetTiles);

            AtmosCorrection ac = new AtmosCorrection(new NNffbpAlphaTabFast(modisNeuralNetString));

            for (int y = 0; y < targetRectangle.getHeight(); y++) {
                checkForCancellation();
                final int lineIndex = y * targetRectangle.width;
                final int pixelY = targetRectangle.y + y;

                for (int x = 0; x < targetRectangle.getWidth(); x++) {
                    final int pixelIndex = lineIndex + x;
                    final PixelData inputData = loadModisPixelData(modisSampleDataMap, pixelIndex);
                    final int pixelX = targetRectangle.x + x;
                    inputData.pixelX = pixelX;
                    inputData.pixelY = pixelY;

                    double salinity;
                    double temperature;
                    if (snTProvider != null) {
                        GeoCoding geoCoding = modisL1bProduct.getGeoCoding();
                        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(pixelX + 0.5f, pixelY + 0.5f), null);
                        salinity = snTProvider.getSalinity(date, geoPos.getLat(), geoPos.getLon());
                        temperature = snTProvider.getTemperature(date, geoPos.getLat(), geoPos.getLon());
                        if (Double.isNaN(salinity)) {
                            salinity = averageSalinity;
                        }
                        if (Double.isNaN(temperature)) {
                            temperature = averageTemperature;
                        }
                    } else {
                        salinity = averageSalinity;
                        temperature = averageTemperature;
                    }

                    AtmosCorrectionResult acResult = null;
                    acResult = ac.perform(inputData, temperature, salinity);

                    fillTargetSampleData(targetSampleDataMap, pixelIndex, inputData, acResult);
                }
                pm.worked(1);
            }
            commitSampleData(targetSampleDataMap, targetTiles);
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }

    }

    private PixelData loadModisPixelData(Map<String, ProductData> sourceTileMap, int index) {
        final PixelData pixelData = new PixelData();
        pixelData.nadirColumnIndex = nadirColumnIndex;
        final ProductData flags = sourceTileMap.get(Constants.MODIS_FLAG_BAND_NAME);
        if (flags != null) {
            pixelData.flag = flags.getElemIntAt(index);
        }

        pixelData.solzen = getScaledValue(sourceTileMap, solzenNode, index);
        pixelData.solazi = getScaledValue(sourceTileMap, solaziNode, index);
        pixelData.satzen = getScaledValue(sourceTileMap, satzenNode, index);
        pixelData.satazi = getScaledValue(sourceTileMap, sataziNode, index);
        pixelData.lat = getScaledValue(sourceTileMap, latNode, index);
        pixelData.lon = getScaledValue(sourceTileMap, lonNode, index);

        pixelData.toa_radiance = new double[spectralNodes.length];
        for (int i = 0; i < spectralNodes.length; i++) {
            final Band spectralNode = spectralNodes[i];
            pixelData.toa_radiance[i] = getScaledValue(sourceTileMap, spectralNode, index);
        }
        return pixelData;
    }

    private static double getScaledValue(Map<String, ProductData> sourceTileMap, RasterDataNode rasterDataNode,
                                         int index) {
        double rawValue = sourceTileMap.get(rasterDataNode.getName()).getElemFloatAt(index);
        rawValue = rasterDataNode.scale(rawValue);
        return rawValue;
    }


    private Map<String, ProductData> preLoadModisSources(Rectangle targetRectangle) {
        final Map<String, ProductData> map = new HashMap<String, ProductData>(27);

        if (flagsNode != null) {
            final Tile l1pFlagTile = getSourceTile(flagsNode, targetRectangle);
            map.put(l1pFlagTile.getRasterDataNode().getName(), l1pFlagTile.getRawSamples());
        }

        final Tile solzenTile = getSourceTile(solzenNode, targetRectangle);
        map.put(solzenTile.getRasterDataNode().getName(), solzenTile.getRawSamples());

        final Tile solaziTile = getSourceTile(solaziNode, targetRectangle);
        map.put(solaziTile.getRasterDataNode().getName(), solaziTile.getRawSamples());

        final Tile satzenTile = getSourceTile(satzenNode, targetRectangle);
        map.put(satzenTile.getRasterDataNode().getName(), satzenTile.getRawSamples());

        final Tile sataziTile = getSourceTile(sataziNode, targetRectangle);
        map.put(sataziTile.getRasterDataNode().getName(), sataziTile.getRawSamples());

        final Tile latitudeTile = getSourceTile(latNode, targetRectangle);
        map.put(latitudeTile.getRasterDataNode().getName(), latitudeTile.getRawSamples());

        final Tile longitudeTile = getSourceTile(lonNode, targetRectangle);
        map.put(longitudeTile.getRasterDataNode().getName(), longitudeTile.getRawSamples());

        for (RasterDataNode spectralNode : spectralNodes) {
            final Tile spectralTile = getSourceTile(spectralNode, targetRectangle);
            map.put(spectralTile.getRasterDataNode().getName(), spectralTile.getRawSamples());
        }
        return map;
    }

    private static Map<String, ProductData> getTargetSampleData(Map<Band, Tile> targetTiles) {
        final Map<String, ProductData> map = new HashMap<String, ProductData>(targetTiles.size());
        for (Map.Entry<Band, Tile> bandTileEntry : targetTiles.entrySet()) {
            final Band band = bandTileEntry.getKey();
            final Tile tile = bandTileEntry.getValue();
            map.put(band.getName(), tile.getRawSamples());
        }
        return map;
    }

    private static void commitSampleData(Map<String, ProductData> sampleDataMap, Map<Band, Tile> targetTiles) {
        for (Map.Entry<Band, Tile> bandTileEntry : targetTiles.entrySet()) {
            final Band band = bandTileEntry.getKey();
            final Tile tile = bandTileEntry.getValue();
            tile.setRawSamples(sampleDataMap.get(band.getName()));
        }

    }

    private void fillTargetSampleData(Map<String, ProductData> targetSampleData, int pixelIndex, PixelData inputData,
                                      AtmosCorrectionResult acResult) {

        fillTargetSample(Constants.MODIS_TOSA_BAND_NAMES, pixelIndex, targetSampleData, acResult.getReflec());
        if (outputTosa) {
            fillTargetSample(Constants.MODIS_TOSA_BAND_NAMES, pixelIndex, targetSampleData, acResult.getTosaReflec());
        }
    }

    private void fillTargetSample(String[] bandNames, int pixelIndex,
                                  Map<String, ProductData> targetData, double[] values) {
        for (int i = 0; i < bandNames.length; i++) {
            final String bandName = bandNames[i];
            if (bandName != null) {
                int bandIndex = i > 10 ? i - 1 : i;
                final ProductData tile = targetData.get(bandName);
                tile.setElemDoubleAt(pixelIndex, values[bandIndex]);
            }
        }
    }

    private void addTargetBands(Product outputProduct) {
        final List<String> groupList = new ArrayList<String>();
        addSpectralTargetBands(outputProduct, Constants.MODIS_REFLEC_BAND_NAMES, "Water leaving reflectance at {0} nm", "sr^-1");
        groupList.add("reflec");
        if (outputTosa) {
            addSpectralTargetBands(outputProduct, Constants.MODIS_TOSA_BAND_NAMES, "TOSA Reflectance at {0} nm", "sr^-1");
            groupList.add("tosa_reflec");
        }
    }

    private void addSpectralTargetBands(Product outputProduct, String[] bandNames, String descriptionPattern, String unit) {
        for (int i = 0; i < Constants.MODIS_REFLEC_BAND_NAMES.length; i++) {
            final float wvl = Float.parseFloat(bandNames[i].substring(bandNames[i].length()-3, bandNames[i].length()));
            final String descr = MessageFormat.format(descriptionPattern, wvl);
            final Band band = outputProduct.addBand(bandNames[i], ProductData.TYPE_FLOAT32);
            band.setDescription(descr);
            band.setUnit(unit);
            band.setValidPixelExpression("");   // todo
        }
    }

    private void validateModisL1bProduct(Product modisL1bProduct) {
        // todo
    }

    private InputStream getNeuralNetStream(String resourceNetName, File neuralNetFile) {
        InputStream neuralNetStream;
        if (neuralNetFile.equals((new File(resourceNetName)))) {
            neuralNetStream = getClass().getResourceAsStream(resourceNetName);
        } else {
            try {
                neuralNetStream = new FileInputStream(neuralNetFile);
            } catch (FileNotFoundException e) {
                throw new OperatorException(e);
            }
        }
        return neuralNetStream;
    }

    private String readNeuralNetFromStream(InputStream neuralNetStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(neuralNetStream));
        try {
            String line = reader.readLine();
            final StringBuilder sb = new StringBuilder();
            while (line != null) {
                // have to append line terminator, cause it's not included in line
                sb.append(line).append('\n');
                line = reader.readLine();
            }
            return sb.toString();
        } catch (IOException ioe) {
            throw new OperatorException("Could not initialize neural net", ioe);
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }

    private AuxdataProvider createSnTProvider() {
        try {
            return AuxdataProviderFactory.createDataProvider();
        } catch (IOException ioe) {
            throw new OperatorException("Not able to create provider for auxiliary data.", ioe);
        }
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ModisAtmosCorrectionOperator.class);
        }
    }
}
