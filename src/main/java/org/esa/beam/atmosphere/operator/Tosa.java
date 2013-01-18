package org.esa.beam.atmosphere.operator;

import org.esa.beam.PixelData;

import static java.lang.Math.*;
import static java.lang.Math.exp;

/**
 * todo: add comment
 *
 * @author olafd
 */
public class Tosa {

    private static final int NUM_BANDS = Constants.MODIS_SPECTRAL_WAVELENGHTS_TO_USE.length;

    private double[] trans_oz_down_rest;
    private double[] trans_oz_up_rest;
    private double[] tau_rayl_rest;
    private double[] trans_oz_down_real;
    private double[] trans_oz_up_real;
    private double[] trans_rayl_down_rest;
    private double[] trans_rayl_up_rest;
    private double[] lrcPath;
    private double[] ed_toa;
    private double[] edTosa;
    private double[] lTosa;

    public void init() {
        trans_oz_down_rest = new double[NUM_BANDS];
        trans_oz_up_rest = new double[NUM_BANDS];
        tau_rayl_rest = new double[NUM_BANDS];
        trans_oz_down_real = new double[NUM_BANDS];
        trans_oz_up_real = new double[NUM_BANDS];
        trans_rayl_down_rest = new double[NUM_BANDS];
        trans_rayl_up_rest = new double[NUM_BANDS];
        lrcPath = new double[NUM_BANDS];
        ed_toa = new double[NUM_BANDS];
        edTosa = new double[NUM_BANDS];
        lTosa = new double[NUM_BANDS];
    }

    public double[] perform(PixelData pixel, double teta_view_surf_rad, double teta_sun_surf_rad) {
        /* angles */
        double cos_teta_sun_surf = cos(teta_sun_surf_rad);
        double sin_teta_sun_surf = sin(teta_sun_surf_rad);
        double cos_teta_view_surf = cos(teta_view_surf_rad);
        double sin_teta_view_surf = sin(teta_view_surf_rad);

        double azi_view_surf_rad = toRadians(pixel.satazi);
        double azi_sun_surf_rad = toRadians(pixel.solazi);
        double azi_diff_surf_rad = acos(cos(azi_view_surf_rad - azi_sun_surf_rad));
        double cos_azi_diff_surf = cos(azi_diff_surf_rad);

        double[] rlTosa = new double[NUM_BANDS];
        double[] sun_toa = pixel.solar_flux;

        double[] lToa = pixel.toa_radiance;

        for (int i = 0; i < lToa.length; i++) {
            // convert back from reflectance to radiance...
            // from http://oceancolor.gsfc.nasa.gov/forum/oceancolor/topic_show.pl?tid=1680:
            // r = pi * L / (Fo mu0),
            // where r is reflectance, L is radiance, Fo is solar irradiance and mu0 is cosine of the solar zenith angle
            lToa[i] = lToa[i] * Constants.SOLAR_FLUXES_TO_USE[i] * cos_teta_view_surf / Math.PI;
        }

        /* calculate relative airmass rayleigh correction for correction layer*/
        if (pixel.altitude < 1.0f) {
            pixel.altitude = 1.0f;
        }

        double altitude_pressure = pixel.pressure * Math.pow((1.0 - 0.0065 * pixel.altitude / 288.15), 5.255);

        double rayl_rest_mass = (altitude_pressure - 1013.2) / 1013.2;


        /* calculate optical thickness of rayleigh for correction layer, lam in micrometer */
        for (int i = 0; i < tau_rayl_rest.length; i++) {
            final double currentWavelength = Constants.MODIS_SPECTRAL_WAVELENGHTS_TO_USE[i] / 1000; // wavelength in micrometer
            tau_rayl_rest[i] = rayl_rest_mass *
                    (0.008524 * pow(currentWavelength, -4.0) +
                            9.63E-5 * pow(currentWavelength, -6.0) +
                            1.1E-6 * pow(currentWavelength, -8.0));
        }

        /* calculate phase function for rayleigh path radiance*/
        double cos_scat_ang = -cos_teta_view_surf * cos_teta_sun_surf - sin_teta_view_surf * sin_teta_sun_surf * cos_azi_diff_surf;
        double delta = 0.0279;
        double gam = delta / (2.0 - delta);
        double phase_rayl = 3.0 / (4.0 * (1.0 + 2.0 * gam)) * ((1.0 - gam) * cos_scat_ang * cos_scat_ang + (1.0 + 3.0 * gam));

        /* ozon and rayleigh correction layer transmission */
        double ozon_rest_mass = (pixel.ozone / 1000.0); /* conc ozone from MERIS is in DU */
        for (int i = 0; i < trans_oz_down_rest.length; i++) {
            final double ozonAbsorption = -Constants.OZONE_ABSORPTIONS_TO_USE[i];
            final double scaledTauRaylRest = -tau_rayl_rest[i] * 0.5; /* 0.5 because diffuse trans */

            trans_oz_down_real[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_sun_surf);
            trans_oz_up_real[i] = exp(ozonAbsorption * pixel.ozone / 1000.0 / cos_teta_view_surf);

            trans_oz_down_rest[i] = exp(ozonAbsorption * ozon_rest_mass / cos_teta_sun_surf);
            trans_oz_up_rest[i] = exp(ozonAbsorption * ozon_rest_mass / cos_teta_view_surf);

            trans_rayl_down_rest[i] = exp(scaledTauRaylRest / cos_teta_sun_surf);
            trans_rayl_up_rest[i] = exp(scaledTauRaylRest / cos_teta_view_surf);
        }

        /* Rayleigh path radiance of correction layer */

        for (int i = 0; i < lrcPath.length; i++) {
            lrcPath[i] = sun_toa[i] * trans_oz_down_real[i] * tau_rayl_rest[i]
                    * phase_rayl / (4 * Math.PI * cos_teta_view_surf);
        }

        /* compute Ed_toa from sun_toa using  cos_teta_sun */
        for (int i = 0; i < ed_toa.length; i++) {
            ed_toa[i] = sun_toa[i] * cos_teta_sun_surf;
        }

        /* Calculate Ed_tosa */
        for (int i = 0; i < edTosa.length; i++) {
            edTosa[i] = ed_toa[i] * trans_oz_down_rest[i] * trans_rayl_down_rest[i];
        }

        /* compute path radiance difference for tosa without - with smile */
        for (int i = 0; i < lTosa.length; i++) {
            /* Calculate L_tosa */
            lTosa[i] = (lToa[i] + lrcPath[i] * trans_oz_up_real[i]) / trans_oz_up_rest[i] * trans_rayl_up_rest[i];
            /* Calculate Lsat_tosa radiance reflectance as input to NN */
            rlTosa[i] = lTosa[i] / edTosa[i];
        }

        if (pixel.pixelX == 856 && pixel.pixelY == 233) {
            System.out.println("x = " + pixel.pixelX);
        }

        return rlTosa;
    }

}
