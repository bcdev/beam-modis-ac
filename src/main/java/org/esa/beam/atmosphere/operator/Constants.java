package org.esa.beam.atmosphere.operator;

/**
 * MODIS Atmospheric Correction constants
 *
 * @author olafd
 */
public class Constants {

    public static final String MODIS_ATMOSPHERIC_NET_NAME = "atmo_correct_modis/31x47x37_4689.5.net";

//    public static final String MODIS_TOA_BAND_NAME_PREFIX = "MODIS_SWATH_Type_L1B/Data Fields/EV_1KM_RefSB_Band_1KM_";
    public static final String MODIS_TOA_BAND_NAME_PREFIX = "EV_1KM_";
    public static final String MODIS_GEO_DATAFIELDS_BAND_NAME_PREFIX = "MODIS_Swath_Type_GEO/Data Fields/";
    public static final String MODIS_GEO_GEOLOCATION_BAND_NAME_PREFIX = "MODIS_Swath_Type_GEO/Geolocation Fields/";

    public static final String MODIS_L2_FLAG_BAND_NAME = "L2Flags";
    public static final String AC_FLAG_BAND_NAME = "ac_flags";

    public static final String MODIS_LATITUDE_BAND_NAME = "Latitude";
    public static final String MODIS_LONGITUDE_BAND_NAME = "Longitude";
    public static final String MODIS_SUN_ZENITH_BAND_NAME = "SolarZenith";
    public static final String MODIS_SUN_AZIMUTH_BAND_NAME = "SolarAzimuth";
    public static final String MODIS_VIEW_ZENITH_BAND_NAME = "SensorZenith";
    public static final String MODIS_VIEW_AZIMUTH_BAND_NAME = "SensorAzimuth";

    // The 1km spectral bands in the MODIS L1b LAC product:
    public static final String[] MODIS_SPECTRAL_BAND_NAMES = {
            "RefSB_8",
            "RefSB_9",
            "RefSB_10",
            "RefSB_11",
            "RefSB_12",
            "RefSB_13lo",
            "RefSB_13hi",
            "RefSB_14lo",
            "RefSB_14hi",
            "RefSB_15",
            "RefSB_16",
            "RefSB_17",
            "RefSB_18",
            "RefSB_19",
            "RefSB_26"
    };

    // The names of the spectral bands to use as input for NN approach:
    public static final String[] MODIS_SPECTRAL_BANDNAMES_TO_USE = {
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_8",     // 412nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_9",     // 443nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_10",    // 488nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_11",    // 531nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_12",    // 547nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_13lo",  // 667nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_14lo",  // 678nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_15",    // 748nm
            MODIS_TOA_BAND_NAME_PREFIX + "RefSB_16"     // 869nm
    };

    // The wavelengths of the spectral bands to use as input for NN approach:
    public static final double[] MODIS_SPECTRAL_WAVELENGHTS_TO_USE = {
            412.0,
            443.0,
            488.0,
            531.0,
            547.0,
            667.0,
            678.0,
            748.0,
            869.0
    };

    // Solar fluxes, F0 Thuillier,
    // from http://oceancolor.gsfc.nasa.gov/DOCS/RSR_tables.html, modified from in mW/cm^2/um to W/m2
    public static final double[] SOLAR_FLUXES_TO_USE = {
            1729.12,     // 412nm
            1876.22,     // 443nm
            1949.33,     // 488nm
            1857.47,     // 531nm
            1865.39,     // 547nm
            1522.55,     // 667nm
            1480.52,     // 678nm
            1280.65,     // 748nm
            958.24      // 869nm
    };

    // Ozone absorptions, from http://oceancolor.gsfc.nasa.gov/DOCS/RSR_tables.html,
    public static final double[] OZONE_ABSORPTIONS_TO_USE = {
            1.987E-03,     // 412nm
            3.189E-03,     // 443nm
            2.032E-02,     // 488nm
            6.838E-02,     // 531nm
            8.622E-02,     // 547nm
            7.382E-02,     // 667nm
            3.787E-02,     // 678nm
            1.235E-02,     // 748nm
            1.936E-03      // 869nm
    };


    // Output reflectance band names
    public static final String[] MODIS_REFLEC_BAND_NAMES = {
            "refl_412",
            "refl_443",
            "refl_489",
            "refl_531",
            "refl_551",
            "refl_665",
            "refl_678",
            "refl_748",
            "refl_869"
    };

    // Output TOSA reflectance band names
    public static final String[] MODIS_TOSA_REFLEC_BAND_NAMES = {
            "tosa_412",
            "tosa_443",
            "tosa_489",
            "tosa_531",
            "tosa_551",
            "tosa_665",
            "tosa_678",
            "tosa_748",
            "tosa_869"
    };
}
