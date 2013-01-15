package org.esa.beam.atmosphere.operator;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;

import java.awt.*;

import static java.lang.Math.abs;
import static org.esa.beam.util.math.MathUtils.ceilInt;
import static org.esa.beam.util.math.MathUtils.floorInt;

/**
 * todo: add comment
 *
 * @author olafd
 */
public class ModisFlightDirection {

    public static int findNadirColumnIndex(Product merisProduct) {
        final int rasterWidth = merisProduct.getSceneRasterWidth();
        final RasterDataNode grid = merisProduct.getRasterDataNode(Constants.MODIS_VIEW_ZENITH_BAND_NAME);
        final double[] data = new double[rasterWidth];
        final Rectangle centerColumn = new Rectangle(0, 0, data.length, 1);
        grid.getGeophysicalImage().getData(centerColumn).getPixels(0, 0, data.length, 1, data);
        return findNadirColumnIndex(data);
    }

    static int findNadirColumnIndex(double[] viewZenithRow) {
        double minValue = viewZenithRow[0];
        int nadirIndex = 0;
        for (int i = 1; i < viewZenithRow.length; i++) {
            if (viewZenithRow[i] < minValue) {
                minValue = viewZenithRow[i];
                nadirIndex = i;
            } else {
                break;
            }
        }

        if (nadirIndex == 0) { // we are on the left side
            final double stepSize = abs(viewZenithRow[0] - viewZenithRow[1]);
            nadirIndex -= ceilInt(minValue / stepSize);
        } else if (nadirIndex == viewZenithRow.length - 1) { // we are on the right side
            final double stepSize = abs(
                    viewZenithRow[viewZenithRow.length - 1] - viewZenithRow[viewZenithRow.length - 2]);
            nadirIndex += floorInt(minValue / stepSize);
        }
        return nadirIndex;
    }

}
