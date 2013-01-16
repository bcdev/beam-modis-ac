package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.nn.util.NeuralNetIOConverter;

/**
 * Class providing the atmospheric correction.
 */
public class AtmosCorrection {

    private NNffbpAlphaTabFast atmosphereNet;

    static final int INVALID = 0x01;  // LAND || CLOUD_ICE
    static final int LAND = 0x02;
    static final int CLOUD_ICE = 0x04;
    static final int TOA_OOR = 0x08;

    /**
     * @param atmosphereNet the neural net for atmospheric correction
     */
    public AtmosCorrection(NNffbpAlphaTabFast atmosphereNet) {
        this.atmosphereNet = atmosphereNet;
    }

    /**
     * This method performs the AC, using new net (15 Jan 2013).
     *
     * @param pixel            the pixel input data
     * @param temperature      the water temperature
     * @param salinity         the water salinity
     * @return AtmosCorrectionResult
     */
    public AtmosCorrectionResult perform(PixelData pixel, double temperature, double salinity) {

        final AtmosCorrectionResult acResult = new AtmosCorrectionResult();

        if (isLand(pixel)) {
            acResult.raiseFlag(LAND);
        }

        if (isCloudIce(pixel)) {
            acResult.raiseFlag(CLOUD_ICE);
        }

        if (isToaOor(pixel)) {
            acResult.raiseFlag(TOA_OOR);
        }

        if ((acResult.getFlag() & LAND) == LAND ||
            (acResult.getFlag() & CLOUD_ICE) == CLOUD_ICE ||
            (acResult.getFlag() & TOA_OOR) == TOA_OOR) {
            acResult.raiseFlag(INVALID);
            return acResult;
        }

        double tetaViewSurfDeg = pixel.satzen; /* viewing zenith angle */
        tetaViewSurfDeg = correctViewAngle(tetaViewSurfDeg, pixel.pixelX, pixel.nadirColumnIndex);
        final double tetaViewSurfRad = Math.toRadians(tetaViewSurfDeg);
        final double tetaSunSurfDeg = pixel.solzen; /* sun zenith angle */
        final double tetaSunSurfRad = Math.toRadians(tetaSunSurfDeg);
        final double aziDiffSurfDeg = getAzimuthDifference(pixel);
        final double aziDiffSurfRad = Math.toRadians(aziDiffSurfDeg);

        double[] xyz = computeXYZCoordinates(tetaViewSurfRad, aziDiffSurfRad);

        Tosa tosa = new Tosa();
        tosa.init();
        final double[] rlTosa = tosa.perform(pixel, tetaViewSurfRad, tetaSunSurfRad);
        acResult.setTosaReflec(rlTosa.clone());
        final double[] rTosa = NeuralNetIOConverter.multiplyPi(rlTosa); // rTosa = rlTosa * PI

        int atmoNetInputIndex = 0;
        double[] atmoNetInput = new double[atmosphereNet.getInmin().length];
        atmoNetInput[atmoNetInputIndex++] = tetaSunSurfDeg;
        atmoNetInput[atmoNetInputIndex++] = xyz[0];
        atmoNetInput[atmoNetInputIndex++] = xyz[1];
        atmoNetInput[atmoNetInputIndex++] = xyz[2];
        atmoNetInput[atmoNetInputIndex++] = temperature;
        atmoNetInput[atmoNetInputIndex++] = salinity;

        final double[] logRTosa = NeuralNetIOConverter.convertLogarithm(rTosa);
        System.arraycopy(logRTosa, 0, atmoNetInput, atmoNetInputIndex, rlTosa.length);
        double[] atmoNetOutput = atmosphereNet.calc(atmoNetInput);
        acResult.setReflec(atmoNetOutput);

        return acResult;
    }

    static boolean isToaOor(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK) == ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK;
    }

    static boolean isCloudIce(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK) == ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK;
    }

    static boolean isLand(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.LAND_FLAG_MASK) == ToaReflectanceValidationOp.LAND_FLAG_MASK;
    }


    private static double correctViewAngle(double teta_view_deg, int pixelX, int centerPixel) {
        final double ang_coef_1 = -0.004793;
        final double ang_coef_2 = 0.0093247;
        teta_view_deg = teta_view_deg + Math.abs(pixelX - centerPixel) * ang_coef_2 + ang_coef_1;
        return teta_view_deg;
    }

    private static double getAzimuthDifference(PixelData pixel) {
        double aziViewSurfRad = Math.toRadians(pixel.satazi);
        double aziSunSurfRad = Math.toRadians(pixel.solazi);
        double aziDiffSurfRad = Math.acos(Math.cos(aziViewSurfRad - aziSunSurfRad));
        return Math.toDegrees(aziDiffSurfRad);
    }

    private static double[] computeXYZCoordinates(double tetaViewSurfRad, double aziDiffSurfRad) {
        double[] xyz = new double[3];

        xyz[0] = Math.sin(tetaViewSurfRad) * Math.cos(aziDiffSurfRad);
        xyz[1] = Math.sin(tetaViewSurfRad) * Math.sin(aziDiffSurfRad);
        xyz[2] = Math.cos(tetaViewSurfRad);
        return xyz;
    }

//    private void writeDebugOutput(PixelData pixel, double[] normInNet, double[] normOutNet, double[] reflec, double[] normReflec, double aziDiffSurfDeg) {
//        System.out.println("pixel.satazi = " + pixel.satazi);
//        System.out.println("pixel.satzen = " + pixel.satzen);
//        System.out.println("pixel.solazi = " + pixel.solazi);
//        System.out.println("pixel.solzen = " + pixel.solzen);
//        System.out.println("azimuth diff = " + aziDiffSurfDeg);
//        for (int i = 0; i < reflec.length; i++) {
//            System.out.println("reflec[" + i + "] = " + reflec[i]);
//        }
//    }

}
