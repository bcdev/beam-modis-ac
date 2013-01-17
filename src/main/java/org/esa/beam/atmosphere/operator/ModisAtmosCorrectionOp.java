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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Main operator for the MODIS atmospheric correction.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "MismatchedReadAndWriteOfArray"})
@OperatorMetadata(alias = "Modis.AtmosCorrection",
                  version = "1.0-SNAPSHOT",
                  authors = "Roland Doerffer, Olaf Danne",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "MODIS atmospheric correction using a neural net.")
public class ModisAtmosCorrectionOp extends Operator {

    @SourceProduct(label = "MODIS L1b input product", description = "The MODIS L1b input product.")
    private Product modisL1bProduct;

    @SourceProduct(label = "MODIS GEO input product", description = "The MODIS GEO input product.")
    private Product modisGeoProduct;

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

    @Parameter(label = "Use SRTM Land/Water mask", defaultValue = "true",
               description = "If set to 'false' a land detection expression as defined below is used.")
    private boolean useSrtmWaterMask;

    @Parameter(defaultValue = "EV_1KM_RefSB_16 > 0.1", // todo
               label = "Land detection expression (if no SRTM mask used)",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "EV_1KM_RefSB_16 > 0.027",   // todo
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "EV_1KM_RefSB_16 > 0.1", label = "'TOA out of range' (TOA_OOR flag) detection expression")
    private String rlToaOorExpression;

    @Parameter(label = "Use climatology map for salinity and temperature", defaultValue = "true",
               description = "By default a climatology map is used. If set to 'false' the specified average values are used " +
                       "for the whole scene.")
    private boolean useSnTMap;

    @Parameter(label = "Average salinity (if no climatology)", defaultValue = "35", unit = "PSU", description = "The salinity of the water (PSU)")
    private double averageSalinity;

    @Parameter(label = "Average temperature (if no climatology)", defaultValue = "15", unit = "°C", description = "The Water temperature (C)")
    private double averageTemperature;

    @Parameter(label = "Altitude", defaultValue = "0", unit = "m", description = "Altitude (m)")
    private double altitude;

    @Parameter(label = "Pressure", defaultValue = "1013.25", unit = "hPa", description = "Pressure at altitude (hPa)")
    private double pressure;

    @Parameter(label = "Ozone", defaultValue = "350", unit = "DU", description = "Ozone (DU)")
    private double ozone;


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

    private int nadirColumnIndex;

    private Band validationBand;


    @Override
    public void initialize() throws OperatorException {
        validateModisL1bProduct(modisL1bProduct);

        latNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_GEO_GEOLOCATION_BAND_NAME_PREFIX+
                                                            Constants.MODIS_LATITUDE_BAND_NAME);
        lonNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_GEO_GEOLOCATION_BAND_NAME_PREFIX +
                                                            Constants.MODIS_LONGITUDE_BAND_NAME);
        solzenNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_GEO_DATAFIELDS_BAND_NAME_PREFIX +
                                                               Constants.MODIS_SUN_ZENITH_BAND_NAME);
        solaziNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_GEO_DATAFIELDS_BAND_NAME_PREFIX +
                                                               Constants.MODIS_SUN_AZIMUTH_BAND_NAME);
        satzenNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_GEO_DATAFIELDS_BAND_NAME_PREFIX +
                                                               Constants.MODIS_VIEW_ZENITH_BAND_NAME);
        sataziNode = modisGeoProduct.getRasterDataNode(Constants.MODIS_GEO_DATAFIELDS_BAND_NAME_PREFIX +
                                                               Constants.MODIS_VIEW_AZIMUTH_BAND_NAME);

        spectralNodes = new Band[Constants.MODIS_SPECTRAL_BAND_NAMES.length];
        for (int i = 0; i < Constants.MODIS_SPECTRAL_BAND_NAMES.length; i++) {
            spectralNodes[i] = modisL1bProduct.getBand(Constants.MODIS_TOA_BAND_NAME_PREFIX +
                                                               Constants.MODIS_SPECTRAL_BAND_NAMES[i]);
        }

        final int rasterHeight = modisL1bProduct.getSceneRasterHeight();
        final int rasterWidth = modisL1bProduct.getSceneRasterWidth();

        Product outputProduct = new Product(modisL1bProduct.getName() + "_AC", "MODIS_L2_AC", rasterWidth, rasterHeight);
        outputProduct.setStartTime(modisL1bProduct.getStartTime());
        outputProduct.setEndTime(modisL1bProduct.getEndTime());
        ProductUtils.copyMetadata(modisL1bProduct, outputProduct);
        ProductUtils.copyGeoCoding(modisL1bProduct, outputProduct);

        addTargetBands(outputProduct);

        Band acFlagsBand = outputProduct.addBand(Constants.AC_FLAG_BAND_NAME, ProductData.TYPE_UINT16);
        final FlagCoding acFlagCoding = createAcFlagCoding();
        acFlagsBand.setSampleCoding(acFlagCoding);
        outputProduct.getFlagCodingGroup().add(acFlagCoding);
        addAcMasks(outputProduct);

        final ToaReflectanceValidationOp validationOp = ToaReflectanceValidationOp.create(modisL1bProduct,
                                                                                          useSrtmWaterMask,
                                                                                          landExpression,
                                                                                          cloudIceExpression,
                                                                                          rlToaOorExpression);
        Product toaValidationProduct = validationOp.getTargetProduct();
        validationBand = toaValidationProduct.getBandAt(0);

        InputStream modisNeuralNetStream = getNeuralNetStream(Constants.MODIS_ATMOSPHERIC_NET_NAME, atmoNetModisFile);
        modisNeuralNetString = readNeuralNetFromStream(modisNeuralNetStream);

        nadirColumnIndex = ModisFlightDirection.findNadirColumnIndex(modisGeoProduct);

        if (useSnTMap) {
            snTProvider = createSnTProvider();
            date = modisL1bProduct.getStartTime().getAsDate();

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

                    AtmosCorrectionResult acResult = ac.perform(inputData, temperature, salinity);

                    fillTargetSampleData(targetSampleDataMap, pixelIndex, acResult);
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
        pixelData.validation = sourceTileMap.get(validationBand.getName()).getElemIntAt(index);
        final ProductData flags = sourceTileMap.get(Constants.MODIS_L2_FLAG_BAND_NAME);
        if (flags != null) {
            pixelData.flag = flags.getElemIntAt(index);
        }

        pixelData.solzen = getScaledValue(sourceTileMap, solzenNode, index);
        pixelData.solazi = getScaledValue(sourceTileMap, solaziNode, index);
        pixelData.satzen = getScaledValue(sourceTileMap, satzenNode, index);
        pixelData.satazi = getScaledValue(sourceTileMap, sataziNode, index);
        pixelData.lat = getScaledValue(sourceTileMap, latNode, index);
        pixelData.lon = getScaledValue(sourceTileMap, lonNode, index);

        // todo: can we get these from aux product??
        pixelData.ozone = ozone;
        pixelData.altitude = altitude;
        pixelData.pressure = pressure;

        // we need the following 9 spectral nodes (toa radiances) as input:
        // 412nm (RefSB_8)
        // 443nm (RefSB_9)
        // 488nm (RefSB_10)
        // 531nm (RefSB_11)
        // 547nm (RefSB_12)
        // 667nm (RefSB_13lo)
        // 678nm (RefSB_14lo)
        // 748nm (RefSB_15)
        // 869nm (RefSB_16)
        pixelData.toa_radiance = new double[Constants.MODIS_SPECTRAL_BANDNAMES_TO_USE.length];
        pixelData.solar_flux = new double[Constants.MODIS_SPECTRAL_BANDNAMES_TO_USE.length];
        int bandsToUseIndex = 0;
        for (String bandToUse : Constants.MODIS_SPECTRAL_BANDNAMES_TO_USE) {
            for (final Band spectralNode : spectralNodes) {
                if (spectralNode.getName().equals(bandToUse)) {
                    pixelData.toa_radiance[bandsToUseIndex] = getScaledValue(sourceTileMap, spectralNode, index);
                    pixelData.solar_flux[bandsToUseIndex] = Constants.SOLAR_FLUXES_TO_USE[bandsToUseIndex];
                    bandsToUseIndex++;
                }
            }
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

        final Tile validationTile = getSourceTile(validationBand, targetRectangle);
        map.put(validationBand.getName(), validationTile.getRawSamples());

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

    private void fillTargetSampleData(Map<String, ProductData> targetSampleData, int pixelIndex,
                                      AtmosCorrectionResult acResult) {

        final ProductData acFlagTile = targetSampleData.get(Constants.AC_FLAG_BAND_NAME);
        acFlagTile.setElemIntAt(pixelIndex, acResult.getFlag());
        fillTargetSample(Constants.MODIS_REFLEC_BAND_NAMES, pixelIndex, targetSampleData, acResult.getReflec());
        if (outputTosa) {
            fillTargetSample(Constants.MODIS_TOSA_REFLEC_BAND_NAMES, pixelIndex, targetSampleData, acResult.getTosaReflec());
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
        addSpectralTargetBands(outputProduct, Constants.MODIS_REFLEC_BAND_NAMES, "Water leaving reflectance at {0} nm", "sr^-1");
        if (outputTosa) {
            addSpectralTargetBands(outputProduct, Constants.MODIS_TOSA_REFLEC_BAND_NAMES, "TOSA Reflectance at {0} nm", "sr^-1");
        }

        final String[] splitSataziName = sataziNode.getName().split("/");
        ProductUtils.copyBand(sataziNode.getName(), modisGeoProduct, splitSataziName[2], outputProduct, true);
        final String[] splitSolaziName = solaziNode.getName().split("/");
        ProductUtils.copyBand(solaziNode.getName(), modisGeoProduct, splitSolaziName[2], outputProduct, true);
        final String[] splitSatzenName = satzenNode.getName().split("/");
        ProductUtils.copyBand(satzenNode.getName(), modisGeoProduct, splitSatzenName[2], outputProduct, true);
        final String[] splitSolzenName = solzenNode.getName().split("/");
        ProductUtils.copyBand(solzenNode.getName(), modisGeoProduct, splitSolzenName[2], outputProduct, true);

    }

    private void addSpectralTargetBands(Product outputProduct, String[] bandNames, String descriptionPattern, String unit) {
        for (int i = 0; i < Constants.MODIS_REFLEC_BAND_NAMES.length; i++) {
//            final float wvl = Float.parseFloat(bandNames[i].substring(bandNames[i].length() - 3, bandNames[i].length()));
            final double wvl = Constants.MODIS_SPECTRAL_WAVELENGHTS_TO_USE[i];
            final String descr = MessageFormat.format(descriptionPattern, wvl);
            final Band band = outputProduct.addBand(bandNames[i], ProductData.TYPE_FLOAT32);
            band.setSpectralWavelength((float) wvl);
            band.setDescription(descr);
            band.setUnit(unit);
            band.setValidPixelExpression("");   // todo
        }
    }

    private void validateModisL1bProduct(Product modisL1bProduct) {
        // todo
    }

    public static FlagCoding createAcFlagCoding() {
        final FlagCoding flagCoding = new FlagCoding(Constants.AC_FLAG_BAND_NAME);
        flagCoding.setDescription("Atmospheric Correction - Flag Coding");

        addFlagAttribute(flagCoding, "INVALID", "Invalid input pixels (LAND || CLOUD_ICE || TOA_OOR)",
                         AtmosCorrection.INVALID);
        addFlagAttribute(flagCoding, "LAND", "Land pixels", AtmosCorrection.LAND);
        addFlagAttribute(flagCoding, "CLOUD_ICE", "Cloud or ice pixels", AtmosCorrection.CLOUD_ICE);
        addFlagAttribute(flagCoding, "TOA_OOR", "TOA out of range", AtmosCorrection.TOA_OOR);

        return flagCoding;
    }

    private static void addFlagAttribute(FlagCoding flagCoding, String name, String description, int value) {
        MetadataAttribute attribute = new MetadataAttribute(name, ProductData.TYPE_UINT16);
        attribute.getData().setElemInt(value);
        attribute.setDescription(description);
        flagCoding.addAttribute(attribute);
    }

    public static void addAcMasks(Product product) {
        final ProductNodeGroup<Mask> maskGroup = product.getMaskGroup();
        maskGroup.add(createMask(product, "ac_invalid", "'AC invalid' pixels (LAND || CLOUD_ICE || TOA_OOR)",
                                 "ac_flags.INVALID", Color.RED, 0.5f));
        maskGroup.add(createMask(product, "ac_land", "Land pixels", "ac_flags.LAND", Color.GREEN, 0.5f));
        maskGroup.add(createMask(product, "cloud_ice", "Cloud or ice pixels", "ac_flags.CLOUD_ICE",
                                 Color.cyan, 0.5f));
        maskGroup.add(createMask(product, "toa_oor", "TOA out of range", "ac_flags.TOA_OOR", Color.MAGENTA, 0.5f));
    }

    private static Mask createMask(Product product, String name, String description, String expression, Color color,
                                   float transparency) {
        return Mask.BandMathsType.create(name, description,
                                         product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                         expression, color, transparency);
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
            super(ModisAtmosCorrectionOp.class);
        }
    }
}
