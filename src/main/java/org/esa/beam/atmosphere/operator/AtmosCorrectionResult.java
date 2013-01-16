package org.esa.beam.atmosphere.operator;

/**
 * Class representing a result from the atmospheric correction.
 *
 * @author Roland Doerffer, Olaf Danne
 */
public class AtmosCorrectionResult {

    private double[] reflec;
    private double[] tosaReflec;
    private double[] path;
    private int flag;

    public AtmosCorrectionResult() {
        reflec = new double[Constants.MODIS_SPECTRAL_WAVELENGHTS_TO_USE.length];
        tosaReflec = new double[Constants.MODIS_SPECTRAL_WAVELENGHTS_TO_USE.length];
        path = new double[Constants.MODIS_SPECTRAL_WAVELENGHTS_TO_USE.length];
        flag = 0;
    }

    public void setReflec(double[] reflec) {
        this.reflec = reflec;
    }

    public double[] getReflec() {
        return reflec;
    }

    public double[] getTosaReflec() {
        return tosaReflec;
    }

    public void setTosaReflec(double[] tosaReflec) {
        this.tosaReflec = tosaReflec;
    }

    public void setPath(double[] path) {
        this.path = path;
    }

    public double[] getPath() {
        return path;
    }

    public void raiseFlag(int flag) {
        this.flag |= flag;
    }

    public int getFlag() {
        return flag;
    }

}
