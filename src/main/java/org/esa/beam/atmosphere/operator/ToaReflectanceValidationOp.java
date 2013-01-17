package org.esa.beam.atmosphere.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.nn.util.DefaultWatermaskStrategy;
import org.esa.beam.nn.util.WatermaskStrategy;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;

/**
 * Operator for validation of TOA reflectances.
 *
 * @author Marco Peters, Olaf Danne
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "FieldCanBeLocal"})
@OperatorMetadata(alias = "Modis.ToaReflValid",
                  version = "0.1",
                  internal = true,
                  authors = "Marco Peters, Olaf Danne",
                  copyright = "(c) 2007, 2013 by Brockmann Consult",
                  description = "Validation of MODIS TOA reflectances.")
public class ToaReflectanceValidationOp extends Operator {

    public static final int LAND_FLAG_MASK = 0x01;
    public static final int CLOUD_ICE_FLAG_MASK = 0x02;
    public static final int RLTOA_OOR_FLAG_MASK = 0x04;

    @SourceProduct(alias = "input")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(label = "Use SRTM Land/Water mask", defaultValue = "true",
               description = "If set to 'false' a land detection expression as defined below is used.")
    private boolean useSrtmWaterMask;

    @Parameter(defaultValue = "EV_1KM_RefSB_16 > 0.1",
               label = "Land detection expression", notEmpty = true, notNull = true)
    private String landExpression;
    @Parameter(defaultValue = "EV_1KM_RefSB_16 > 0.027", label = "Cloud/Ice detection expression", notEmpty = true,
               notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "EV_1KM_RefSB_16 > 0.1", label = "'TOA out of range' (TOA_OOR flag) detection expression")
    private String rlToaOorExpression;

    private Band landWaterBand;
    private Band cloudIceBand;
    private Band rlToaOorBand;
    private Product reflProduct;

    private WatermaskClassifier classifier;
    private WatermaskStrategy strategy = null;
    private static final byte WATERMASK_FRACTION_THRESH = 23;   // for 3x3 subsampling, this means 2 subpixels water


    public static ToaReflectanceValidationOp create(Product sourceProduct,
                                                    boolean useSrtmWaterMask,
                                                    String landExpression,
                                                    String cloudIceExpression,
                                                    String rlToaOorExpression) {
        final ToaReflectanceValidationOp validationOp = new ToaReflectanceValidationOp();
        validationOp.sourceProduct = sourceProduct;
        validationOp.useSrtmWaterMask = useSrtmWaterMask;
        validationOp.landExpression = landExpression;
        validationOp.cloudIceExpression = cloudIceExpression;
        validationOp.rlToaOorExpression= rlToaOorExpression;
        return validationOp;
    }

    @Override
    public void initialize() throws OperatorException {
        targetProduct = new Product(String.format("%s_cls", sourceProduct.getName()),
                                    String.format("%s_CLS", sourceProduct.getProductType()),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        reflProduct = new Product(String.format("%s_cls", sourceProduct.getName()),
                                  String.format("%s_CLS", sourceProduct.getProductType()),
                                  sourceProduct.getSceneRasterWidth(),
                                  sourceProduct.getSceneRasterHeight());
//        reflProduct = sourceProduct;

        //
        for (Band b : sourceProduct.getBands()) {
            if (b.getName().startsWith(Constants.MODIS_TOA_BAND_NAME_PREFIX)) {
                ProductUtils.copyBand(b.getName(), sourceProduct, reflProduct, true);
            }
        }

        setWatermaskStrategy();

        BandMathsOp landWaterOp = BandMathsOp.createBooleanExpressionBand(landExpression, reflProduct);
        landWaterBand = landWaterOp.getTargetProduct().getBandAt(0);

        BandMathsOp cloudIceOp = BandMathsOp.createBooleanExpressionBand(cloudIceExpression, reflProduct);
        cloudIceBand = cloudIceOp.getTargetProduct().getBandAt(0);

        BandMathsOp rlToaOorOp = BandMathsOp.createBooleanExpressionBand(rlToaOorExpression, reflProduct);
        rlToaOorBand = rlToaOorOp.getTargetProduct().getBandAt(0);

        final FlagCoding flagCoding = new FlagCoding("rlToa_flags");
        flagCoding.addFlag("land", LAND_FLAG_MASK, "Pixel is land");
        flagCoding.addFlag("cloud_ice", CLOUD_ICE_FLAG_MASK, "Pixel is cloud or ice");
        flagCoding.addFlag("rlToa_OOR", RLTOA_OOR_FLAG_MASK, "RlToa is Out Of Range");
        targetProduct.getFlagCodingGroup().add(flagCoding);

        Band classBand = targetProduct.addBand("rlToa_flags", ProductData.TYPE_INT8);
        classBand.setNoDataValue(-1);
        classBand.setNoDataValueUsed(true);
        classBand.setSampleCoding(flagCoding);

    }

    @Override
    public void dispose() {
        if (reflProduct != null) {
            reflProduct.dispose();
            reflProduct = null;
        }
        super.dispose();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        try {
            pm.beginTask("Computing TOA_Reflectance classification", 4 * targetTile.getHeight());

            final Tile landWaterTile = getSourceTile(landWaterBand, targetTile.getRectangle());
            final Tile cloudIceTile = getSourceTile(cloudIceBand, targetTile.getRectangle());
            final Tile rlToaOorTile = getSourceTile(rlToaOorBand, targetTile.getRectangle());

            GeoPos geoPos = null;

            Rectangle rectangle = targetTile.getRectangle();
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    byte waterMaskSample = WatermaskClassifier.INVALID_VALUE;
                    byte waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
                    final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                    if (geoCoding.canGetGeoPos()) {
                        geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                        waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                        waterMaskFraction = strategy.getWatermaskFraction(geoCoding, x, y);
                    }

                    byte value = 0;

                    boolean isLand;
                    if (useSrtmWaterMask) {
                        isLand = !(waterMaskSample == WatermaskClassifier.WATER_VALUE) &&
                                waterMaskFraction < WATERMASK_FRACTION_THRESH;
                    } else {
                        isLand = landWaterTile.getSampleBoolean(x, y);
                    }

                    if (isLand) {
                        value |= LAND_FLAG_MASK;
                    }

                    final boolean isToaOOR = rlToaOorTile.getSampleBoolean(x, y);
                    if (isToaOOR) {
                        value |= RLTOA_OOR_FLAG_MASK;
                    }

                    if (!isToaOOR && !isLand && cloudIceTile.getSampleBoolean(x, y)) {
                        value |= CLOUD_ICE_FLAG_MASK;
                    }
                    targetTile.setSample(x, y, value);
                }
            }
        } finally {
            pm.done();
        }


    }

    private void setWatermaskStrategy() {
        try {
            classifier = new WatermaskClassifier(50, 3, 3);
        } catch (IOException e) {
            getLogger().warning("Watermask classifier could not be initialized - fallback mode is used.");
        }
        strategy = new DefaultWatermaskStrategy(classifier);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ToaReflectanceValidationOp.class);
        }
    }

}

