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

    public AtmosCorrectionResult() {
        reflec = new double[9];
        tosaReflec = new double[9];
        path = new double[9];
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

}
